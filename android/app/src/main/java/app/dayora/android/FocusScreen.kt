package app.dayora.android

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

private fun isEligibleFocusTask(task: JSONObject, id: String) = eligibleFocusTask(
    task.optString("id"), id, task.optString("status").takeUnless { it == "null" }.orEmpty(),
    !task.isNull("completedAt") && task.optString("completedAt").isNotEmpty(), task.optBoolean("_pending"))

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun FocusScreen(r: Repository, openSessions: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tick by r.changed.collectAsState()
    var sessions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var tasks by remember { mutableStateOf(emptyList<JSONObject>()) }
    var workspaces by remember { mutableStateOf(emptyList<JSONObject>()) }
    var workspaceId by remember { mutableStateOf(r.context.getSharedPreferences("workspace", 0).getString("selectedId", "").orEmpty()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var online by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("NORMAL") }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var shortBreakMinutes by rememberSaveable { mutableIntStateOf(5) }
    var longBreakMinutes by rememberSaveable { mutableIntStateOf(15) }
    var taskId by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var choosingEndTime by rememberSaveable { mutableStateOf(false) }
    var countUpTarget by rememberSaveable { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<FocusConfirmation?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(tick) {
        try {
            workspaceId = r.context.getSharedPreferences("workspace", 0).getString("selectedId", workspaceId).orEmpty()
            sessions = r.list("focus").sortedByDescending { it.optString("startedAt") }
            tasks = r.list("tasks")
            workspaces = r.list("workspaces")
            if (workspaceId.isBlank() || workspaces.none { it.optString("id") == workspaceId }) {
                workspaceId = workspaces.firstOrNull()?.optString("id").orEmpty()
                r.context.getSharedPreferences("workspace", 0).edit().putString("selectedId", workspaceId).apply()
            }
            val pomodoro = r.list("settings").firstOrNull()?.optJSONObject("settings")?.optJSONObject("pomodoro")
            if (pomodoro != null && sessions.none { it.optString("state") in listOf("RUNNING", "PAUSED") }) {
                mode = pomodoro.optString("mode", mode)
                minutes = pomodoro.optInt("focusMinutes", minutes)
                shortBreakMinutes = pomodoro.optInt("shortBreakMinutes", shortBreakMinutes)
                longBreakMinutes = pomodoro.optInt("longBreakMinutes", longBreakMinutes)
            }
            loaded = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Could not load saved sessions. ${e.message.orEmpty()}"
        }
    }
    val active = sessions.firstOrNull { it.optString("state") in listOf("RUNNING", "PAUSED") }
    val completed = sessions.firstOrNull { it.optString("state") == "COMPLETED" }
    LaunchedEffect(active?.optString("state")) {
        while (true) {
            now = System.currentTimeMillis()
            val network = r.context.getSystemService(ConnectivityManager::class.java)
            online = network.getNetworkCapabilities(network.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            delay(1000)
        }
    }
    fun request(path: String, body: JSONObject = JSONObject()) {
        if (path == "/api/focus" && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        if (busy || !online) return
        busy = true
        error = ""
        scope.launch {
            try { r.focusAction(path, body) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                error = if (e is HttpFailure && e.status in 400..499) e.message.orEmpty().removePrefix("HTTP ${e.status}: ").take(200) else "Request not confirmed. Refresh before retrying; it may have reached the server. ${e.message.orEmpty()}"
            } finally { busy = false }
        }
    }
    fun refresh() {
        if (busy || !online) return
        busy = true
        scope.launch {
            try { r.sync(); error = "" }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "Refresh failed. Saved sessions retained. ${e.message.orEmpty()}"
            } finally { busy = false }
        }
    }
    val workspaceTasks = tasks.filter { it.optString("workspaceId") == workspaceId }
    val selectedTask = workspaceTasks.firstOrNull { isEligibleFocusTask(it, taskId) }
    LaunchedEffect(workspaceId, taskId, workspaceTasks) { if (taskId.isNotEmpty() && selectedTask == null) taskId = "" }
    fun countUpSeconds(): Long {
        val now = java.time.LocalDateTime.now()
        val target = runCatching { LocalTime.parse(countUpTarget, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull() ?: return 0
        var finish = now.toLocalDate().atTime(target)
        if (!finish.isAfter(now)) finish = finish.plusDays(1)
        return (ChronoUnit.MILLIS.between(now, finish) + 999) / 1000
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        val accent = MaterialTheme.colorScheme.primary
        // ponytail: blur decorative layers only; native Compose has no cross-version backdrop blur.
        Box(Modifier.align(Alignment.TopEnd).size(260.dp).blur(64.dp).background(accent.copy(.18f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).size(220.dp).blur(56.dp).background(MaterialTheme.colorScheme.secondary.copy(.16f), CircleShape))
        val compact = maxHeight < 620.dp
        Column(
            Modifier.widthIn(max = 540.dp).fillMaxWidth().fillMaxHeight().imePadding().padding(if (compact) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp, Alignment.CenterVertically)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = openSessions, shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .55f), border = BorderStroke(1.dp, Color.White.copy(alpha = .45f)), shadowElevation = 8.dp) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.History, "Open session history", tint = accent) }
                }
            }
            if (error.isNotEmpty()) Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
            val running = active?.optString("state") == "RUNNING"
            val timer = focusTimer(active?.optLong("durationSeconds") ?: minutes * 60L, active?.optLong("activeDurationSeconds") ?: 0L, if (running) runCatching { Instant.parse(active!!.getString("runningSince")).toEpochMilli() }.getOrNull() else null, now)
            val display = timer.remaining
            val progress by animateFloatAsState(timer.progress.coerceIn(0f, 1f), label = "focus progress")
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val diameter = minOf(maxWidth, maxHeight, 280.dp)
                Surface(Modifier.size(diameter), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .58f), border = BorderStroke(1.dp, Color.White.copy(alpha = .5f)), shadowElevation = 10.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = .12f), Color.Transparent))))
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize().padding(10.dp).semantics { contentDescription = "${(progress * 100).toInt()} percent complete" }, color = accent, trackColor = accent.copy(alpha = .12f), strokeWidth = 9.dp, strokeCap = StrokeCap.Round)
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(20.dp), color = accent.copy(.12f)) {
                                Text(active?.optString("state") ?: "READY", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("${display / 60}:${(display % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            val taskTitle = active?.optString("taskId")?.let { id -> tasks.firstOrNull { it.optString("id") == id }?.optString("title") } ?: selectedTask?.optString("title")
                            if (active == null) TextButton({ picking = true }, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(taskTitle ?: "Choose task", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            } else if (taskTitle != null) Text(taskTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(.42f), border = BorderStroke(1.dp, Color.White.copy(.45f))) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf("NORMAL" to "Countdown", "POMODORO" to "Pomodoro").forEachIndexed { index, (value, label) ->
                        SegmentedButton(selected = (active?.optString("mode") ?: mode) == value, onClick = { mode = value }, enabled = active == null && !busy,
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = accent.copy(.18f), inactiveContainerColor = Color.Transparent),
                            icon = { Icon(if (value == "NORMAL") Icons.Filled.Timer else Icons.Filled.AvTimer, null, Modifier.size(18.dp)) }) { Text(label) }
                    }
                }
            }
            val enabled = online && loaded && !busy && error.isEmpty()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                FilledIconButton(onClick = { if (active != null) { if (running) confirmAction = FocusConfirmation(active.getString("id"), "RUNNING", "pause") else request("/api/focus/${active.getString("id")}/resume") } else if (mode == "NORMAL") { countUpTarget = LocalTime.now().plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm")); choosingEndTime = true } else request("/api/focus", JSONObject().put("mode", mode).put("focusDurationMinutes", minutes).put("shortBreakDurationMinutes", shortBreakMinutes).put("longBreakDurationMinutes", longBreakMinutes).put("taskId", selectedTask!!.getString("id")).put("workspaceId", selectedTask!!.getString("workspaceId"))) }, enabled = enabled && (active != null || selectedTask != null), modifier = Modifier.size(64.dp)) { Icon(if (active == null || !running) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (active == null) "Start focus" else if (running) "Pause focus" else "Resume focus", Modifier.size(30.dp)) }
                if (active != null) OutlinedIconButton({ confirmAction = FocusConfirmation(active.getString("id"), active.optString("state"), "complete") }, enabled = enabled, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Check, "Finish session") }
            }
        }
    }
    if (choosingEndTime) GlassDialog({ choosingEndTime = false }) {
        Text("Focus until", style = MaterialTheme.typography.headlineSmall)
        val initialTime = LocalTime.parse(countUpTarget)
        val timeState = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)
        LaunchedEffect(timeState.hour, timeState.minute) { countUpTarget = "%02d:%02d".format(timeState.hour, timeState.minute) }
        TimePicker(timeState, Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton({ choosingEndTime = false }) { Text("Cancel") }
            Button(onClick = { choosingEndTime = false; request("/api/focus", JSONObject().put("mode", "NORMAL").put("durationSeconds", countUpSeconds()).put("taskId", selectedTask!!.getString("id")).put("workspaceId", selectedTask!!.getString("workspaceId"))) }, enabled = countUpSeconds() > 0) { Text("Start") }
        }
    }

    confirmAction?.let { captured -> GlassDialog({ confirmAction = null }) {
        Text(if (captured.action == "pause") "Pause focus?" else "Finish session?", style = MaterialTheme.typography.headlineSmall)
        Text(if (captured.action == "pause") "Your progress is safe." else "Save your progress and end this session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton({ confirmAction = null }) { Text("Keep working") }
            Button({
                val current = sessions.firstOrNull { it.optString("id") == captured.sessionId }
                confirmAction = null
                if (captured.matches(current?.optString("id"), current?.optString("state"))) request("/api/focus/${captured.sessionId}/${captured.action}") else error = "Session changed elsewhere. Refresh before retrying."
            }) { Text(if (captured.action == "pause") "Pause" else "Finish") }
        }
    } }

    if (picking) GlassDialog({ picking = false }) {
        Text("Choose task", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search tasks") }, singleLine = true)
        TextButton({ taskId = ""; picking = false }) { Text("No task") }
        workspaceTasks.filter { isEligibleFocusTask(it, it.optString("id")) && it.optString("title").contains(query, true) }.forEach { task ->
            Surface(onClick = { taskId = task.optString("id"); picking = false }, color = MaterialTheme.colorScheme.surface.copy(.45f), shape = RoundedCornerShape(16.dp)) { Text(task.optString("title"), Modifier.fillMaxWidth().padding(16.dp)) }
        }
        TextButton({ picking = false }, Modifier.align(Alignment.End)) { Text("Close") }
    }
}
