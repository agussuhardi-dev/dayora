package app.dayora.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun WearSettings(r: Repository) {
    val context = r.context
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val tick by r.changed.collectAsState()
    var connected by remember { mutableStateOf(emptyList<Node>()) }
    var paired by remember { mutableStateOf(emptyList<JSONObject>()) }
    var error by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }

    fun refresh() = scope.launch {
        runCatching {
            connected = Wearable.getNodeClient(context).connectedNodes.await()
            if (r.authenticated.value) {
                val devices = JSONArray(r.request("GET", "/api/wear/devices"))
                paired = (0 until devices.length()).map(devices::getJSONObject)
                pending = pending?.takeUnless { pendingId -> paired.any { it.optString("deviceId") == pendingId } }
            } else paired = emptyList()
            error = ""
        }.onFailure { error = it.message ?: "Could not refresh watches" }
    }

    LaunchedEffect(tick) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pairedById = paired.associateBy { it.optString("deviceId") }
    val nodesById = connected.associateBy { it.id }
    val deviceIds = (nodesById.keys + pairedById.keys).filter { it.isNotBlank() }
    var busy by remember { mutableStateOf<String?>(null) }
    var disconnect by remember { mutableStateOf<String?>(null) }
    GlassCard(Modifier.fillMaxWidth(), hero = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = {}, enabled = false) {
                Icon(Icons.Default.Watch, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text("Watch", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh watches") }
        }
        if (deviceIds.isEmpty()) Text("No watch found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        deviceIds.forEachIndexed { index, id ->
            val node = nodesById[id]
            val isPaired = id in pairedById
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(node?.displayName ?: pairedById[id]?.optString("name")?.takeIf { it.isNotBlank() } ?: "Watch", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when { pending == id -> "Approve on watch"; node == null -> "Offline"; isPaired -> "Connected"; else -> "Ready to pair" },
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = if (node != null && isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isPaired) {
                        TextButton(onClick = { disconnect = id }, enabled = busy == null) { Text("Unpair") }
                    } else {
                        FilledTonalButton(onClick = {
                            busy = id
                            scope.launch {
                                try { WearBridge(context).pair(id); pending = id; error = "" }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { error = e.message ?: "Pairing failed" }
                                finally { busy = null }
                            }
                        }, enabled = busy == null && pending != id) { Text(if (busy == id) "Pairing…" else "Pair") }
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    disconnect?.let { id ->
        AlertDialog(
            onDismissRequest = { disconnect = null },
            title = { Text("Unpair watch?") },
            text = { Text("Focus updates will stop on this watch.") },
            dismissButton = { TextButton(onClick = { disconnect = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = {
                disconnect = null
                busy = id
                scope.launch {
                    try { WearBridge(context).revoke(id); pending = pending?.takeUnless { it == id }; refresh().join() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not unpair watch" }
                    finally { busy = null }
                }
            }) { Text("Unpair") } }
        )
    }
}
