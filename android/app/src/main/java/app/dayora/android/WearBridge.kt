package app.dayora.android

import android.content.Context
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Authenticated phone relay. Never sends cookies or CSRF credentials to a watch. */
class WearBridge(private val context: Context) {
    private val repository = Repository.get(context)
    private val prefs = context.getSharedPreferences("wear-pair", Context.MODE_PRIVATE)
    private val account get() = repository.owner
    suspend fun nodes() = Wearable.getNodeClient(context).connectedNodes.await().also { nodes ->
        android.util.Log.d("DayoraWear", "Connected nodes=${nodes.joinToString { "${it.id}:${it.displayName}:nearby=${it.isNearby}" }}")
    }
    private suspend fun send(node: String, path: String, value: JSONObject) {
        // sendMessage resolves with the number of recipients; a disconnected node yields 0 without throwing.
        val sent = Wearable.getMessageClient(context).sendMessage(node, path, value.toString().toByteArray()).await()
        require(sent > 0) { "Message not delivered to node $node (0 recipients); is the watch connected?" }
    }
    suspend fun pair(node: String) {
        require(repository.authenticated.value && account.isNotEmpty())
        repository.csrf()
        val result = JSONObject(repository.request("POST", "/api/wear/pairing", JSONObject().put("deviceId", node).toString()))
        prefs.edit().putString("pending:$node", account).putString("pairToken:$node", result.getString("token")).commit()
        val payload = result.put("account", account)
        // Retry the initial message: the watch may have missed it while its listener was not yet bound.
        var last: Throwable? = null
        for (attempt in 1..3) {
            try { send(node, "/dayora/pair", payload); return }
            catch (e: Exception) { last = e; delay(1000L * attempt) }
        }
        throw last ?: IllegalStateException("Could not send pairing request to watch")
    }
    suspend fun revoke(node: String) {
        repository.csrf()
        repository.request("DELETE", "/api/wear/devices/${java.net.URLEncoder.encode(node, "UTF-8")}")
        prefs.edit().remove("paired:$node").remove("pending:$node").commit()
        runCatching { send(node, "/dayora/revoked", JSONObject()) }
            .onFailure { android.util.Log.w("DayoraWear", "Revoke notice not delivered to $node", it) }
    }
    private suspend fun sendState(node: String) {
        android.util.Log.d("DayoraWear", "Fetching state for $node")
        val timezone = java.net.URLEncoder.encode(java.time.ZoneId.systemDefault().id, "UTF-8")
        val current = JSONObject(repository.request("GET", "/api/wear/state?timezone=$timezone"))
        val settings = JSONObject(repository.request("GET", "/api/settings")).optJSONObject("settings")?.optJSONObject("pomodoro")
        android.util.Log.d("DayoraWear", "Pushing state to $node: session=${current.optJSONObject("session")?.optString("state") ?: "none"}, completedToday=${current.optInt("completedToday")}")
        send(node, "/dayora/state", current.put("pomodoro", settings ?: JSONObject()).put("account", account))
    }
    suspend fun syncConnected() {
        if (!repository.authenticated.value) return
        for (node in nodes()) {
            if (prefs.getString("paired:${node.id}", null) == account) {
                runCatching { sendState(node.id) }.onFailure {
                    android.util.Log.w("DayoraWear", "State push failed: ${node.id}", it)
                }
            }
        }
    }
    suspend fun onMessage(node: String, path: String, data: ByteArray) {
        android.util.Log.d("DayoraWear", "Received $path from $node")
        require(data.size <= 65536 && repository.authenticated.value)
        if (path == "/dayora/hello") {
            // Watch is unpaired and asking to (re)pair. Issue a fresh token each time so a
            // reset or reinstalled watch can re-claim (claim is idempotent on the backend).
            repository.csrf()
            val result = JSONObject(repository.request("POST", "/api/wear/pairing", JSONObject().put("deviceId", node).toString()))
            prefs.edit().putString("pairToken:$node", result.getString("token")).putString("pending:$node", account).commit()
            android.util.Log.d("DayoraWear", "Watch $node says hello; issuing fresh pairing request")
            send(node, "/dayora/pair", JSONObject().put("account", account).put("token", result.getString("token")))
            return
        }
        val message = JSONObject(String(data, Charsets.UTF_8))
        require(message.optString("account") == account) { "Different account; pair again" }
        repository.csrf()
        if (path == "/dayora/pair/claim") {
            require(prefs.getString("pending:$node", null) == account)
            repository.request("POST", "/api/wear/pairing/consume", JSONObject().put("deviceId", node).put("token", message.getString("token")).toString())
            prefs.edit().remove("pending:$node").remove("pairToken:$node").putString("paired:$node", account).commit()
            send(node, "/dayora/paired", JSONObject().put("account", account))
        } else {
            require(prefs.getString("paired:$node", null) == account) { "Watch not paired" }
            if (path == "/dayora/focus") {
                val payload = JSONObject(message.toString()).put("deviceId", node)
                try {
                    val updated = JSONObject(repository.request("POST", "/api/wear/commands", payload.toString()))
                    send(node, "/dayora/ack", JSONObject().put("account", account).put("operationId", payload.getString("operationId")).put("session", updated))
                } catch (e: HttpFailure) {
                    send(node, "/dayora/error", JSONObject().put("account", account).put("status", e.status).put("message", e.message).put("operationId", payload.getString("operationId")))
                    return
                }
            }
        }
        sendState(node)
        if (path == "/dayora/focus" || path == "/dayora/pair/claim") repository.schedule()
    }
}

class WearMessageService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        // Listener callback remains alive until the bounded relay request finishes.
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(25000) {
                runCatching { WearBridge(applicationContext).onMessage(event.sourceNodeId, event.path, event.data) }
                    .onFailure { android.util.Log.w("DayoraWear", "Relay failed: ${event.path} from ${event.sourceNodeId}", it) }
            } ?: android.util.Log.w("DayoraWear", "Relay timed out: ${event.path}")
        }
    }
}
