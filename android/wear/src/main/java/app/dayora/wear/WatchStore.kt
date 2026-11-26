package app.dayora.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class WatchStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("wear", Context.MODE_PRIVATE)

    companion object {
        private val stateLock = Any()
        private val sendLock = Mutex()
        private const val RETRY_MS = 4_000L
    }

    fun read(): JSONObject = synchronized(stateLock) { JSONObject(prefs.getString("state", "{}")!!) }

    private fun save(state: JSONObject) {
        state.remove("history")
        val visibleError = state.optString("commandError").ifEmpty { state.optString("syncError") }
        if (visibleError.isEmpty()) state.remove("error") else state.put("error", visibleError)
        check(prefs.edit().putString("state", state.toString()).commit())
    }

    fun update(block: (JSONObject) -> Unit): JSONObject = synchronized(stateLock) {
        read().also { block(it); save(it) }
    }

    fun elapsed(session: JSONObject): Long {
        val base = session.optLong("activeDurationSeconds").coerceAtLeast(0)
        if (session.optString("state") != "RUNNING") return base
        val since = runCatching { Instant.parse(session.getString("runningSince")).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        return (base + (System.currentTimeMillis() - since).coerceAtLeast(0) / 1_000)
            .coerceIn(0, session.optLong("durationSeconds", 1_500))
    }

    fun action(action: String) = update { state ->
        require(state.optBoolean("paired")) { "Pair using phone Settings first" }
        require(state.optString("commandError").isEmpty()) { "Retry or discard the failed change first" }
        val queue = state.optJSONArray("queue") ?: JSONArray()
        require(queue.length() < 100) { "Outbox full; reconnect phone" }
        val current = state.optJSONObject("session")
        val operationId = UUID.randomUUID().toString()
        val operation = JSONObject().put("operationId", operationId)
            .put("account", state.getString("account")).put("action", action)
        if (action == "start") {
            operation.put("mode", state.optString("selectedMode", state.optString("mode", "NORMAL")))
                .put("focusMinutes", state.optInt("focusMinutes", 25))
                .put("shortBreakMinutes", state.optInt("shortBreakMinutes", 5))
                .put("longBreakMinutes", state.optInt("longBreakMinutes", 15))
        }
        val next = when (action) {
            "start" -> {
                require(current == null || current.optString("state") !in setOf("RUNNING", "PAUSED")) { "Session already active" }
                val localId = "local:$operationId"
                operation.put("localId", localId)
                val mode = state.optString("selectedMode", state.optString("mode", "NORMAL"))
                val duration = if (mode == "POMODORO") state.optInt("focusMinutes", 25) * 60L else 86_400L
                JSONObject().put("id", localId).put("state", "RUNNING").put("mode", mode).put("phase", "FOCUS")
                    .put("durationSeconds", duration).put("activeDurationSeconds", 0)
                    .put("runningSince", Instant.now().toString())
            }
            "pause" -> transition(current, "RUNNING", "PAUSED", operation)
            "resume" -> transition(current, "PAUSED", "RUNNING", operation)
            "complete" -> transition(current, setOf("RUNNING", "PAUSED"), "COMPLETED", operation)
            "cancel" -> transition(current, setOf("RUNNING", "PAUSED"), "CANCELLED", operation)
            else -> error("Invalid action")
        }
        queue.put(operation)
        state.put("queue", queue).put("session", next).remove("commandError")
    }

    private fun transition(current: JSONObject?, expected: String, next: String, operation: JSONObject) =
        transition(current, setOf(expected), next, operation)

    private fun transition(current: JSONObject?, expected: Set<String>, next: String, operation: JSONObject): JSONObject {
        require(current != null && current.optString("state") in expected) { "Invalid session transition" }
        operation.put("sessionId", current.getString("id"))
        return JSONObject(current.toString()).put("activeDurationSeconds", elapsed(current)).put("state", next)
            .put("runningSince", if (next == "RUNNING") Instant.now().toString() else JSONObject.NULL)
    }

    suspend fun flush(context: Context) = sendLock.withLock {
        val state = read()
        var node = state.optString("node")
        if (node.isEmpty()) {
            node = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }
                .getOrDefault(emptySet()).firstOrNull()?.id.orEmpty()
            if (node.isEmpty()) { Log.w("DayoraWear", "Sync skipped: no phone node"); return@withLock }
        }
        val queue = state.optJSONArray("queue") ?: JSONArray()
        val now = System.currentTimeMillis()
        val head = queue.optJSONObject(0)
        val canSendHead = head != null && state.optString("commandError").isEmpty() &&
            (state.optString("sendingOperationId") != head.optString("operationId") || now - state.optLong("sendingAt") >= RETRY_MS)
        val path = when {
            !state.optBoolean("paired") && state.has("token") -> "/dayora/pair/claim"
            !state.optBoolean("paired") -> "/dayora/hello"
            canSendHead -> "/dayora/focus"
            else -> "/dayora/sync"
        }
        val payload = when (path) {
            "/dayora/pair/claim" -> JSONObject().put("account", state.optString("account")).put("token", state.getString("token"))
            "/dayora/focus" -> JSONObject(head.toString()).apply { remove("localId") }
            "/dayora/hello" -> JSONObject()
            else -> JSONObject().put("account", state.optString("account"))
        }
        if (path == "/dayora/focus") update { it.put("sendingOperationId", head!!.getString("operationId")).put("sendingAt", now) }
        val result = Wearable.getMessageClient(context).sendMessage(node, path, payload.toString().toByteArray()).await()
        Log.d("DayoraWear", "sendMessage($path -> $node) result=$result")
    }

    fun retryPending() = update { it.remove("commandError"); it.remove("sendingOperationId"); it.remove("sendingAt") }

    fun forget() = synchronized(stateLock) {
        prefs.edit().clear().commit()
        read()
    }

    fun discardPending() = update { state ->
        state.put("queue", JSONArray()).remove("commandError")
        state.remove("sendingOperationId"); state.remove("sendingAt")
        val confirmed = state.optJSONObject("serverSession")
        if (confirmed == null) state.remove("session") else state.put("session", JSONObject(confirmed.toString()))
    }

    fun receive(node: String, path: String, data: ByteArray) = update { state ->
        require(data.size <= 65_536)
        val value = JSONObject(String(data))
        if (path == "/dayora/pair") {
            // Fresh pairing resets any stale edits from a previous pairing so a re-pair
            // (or reinstall with preserved data) can never deadlock on "pending edits".
            state.put("node", node).put("account", value.getString("account"))
                .put("token", value.getString("token")).put("paired", false)
            state.put("queue", JSONArray())
            state.remove("session"); state.remove("serverSession")
            state.remove("commandError"); state.remove("syncError")
            state.remove("sendingOperationId"); state.remove("sendingAt")
            return@update
        }
        if (path == "/dayora/revoked" && state.optString("node").isEmpty()) return@update
        require(node == state.optString("node"))
        if (path == "/dayora/revoked") {
            state.put("paired", false).put("syncError", "Pairing revoked; pending edits retained")
                .remove("token")
            state.remove("sendingOperationId"); state.remove("sendingAt")
            return@update
        }
        require(value.optString("account") == state.optString("account"))
        when (path) {
            "/dayora/paired" -> { state.put("paired", true).remove("token"); state.remove("syncError") }
            "/dayora/error" -> {
                val operationId = value.optString("operationId")
                val headId = state.optJSONArray("queue")?.optJSONObject(0)?.optString("operationId")
                if (operationId.isNotEmpty() && operationId == headId) state.put("commandError", value.optString("message", "Command failed; edits retained"))
                else if (operationId.isEmpty()) state.put("syncError", value.optString("message", "Sync failed"))
            }
            "/dayora/state" -> {
                if (value.has("completedToday")) state.put("completedToday", value.getInt("completedToday"))
                value.optJSONObject("pomodoro")?.let { settings ->
                    for (key in listOf("mode", "focusMinutes", "shortBreakMinutes", "longBreakMinutes")) if (settings.has(key)) state.put(key, settings.get(key))
                }
                val server = value.optJSONObject("session")
                if (server == null) state.remove("serverSession") else state.put("serverSession", JSONObject(server.toString()))
                if ((state.optJSONArray("queue")?.length() ?: 0) == 0) {
                    if (server == null) state.remove("session") else state.put("session", JSONObject(server.toString()))
                }
                state.remove("syncError")
            }
            "/dayora/ack" -> acknowledge(state, value)
        }
    }

    private fun acknowledge(state: JSONObject, value: JSONObject) {
        val queue = state.optJSONArray("queue") ?: JSONArray()
        if (queue.length() == 0) return
        val head = queue.getJSONObject(0)
        if (head.optString("operationId") != value.optString("operationId")) return
        val acknowledged = value.optJSONObject("session")
        val localId = head.optString("localId")
        val serverId = acknowledged?.optString("id").orEmpty()
        queue.remove(0)
        if (localId.isNotEmpty() && serverId.isNotEmpty()) {
            for (i in 0 until queue.length()) if (queue.getJSONObject(i).optString("sessionId") == localId) queue.getJSONObject(i).put("sessionId", serverId)
            state.optJSONObject("session")?.takeIf { it.optString("id") == localId }?.put("id", serverId)
        }
        state.put("queue", queue).remove("commandError")
        state.remove("sendingOperationId"); state.remove("sendingAt")
        if (acknowledged != null) state.put("serverSession", JSONObject(acknowledged.toString()))
        if (queue.length() == 0) {
            if (acknowledged == null) state.remove("session") else state.put("session", JSONObject(acknowledged.toString()))
        }
    }
}
