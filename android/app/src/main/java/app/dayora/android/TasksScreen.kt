package app.dayora.android

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Tasks(r: Repository) {
    val tick by r.changed.collectAsState()
    var tasks by remember { mutableStateOf(emptyList<JSONObject>()) }
    var workspaces by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedId by remember { mutableStateOf(r.context.getSharedPreferences("workspace", 0).getString("selectedId", "").orEmpty()) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("NONE") }
    var error by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        tasks = r.list("tasks")
        workspaces = r.list("workspaces")
        selectedId = r.context.getSharedPreferences("workspace", 0).getString("selectedId", selectedId).orEmpty()
        if (selectedId.isBlank() || workspaces.none { it.optString("id") == selectedId }) {
            selectedId = workspaces.firstOrNull()?.optString("id").orEmpty()
            r.context.getSharedPreferences("workspace", 0).edit().putString("selectedId", selectedId).apply()
        }
    }
    val visible = tasks.filter { it.optString("workspaceId") == selectedId }
    val closeForm = { creating = false; title = ""; notes = ""; priority = "NONE"; error = "" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }, containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Filled.Add, "New task") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("${visible.count { it.optString("status") != "DONE" }} open") }, leadingIcon = { Icon(Icons.Filled.Checklist, null) })

                }
            }
            if (visible.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth(), hero = true) {
                    Icon(Icons.Filled.Checklist, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Text("All clear", style = MaterialTheme.typography.titleLarge)
                    Text("Add a task to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(visible, key = { it.optString("id") }) { task ->
                val status = task.optString("status", "TODO")
                val done = status == "DONE"
                var choosingStatus by remember { mutableStateOf(false) }
                val pending = task.optBoolean("_pending")
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { choosingStatus = true }, enabled = !pending) {
                            Icon(if (done) Icons.Filled.CheckCircle else if (status == "IN_PROGRESS") Icons.Filled.PlayCircle else Icons.Filled.RadioButtonUnchecked, "Change task status", tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(task.optString("title"), style = MaterialTheme.typography.bodyLarge, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    AssistChip(
                                        onClick = { choosingStatus = true },
                                        enabled = !pending,
                                        label = { Text(when (status) { "DONE" -> "Done"; "IN_PROGRESS" -> "In progress"; else -> "To do" }) },
                                        trailingIcon = { Icon(Icons.Filled.ExpandMore, null, Modifier.size(16.dp)) }
                                    )
                                    DropdownMenu(expanded = choosingStatus, onDismissRequest = { choosingStatus = false }) {
                                        listOf("TODO" to "To do", "IN_PROGRESS" to "In progress", "DONE" to "Done").forEach { (value, label) ->
                                            DropdownMenuItem(text = { Text(label) }, leadingIcon = { if (value == status) Icon(Icons.Filled.Check, null) }, onClick = {
                                                choosingStatus = false
                                                if (value != status) scope.launch {
                                                    try {
                                                        val updated = JSONObject(task.toString()).put("status", value)
                                                            .put("completedAt", if (value == "DONE") java.time.Instant.now().toString() else JSONObject.NULL)
                                                        r.edit("tasks", updated)
                                                    } catch (e: Exception) { error = e.message ?: "Could not change status" }
                                                }
                                            })
                                        }
                                    }
                                }
                                val due = task.optString("dueDate")
                                if (due.isNotBlank() && due != "null") Text(due, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (task.optString("priority") == "HIGH") Icon(Icons.Filled.Flag, "High priority", tint = MaterialTheme.colorScheme.error)
                        if (task.optBoolean("_pending")) Icon(Icons.Filled.Sync, "Syncing", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (creating) ModalBottomSheet(onDismissRequest = closeForm) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("New task", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = closeForm) { Icon(Icons.Filled.Close, "Close") }
            }
            OutlinedTextField(title, { title = it; error = "" }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Task title") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Notes") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = priority == "NONE", onClick = { priority = "NONE" }, label = { Text("Normal") }, leadingIcon = { Icon(Icons.Filled.Remove, null) })
                FilterChip(selected = priority == "HIGH", onClick = { priority = "HIGH" }, label = { Text("High") }, leadingIcon = { Icon(Icons.Filled.Flag, null) })
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { scope.launch { try { r.edit("tasks", JSONObject().put("title", title.trim()).put("notes", notes.trim()).put("priority", priority).put("status", "TODO").put("workspaceId", selectedId)); closeForm() } catch (e: Exception) { error = e.message ?: "Could not save task" } } }, enabled = title.isNotBlank() && selectedId.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.AddTask, null); Spacer(Modifier.width(8.dp)); Text("Add task")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CreateWorkspaceSheet(r: Repository, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New workspace", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { scope.launch { try { r.edit("workspaces", JSONObject().put("name", name.trim())); onDismiss() } catch (e: Exception) { error = e.message ?: "Could not create workspace" } } }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create") }
        }
    }
}
