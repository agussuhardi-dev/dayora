package app.dayora.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlannerScreen(r: Repository) {
    val tick by r.changed.collectAsState()
    var events by remember { mutableStateOf(emptyList<JSONObject>()) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0).toString()) }
    var end by remember { mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    LaunchedEffect(tick) { events = r.list("calendar") }
    val workspaceId = r.context.getSharedPreferences("workspace", 0).getString("selectedId", "").orEmpty()
    val upcoming = events.filter { it.optString("workspaceId") == workspaceId }.sortedBy { it.optString("startsAt") }
    fun reset() { editing = null; title = ""; error = ""; showForm = false }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { FloatingActionButton(onClick = { reset(); showForm = true }) { Icon(Icons.Filled.Add, "New event") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { reset(); showForm = true }) { Icon(Icons.Filled.Event, "Add event") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("${upcoming.size} events") }, leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) })
                    AssistChip(onClick = {}, label = { Text(ZoneId.systemDefault().id) }, leadingIcon = { Icon(Icons.Filled.Public, null) })
                }
            }
            if (upcoming.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth(), hero = true) {
                    Icon(Icons.Filled.EventAvailable, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text("Your calendar is clear", style = MaterialTheme.typography.titleLarge)
                    Text("Add an event when something matters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(upcoming, key = { it.optString("id") }) { event ->
                val date = runCatching { Instant.parse(event.optString("startsAt")).atZone(ZoneId.systemDefault()) }.getOrNull()
                Card(Modifier.fillMaxWidth().clickable { editing = event; title = event.optString("title"); start = LocalDateTime.ofInstant(Instant.parse(event.getString("startsAt")), ZoneId.systemDefault()).toString(); end = LocalDateTime.ofInstant(Instant.parse(event.getString("endsAt")), ZoneId.systemDefault()).toString(); showForm = true }, shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.primary) } }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.optString("title"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(date?.format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm")) ?: "Scheduled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, "Edit")
                    }
                }
            }
        }
    }
    if (showForm) ModalBottomSheet(onDismissRequest = { reset() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(if (editing == null) "New event" else "Edit event", style = MaterialTheme.typography.headlineSmall); IconButton(onClick = { reset() }) { Icon(Icons.Filled.Close, "Close") } }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("Event title") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            OutlinedTextField(start, { start = it }, Modifier.fillMaxWidth(), label = { Text("Start · YYYY-MM-DDTHH:mm") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            OutlinedTextField(end, { end = it }, Modifier.fillMaxWidth(), label = { Text("End · YYYY-MM-DDTHH:mm") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { scope.launch { try { val from = LocalDateTime.parse(start).atZone(ZoneId.systemDefault()).toInstant(); val to = LocalDateTime.parse(end).atZone(ZoneId.systemDefault()).toInstant(); require(title.trim().isNotBlank()) { "Title is required" }; require(to > from) { "End must follow start" }; r.edit("calendar", JSONObject(editing?.toString() ?: "{}").put("title", title.trim()).put("startsAt", from.toString()).put("endsAt", to.toString()).put("timezone", ZoneId.systemDefault().id)); reset() } catch (e: Exception) { error = e.message ?: "Invalid event" } } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text(if (editing == null) "Add event" else "Save changes") }
            if (editing != null) OutlinedButton(onClick = { scope.launch { try { r.edit("calendar", editing!!, true); reset() } catch (e: Exception) { error = e.message.orEmpty() } } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Filled.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Delete event") }
        }
    }
}
