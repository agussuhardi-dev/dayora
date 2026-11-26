package app.dayora.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun FocusSessionsScreen(r: Repository, back: () -> Unit) {
    var sessions by remember { mutableStateOf(emptyList<JSONObject>()) }
    val tick by r.changed.collectAsState()
    LaunchedEffect(tick) {
        while (true) {
            val start = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val workspaceId = r.context.getSharedPreferences("workspace", 0).getString("selectedId", "").orEmpty()
            sessions = r.list("focus").filter { session -> session.optString("workspaceId") == workspaceId && runCatching { !java.time.Instant.parse(session.optString("startedAt")).isBefore(start) }.getOrDefault(false) }.sortedByDescending { it.optString("startedAt") }
            val midnight = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            kotlinx.coroutines.delay((midnight.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1000))
        }
    }
    val completed = sessions.count { it.optString("state") == "COMPLETED" }
    val totalMinutes = sessions.sumOf { it.optLong("activeDurationSeconds") / 60 }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.align(Alignment.TopEnd).size(240.dp).blur(60.dp).background(MaterialTheme.colorScheme.primary.copy(.16f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).size(200.dp).blur(54.dp).background(MaterialTheme.colorScheme.secondary.copy(.14f), CircleShape))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = back, shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(.55f), shadowElevation = 6.dp) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                Spacer(Modifier.width(14.dp))
                Column { Text("Small steps, collected.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth(), hero = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Summary("$completed", "Completed", Icons.Filled.CheckCircle)
                    Summary("${totalMinutes}m", "Focused", Icons.Filled.Timer)
                    Summary("${sessions.size}", "Sessions", Icons.Filled.History)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (sessions.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Spa, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Text("Your first session starts here", style = MaterialTheme.typography.titleMedium)
                    Text("A little focus goes a long way.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(sessions) { session -> SessionItem(session) }
            }
        }
    }
}

@Composable private fun Summary(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SessionItem(session: JSONObject) {
    val completed = session.optString("state") == "COMPLETED"
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(.14f)) {
                Icon(if (session.optString("mode") == "POMODORO") Icons.Filled.AvTimer else Icons.Filled.Timer, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (session.optString("mode") == "POMODORO") "Pomodoro focus" else "Count up focus", style = MaterialTheme.typography.titleMedium)
                Text("${session.optLong("activeDurationSeconds") / 60} min focused", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = if (completed) Color(0x33459B76) else MaterialTheme.colorScheme.primary.copy(.12f)) {
                Text(if (completed) "Done" else session.optString("state").lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
