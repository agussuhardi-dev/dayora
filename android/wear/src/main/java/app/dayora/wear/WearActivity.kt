package app.dayora.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Timer math self-check; intentionally dependency-free. */
internal fun progress(elapsed: Long, duration: Long): Float = if (duration <= 0) 0f else (elapsed.toFloat() / duration).coerceIn(0f, 1f)
internal fun timerDisplay(mode: String, elapsed: Long, duration: Long): Long =
    if (mode == "NORMAL") elapsed else (duration - elapsed).coerceAtLeast(0)
private fun timerSelfCheck() {
    check(progress(0, 1500) == 0f); check(progress(1500, 1500) == 1f); check(progress(2000, 1500) == 1f)
    check(timerDisplay("POMODORO", 0, 3 * 60L) == 180L)
    check(timerDisplay("NORMAL", 0, 3 * 60L) == 0L)
    check(timerDisplay("POMODORO", 20, 60) == 40L)
}

class WearActivity:ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b);timerSelfCheck();setContent{Watch()}} }
@Composable private fun Watch(){
    val c=androidx.compose.ui.platform.LocalContext.current; val store=remember{WatchStore(c)}; val scope=rememberCoroutineScope(); var state by remember{mutableStateOf(store.read())}; var now by remember{mutableLongStateOf(System.currentTimeMillis())}; var error by remember{mutableStateOf("")}; var connected by remember{mutableStateOf(false)}; var confirmCancel by remember{mutableStateOf(false)}; var confirmDiscard by remember{mutableStateOf(false)}; var confirmAction by remember{mutableStateOf("")}; var confirmForget by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ }; LaunchedEffect(Unit){if(android.os.Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
    LaunchedEffect(Unit){while(true){state=store.read();now=System.currentTimeMillis();delay(1000)}};LaunchedEffect(Unit){while(true){runCatching{val nodes=Wearable.getNodeClient(c).connectedNodes.await();connected=nodes.isNotEmpty();if(nodes.isEmpty())Log.d("DayoraWear","No phone node; waiting for Wear Data Layer") else Log.d("DayoraWear","Phone nodes=${nodes.joinToString { "${it.id}:${it.displayName}:nearby=${it.isNearby}" }}");if(connected)store.flush(c)}.onFailure{Log.e("DayoraWear","Phone poll/send failed",it);connected=false;error=it.message.orEmpty()};delay(4000)}}
    val s=state.optJSONObject("session");val active=s?.takeIf{it.optString("state") in listOf("RUNNING","PAUSED")};var selectedMode by remember{mutableStateOf(state.optString("selectedMode",state.optString("mode","NORMAL")))};val mode=active?.optString("mode",selectedMode)?:selectedMode;val duration=active?.optLong("durationSeconds")?:if(mode=="POMODORO")state.optInt("focusMinutes",25)*60L else 0L;val elapsed=active?.let(store::elapsed)?:0;val countUp=mode=="NORMAL";val shown=timerDisplay(mode,elapsed,duration);fun act(a:String){runCatching{store.action(a);vibrate(c,if(a=="complete")200 else 50);state=store.read();scope.launch{runCatching{store.flush(c)}}}.onFailure{error=it.message.orEmpty()}}
    val running = active?.optString("state") == "RUNNING"
    DisposableEffect(running) {
        val window = (c as? android.app.Activity)?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    var previousSession by remember { mutableStateOf(s?.optString("id")) }
    var previousStatus by remember { mutableStateOf(s?.optString("state")) }
    LaunchedEffect(s?.optString("id"), s?.optString("state")) {
        val id = s?.optString("id")
        val status = s?.optString("state")
        if (status != previousStatus || (id != previousSession && status == "RUNNING" && previousStatus != "RUNNING")) {
            vibrate(c, if (status == "COMPLETED" || (status == null && previousStatus == "RUNNING")) 200 else 50)
        }
        previousSession = id; previousStatus = status
    }
    var modePage by remember { mutableStateOf(false) }
    LaunchedEffect(active?.optString("id")) { if(active!=null) modePage=false }
    BackHandler(enabled=modePage) { modePage=false }
    var cancelPage by remember { mutableStateOf(false) }
    BackHandler(enabled=cancelPage) { cancelPage=false; confirmCancel=false }
    LaunchedEffect(active?.optString("id")) { if(active==null) { cancelPage=false; confirmCancel=false } }
    MaterialTheme(colors=Colors(primary=Color(0xFF9D8BFF),onPrimary=Color.Black,secondary=Color(0xFF9D8BFF),onSecondary=Color.Black,background=Color(0xFF101018),onBackground=Color.White,surface=Color(0xFF211E2D),onSurface=Color.White,error=Color(0xFFFF9B9B),onError=Color.Black)) {
        if (!state.optBoolean("paired")) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(32.dp), contentAlignment=Alignment.Center) {
                Text(if(state.has("token"))"Pairing with Dayora…\nWaiting for phone approval" else "Connect Dayora\nPair this watch in Dayora on your phone", textAlign=TextAlign.Center)
            }
        } else if (state.optString("commandError").isNotEmpty()) {
            ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, contentPadding=PaddingValues(horizontal=20.dp, vertical=28.dp)) {
                item { Text("Sync failed", textAlign=TextAlign.Center) }
                item { Text(state.optString("commandError"), color=MaterialTheme.colors.error, textAlign=TextAlign.Center) }
                item { Text("${state.optJSONArray("queue")?.length() ?: 0} pending changes", textAlign=TextAlign.Center) }
                if (confirmDiscard) {
                    item { Text("Discard all pending watch changes? Server data stays unchanged.", textAlign=TextAlign.Center) }
                    item { Chip(onClick={store.discardPending();state=store.read();error="";confirmDiscard=false;scope.launch{runCatching{store.flush(c)}}},label={Text("Discard changes")}) }
                    item { Chip(onClick={confirmDiscard=false},label={Text("Keep changes")}) }
                } else {
                    item { Chip(onClick={store.retryPending();state=store.read();error="";scope.launch{runCatching{store.flush(c)}}},label={Text("Retry sync")}) }
                    item { Chip(onClick={confirmDiscard=true},label={Text("Discard…")}) }
                }
            }
        } else if (modePage) {
            var modeDrag by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart={modeDrag=0f}, onHorizontalDrag={_,amount->modeDrag+=amount}, onDragEnd={if(modeDrag > size.width * 0.2f) modePage=false})
            }.padding(horizontal=18.dp, vertical=16.dp), contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Timer mode", style=MaterialTheme.typography.title3, textAlign=TextAlign.Center)
                    Text("Choose how your focus runs", style=MaterialTheme.typography.caption2, textAlign=TextAlign.Center, color=Color.LightGray)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Chip(onClick={selectedMode="NORMAL";store.update{it.put("selectedMode","NORMAL")};state=store.read();modePage=false}, modifier=Modifier.weight(1f).height(64.dp).semantics { contentDescription="Count up" }, label={Text("↑\nCount up", textAlign=TextAlign.Center)})
                        Chip(onClick={selectedMode="POMODORO";store.update{it.put("selectedMode","POMODORO")};state=store.read();modePage=false}, modifier=Modifier.weight(1f).height(64.dp).semantics { contentDescription="Pomodoro" }, label={Text("◷\nPomodoro", textAlign=TextAlign.Center)})
                    }
                    Text("●  ○", style=MaterialTheme.typography.caption2, modifier=Modifier.semantics { contentDescription="Page 2 of 2" })
                    Text("Swipe right to return", style=MaterialTheme.typography.caption3, color=Color.LightGray)
                }
            }
        } else if (confirmAction.isNotEmpty()) {
            ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, contentPadding=PaddingValues(horizontal=20.dp, vertical=28.dp)) {
                item { Text("${confirmAction.replaceFirstChar { it.uppercase() }} focus?", textAlign=TextAlign.Center) }
                item { Chip(onClick={val action=confirmAction;confirmAction="";act(action)},label={Text("Confirm")}) }
                item { Chip(onClick={confirmAction=""},label={Text("Back")}) }
            }
        } else if (confirmForget) {
            ScalingLazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colors.background), horizontalAlignment=Alignment.CenterHorizontally, contentPadding=PaddingValues(horizontal=20.dp, vertical=28.dp)) {
                item { Text("Forget this phone?", textAlign=TextAlign.Center) }
                item { Text("This watch will stop syncing with Dayora until you pair again.", color=Color.LightGray, style=MaterialTheme.typography.caption2, textAlign=TextAlign.Center) }
                item { Chip(onClick={store.forget();state=store.read();error="";confirmForget=false;vibrate(c,100)},label={Text("Forget phone")}) }
                item { Chip(onClick={confirmForget=false},label={Text("Keep phone")}) }
            }
        } else if (cancelPage) {
            ScalingLazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colors.background), horizontalAlignment=Alignment.CenterHorizontally, contentPadding=PaddingValues(horizontal=20.dp, vertical=28.dp)) {
                item { Text(if(confirmCancel) "Cancel this session?" else "Focus options", textAlign=TextAlign.Center) }
                if(confirmCancel) {
                    item { Chip(onClick={act("cancel");confirmCancel=false;cancelPage=false},label={Text("Yes, cancel")}) }
                    item { Chip(onClick={confirmCancel=false;cancelPage=false},label={Text("Keep session")}) }
                } else {
                    item { Chip(onClick={confirmCancel=true},label={Text("Cancel session")}) }
                    item { Chip(onClick={cancelPage=false},label={Text("Back to timer")}) }
                }
            }
        } else {
            var drag by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background).pointerInput(active != null) {
                detectHorizontalDragGestures(onDragStart={drag=0f}, onHorizontalDrag={ _, amount -> drag+=amount }, onDragEnd={
                    if(drag < -size.width * 0.2f) { if(active!=null) cancelPage=true else modePage=true }
                })
            }, contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                    val stroke=7.dp.toPx(); val side=minOf(size.width,size.height)-stroke
                    val topLeft=Offset((size.width-side)/2,(size.height-side)/2)
                    drawArc(Color(0xFF373248),-90f,360f,false,topLeft,Size(side,side),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(Color(0xFF9D8BFF),-90f,360f*progress(elapsed,duration),false,topLeft,Size(side,side),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke,cap=StrokeCap.Round))
                }
                Column(Modifier.fillMaxWidth(0.9f), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                    if (active == null) Text(
                        if (mode == "POMODORO") "Pomodoro" else "Count up",
                        style=MaterialTheme.typography.caption1,
                        modifier=Modifier.clickable { modePage=true }.semantics { contentDescription="Select timer mode" }
                    )
                    Text("${shown/60}:${(shown%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.display2, textAlign=TextAlign.Center, maxLines=1, modifier=Modifier.semantics { contentDescription="${shown/60} minutes ${shown%60} seconds ${if(countUp) "elapsed" else "remaining"}" })
                    Text(if(active==null) "Ready  ·  Swipe left for mode" else if(active.optString("state")=="PAUSED") "Paused" else active.optString("phase", "FOCUS").replace('_', ' '),style=MaterialTheme.typography.caption1,maxLines=1)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
                        val action=if(active==null) "start" else if(active.optString("state")=="RUNNING") "pause" else "resume"
                        val label=when(action){"start"->"Start focus";"pause"->"Pause";else->"Resume"}
                        Button(onClick={confirmAction=action},modifier=Modifier.size(52.dp).semantics { contentDescription=label },shape=CircleShape) { Text(if(action=="pause") "Ⅱ" else "▶") }
                        if(active!=null) Button(onClick={confirmAction="complete"},modifier=Modifier.size(52.dp).semantics { contentDescription="Finish focus" },shape=CircleShape) { Text("✓") }
                    }
                    Spacer(Modifier.height(5.dp))
                    val count=state.optInt("completedToday")
                    Text(if(!connected) "Saved on watch" else "$count ${if(count==1) "session" else "sessions"} today",style=MaterialTheme.typography.caption2,textAlign=TextAlign.Center,maxLines=1)
                    if(active==null) Text("●  ○",style=MaterialTheme.typography.caption2,modifier=Modifier.semantics { contentDescription="Page 1 of 2" })
                    else Text("Swipe left for options",style=MaterialTheme.typography.caption3,maxLines=1)
                    Spacer(Modifier.height(5.dp))
                    Text("Forget phone",style=MaterialTheme.typography.caption3,color=Color.LightGray,modifier=Modifier.clickable{confirmForget=true}.semantics{contentDescription="Forget this phone"})
                    if(error.isNotEmpty()||state.optString("error").isNotEmpty()) {
                        Text(error.ifEmpty{state.optString("error")},color=MaterialTheme.colors.error,style=MaterialTheme.typography.caption3,maxLines=2,textAlign=TextAlign.Center)
                        if (state.optString("commandError").isNotEmpty()) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Chip(onClick={store.retryPending(); state=store.read(); scope.launch{runCatching{store.flush(c)}}},label={Text("Retry")})
                            Chip(onClick={store.discardPending(); state=store.read()},label={Text("Discard")})
                        }
                    }
                }
            }
        }
    }
}
private fun vibrate(c:Context,ms:Long){(c.getSystemService(Context.VIBRATOR_SERVICE)as Vibrator).vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE))}
private fun notifyComplete(c:Context){val n=c.getSystemService(NotificationManager::class.java);n.createNotificationChannel(NotificationChannel("focus","Focus",NotificationManager.IMPORTANCE_DEFAULT));if(android.os.Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)n.notify(7,androidx.core.app.NotificationCompat.Builder(c,"focus").setSmallIcon(app.dayora.wear.R.drawable.ic_notification).setContentTitle("Focus complete").setContentText("Nice work. Take a breath.").setAutoCancel(true).build())}
class DayoraListener:WearableListenerService(){override fun onMessageReceived(e:MessageEvent){runBlocking(Dispatchers.IO){withTimeoutOrNull(20000){runCatching{WatchStore(applicationContext).receive(e.sourceNodeId,e.path,e.data);if(e.path in listOf("/dayora/pair","/dayora/paired","/dayora/ack"))WatchStore(applicationContext).flush(applicationContext)}.onFailure{Log.e("DayoraWear","Receive/reply failed: ${e.path}",it)}}?:Log.w("DayoraWear","Receive/reply timed out: ${e.path}")}}}
