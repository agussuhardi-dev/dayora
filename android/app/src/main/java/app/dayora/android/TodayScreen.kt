package app.dayora.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun Today(r: Repository, navigate: (String) -> Unit) {
    val tick by r.changed.collectAsState()
    var tasks by remember { mutableStateOf(emptyList<JSONObject>()) }
    var events by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(tick) {
        tasks = r.list("tasks")
        events = r.list("calendar")
    }
    val workspaceId = r.context.getSharedPreferences("workspace", 0).getString("selectedId", "").orEmpty()
    val workspaceTasks = tasks.filter { it.optString("workspaceId") == workspaceId }
    val workspaceEvents = events.filter { it.optString("workspaceId") == workspaceId }
    val greeting = when (LocalTime.now().hour) { in 0..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    val open = workspaceTasks.filter { it.optString("status") != "DONE" }
    val next = open.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 44.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(greeting, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { navigate("Tasks") }) { Icon(Icons.Filled.Checklist, "Open tasks") }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Spa, null, tint = MaterialTheme.colorScheme.primary)
                        Text("YOUR NEXT STEP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(next?.optString("title") ?: "Nothing urgent", style = MaterialTheme.typography.headlineSmall)
                    Text(if (next == null) "A clear day leaves room to recharge." else "Start small. Give this your full attention.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = { navigate(if (next == null) "Tasks" else "Focus") }, shape = RoundedCornerShape(16.dp)) {
                        Icon(if (next == null) Icons.Filled.Add else Icons.Filled.Timer, null)
                        Spacer(Modifier.width(8.dp)); Text(if (next == null) "Add task" else "Focus")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f).clickable { navigate("Tasks") }, Icons.Filled.Checklist, open.size.toString(), "Open", MaterialTheme.colorScheme.primary)
                StatCard(Modifier.weight(1f).clickable { navigate("Planner") }, Icons.Filled.CalendarMonth, workspaceEvents.size.toString(), "Events", Color(0xFF408A72))
            }
        }
        if (open.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Up next", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { navigate("Tasks") }) { Icon(Icons.Filled.ArrowForward, "All tasks") }
                }
                open.take(3).forEach { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp)); Text(task.optString("title"), Modifier.weight(1f), maxLines = 1)
                        if (task.optString("priority") == "HIGH") Icon(Icons.Filled.Flag, "High priority", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Schedule", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { navigate("Planner") }) { Icon(Icons.Filled.CalendarToday, "Open planner") }
                }
                if (workspaceEvents.isEmpty()) Text("No events today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else workspaceEvents.take(3).forEach { event ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp)); Text(event.optString("title"), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable private fun StatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: Color) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
