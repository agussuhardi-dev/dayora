package app.dayora.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

val DayoraPurple=Color(0xFF7561D8)
private val DayoraInk=Color(0xFF29243A)
private val DayoraBackground=Color(0xFFF8F6FC)
private val DayoraMint=Color(0xFFDDF2EC)
private val DayoraLilac=Color(0xFFE7E0FA)
private val DayoraPeach=Color(0xFFFFE7DA)
private val GlassBorder=Color.White.copy(alpha=.42f)
private val DefaultBackend=BuildConfig.DAYORA_API_URL

class MainActivity:ComponentActivity(){private val repository by lazy{Repository.get(applicationContext)};private var foregroundToken by mutableIntStateOf(0);private var focusEvent by mutableIntStateOf(0);private fun consumeFocusIntent(i:Intent){if(i.getBooleanExtra("openFocus",false))focusEvent++};override fun onCreate(b:Bundle?){super.onCreate(b);consumeFocusIntent(intent);setContent{DayoraTheme{App(repository,foregroundToken,focusEvent)}}};override fun onNewIntent(i:Intent){super.onNewIntent(i);setIntent(i);consumeFocusIntent(i)};override fun onResume(){super.onResume();applyKeepScreenOn()};override fun onStart(){super.onStart();foregroundToken++;repository.setForeground(true)};override fun onStop(){repository.setForeground(false);super.onStop()};fun applyKeepScreenOn(){if(getSharedPreferences("visual-accessibility",0).getBoolean("keepScreenOn",false))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}

@Composable fun DayoraTheme(content: @Composable () -> Unit){
 val r=Repository.get(LocalContext.current);val appearance by r.appearance.collectAsState();val dark=appearance.first=="dark"||(appearance.first=="system"&&androidx.compose.foundation.isSystemInDarkTheme());val accent=runCatching{Color(android.graphics.Color.parseColor(appearance.second))}.getOrDefault(DayoraPurple)
 val solid=LocalContext.current.getSharedPreferences("visual-accessibility",0).getBoolean("solid",false);val scheme=if(dark)darkColorScheme(primary=accent,secondary=Color(0xFF9DD8C4),surface=Color(0xFF191622),background=Color(0xFF100E16))else lightColorScheme(primary=accent,onPrimary=Color.White,background=DayoraBackground,surface=Color(0xFFFEFBFF),onSurface=DayoraInk,secondary=Color(0xFF408A72));CompositionLocalProvider(LocalSolidSurfaces provides solid){MaterialTheme(colorScheme=scheme,typography=Typography(headlineLarge=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.Bold),headlineMedium=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold),titleLarge=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold)),content=content)}
}

data class Destination(val label:String,val icon:ImageVector)
private val destinations=listOf(Destination("Today",Icons.Filled.SpaceDashboard),Destination("Focus",Icons.Filled.Timer),Destination("Tasks",Icons.Filled.Checklist),Destination("Planner",Icons.Filled.CalendarToday),Destination("Settings",Icons.Filled.Settings))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(r:Repository, foregroundToken:Int, focusEvent:Int=0){
 val tick by r.changed.collectAsState()
 var workspaces by remember { mutableStateOf(emptyList<JSONObject>()) }
 var workspaceId by remember { mutableStateOf(r.context.getSharedPreferences("workspace",0).getString("selectedId", "").orEmpty()) }
 var switching by remember { mutableStateOf(false) }
 var workspaceError by remember { mutableStateOf("") }
 LaunchedEffect(tick) {
  try {
   workspaces = r.list("workspaces")
   if (workspaces.none { it.optString("id") == workspaceId }) {
    workspaceId = workspaces.firstOrNull()?.optString("id").orEmpty()
    r.context.getSharedPreferences("workspace",0).edit().putString("selectedId",workspaceId).apply()
   }
   workspaceError = ""
  } catch (e: kotlinx.coroutines.CancellationException) { throw e }
  catch (e: Exception) { workspaceError = "Could not load workspaces" }
 }
 if (switching) ModalBottomSheet(onDismissRequest = { switching = false }) {
  Text("Choose workspace", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
  LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
   items(workspaces, key = { it.optString("id") }) { workspace ->
    val selected = workspace.optString("id") == workspaceId
    Surface(onClick = {
     workspaceId = workspace.optString("id")
     r.context.getSharedPreferences("workspace",0).edit().putString("selectedId",workspaceId).apply()
     r.changed.value++
     switching = false
    }, modifier = Modifier.fillMaxWidth()) {
     ListItem(headlineContent = { Text(workspace.optString("name")) }, leadingContent = { Icon(Icons.Filled.Workspaces,null) }, trailingContent = { if(selected) Icon(Icons.Filled.Check,"Selected") })
    }
   }
  }
  if(workspaceError.isNotEmpty()) Text(workspaceError, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
  Spacer(Modifier.height(24.dp))
 }

 val auth by r.authenticated.collectAsState();var page by rememberSaveable{mutableStateOf("Today")};var lastForegroundToken by remember{mutableIntStateOf(0)}
 LaunchedEffect(auth,focusEvent){if(auth&&focusEvent>0)page="Focus"}
  LaunchedEffect(auth){if(!auth)FocusForegroundService.configureBackground(r.context,false) else if(r.context.getSharedPreferences("focus",0).getBoolean("alwaysBackground",false))try{FocusForegroundService.configureBackground(r.context,true)}catch(e:Exception){android.util.Log.w("Dayora","Could not restore background service",e)}}
  LaunchedEffect(auth,foregroundToken){if(auth&&foregroundToken>lastForegroundToken){lastForegroundToken=foregroundToken;try{if(r.list("focus").any{it.optString("state")=="RUNNING"&&it.optString("phase")=="FOCUS"&&it.optString("runningSince").isNotBlank()&&it.optLong("durationSeconds")>it.optLong("activeDurationSeconds")})page="Focus"}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){android.util.Log.w("Dayora","Could not check running session",e)}}}
 if(!auth)Login(r) else Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
  val title = if(page == "Sessions") "Focus history" else page
  CenterAlignedTopAppBar(title={ Text(title, style=MaterialTheme.typography.titleLarge) }, navigationIcon={ Icon(painterResource(R.drawable.ic_notification), "Dayora", Modifier.padding(start=16.dp).size(28.dp), tint=MaterialTheme.colorScheme.primary) }, actions={
   val name = workspaces.firstOrNull { it.optString("id") == workspaceId }?.optString("name") ?: "Workspace"
   TextButton(onClick={ switching=true }, enabled=workspaces.isNotEmpty()) { Icon(Icons.Filled.Workspaces,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(name, maxLines=1); Icon(Icons.Filled.ExpandMore,null) }
  })
 },bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=2.dp){destinations.forEach{d->NavigationBarItem(page==d.label,{page=d.label},{Icon(d.icon,null)},label={Text(d.label)})}}}){padding->Crossfade(page,label="page"){selected->Box(Modifier.fillMaxSize().padding(padding)){when(selected){"Today"->Today(r){page=it};"Tasks"->Tasks(r);"Focus"->FocusScreen(r){page="Sessions"};"Sessions"->FocusSessionsScreen(r){page="Focus"};"Planner"->PlannerScreen(r);else->SettingsScreen(r)}}}}
}

@Composable fun Login(r:Repository){
 val debug=LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0;var url by rememberSaveable{mutableStateOf(DefaultBackend)};var user by rememberSaveable{mutableStateOf("")};var password by rememberSaveable{mutableStateOf("")};var register by rememberSaveable{mutableStateOf(false)};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DayoraLilac.copy(.7f),MaterialTheme.colorScheme.background,DayoraMint.copy(.55f)))).padding(24.dp),contentAlignment=Alignment.Center){Card(Modifier.fillMaxWidth().widthIn(max=480.dp),shape=RoundedCornerShape(30.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface.copy(.94f)),elevation=CardDefaults.cardElevation(8.dp)){Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(painterResource(R.drawable.ic_notification),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text("dayora.",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)};Text(if(register)"Create your space." else "Make space for what matters.",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.semantics{heading()});Text(if(register)"Start with one clear account." else "A calmer way to plan, focus, and finish.",color=MaterialTheme.colorScheme.onSurfaceVariant);if(debug)OutlinedTextField(url,{url=it},label={Text("Backend URL")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(user,{user=it},label={Text("Username")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);Button({scope.launch{busy=true;error="";try{r.login(if(debug)url else DefaultBackend,user.trim(),password,register)}catch(e:Exception){error=e.message?:"Could not connect"}finally{busy=false}}},enabled=!busy&&user.isNotBlank()&&password.isNotBlank(),modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(14.dp)){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Text(if(register)"Create account" else "Sign in")};TextButton({register=!register;error=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text(if(register)"Already have an account? Sign in" else "New to Dayora? Create account")}}}}
}

@Composable fun PageHeader(eyebrow:String,title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(eyebrow.uppercase(),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold);Text(title,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.semantics{heading()});Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun LegacyToday(r: Repository, navigate: (String) -> Unit) {
 val tick by r.changed.collectAsState()
 var tasks by remember { mutableStateOf(emptyList<JSONObject>()) }
 var events by remember { mutableStateOf(emptyList<JSONObject>()) }
 LaunchedEffect(tick) {
  tasks = r.list("tasks").filter { it.optString("status", "TODO") != "DONE" }
  events = r.list("calendar")
 }
 val greeting = when (LocalTime.now().hour) { in 0..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 44.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
  item { PageHeader("$greeting · ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}", "Make space for what matters.", "A calmer view of your day, one clear step at a time.") }
  item {
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    AssistChip({ navigate("Tasks") }, { Text("${tasks.size} tasks") }, leadingIcon = { Icon(Icons.Filled.Checklist, null) })
    AssistChip({ navigate("Planner") }, { Text("${events.size} events") }, leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) })
   }
  }
  item {
   Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = DayoraLilac)) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
     Icon(Icons.Filled.Spa, null, Modifier.size(42.dp), tint = DayoraPurple)
     Text("ONE THING AT A TIME", style = MaterialTheme.typography.labelSmall, color = DayoraPurple, fontWeight = FontWeight.Bold)
     Text("Ready for one clear thing?", style = MaterialTheme.typography.headlineMedium)
     Text("Choose one small step and give it your full attention.")
     Button({ navigate("Focus") }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Timer, null); Spacer(Modifier.width(8.dp)); Text("Start a focus session") }
    }
   }
  }
  item { Text("Your day, at a glance", style = MaterialTheme.typography.titleLarge) }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Icon(Icons.Filled.Checklist, null, tint = Color(0xFFCB7249))
    Text("Your priorities", style = MaterialTheme.typography.titleLarge)
    if (tasks.isEmpty()) Text("Your list is clear. Add one meaningful task to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else tasks.take(3).forEach { Text("○  ${it.optString("title")}") }
    TextButton({ navigate("Tasks") }) { Text("Manage tasks"); Icon(Icons.Filled.ArrowForward, null) }
   }
  }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Icon(Icons.Filled.CalendarMonth, null, tint = DayoraPurple)
    Text("Today’s schedule", style = MaterialTheme.typography.titleLarge)
    if (events.isEmpty()) Text("Your day has breathing room.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else events.take(3).forEach { Text(it.optString("title")) }
    TextButton({ navigate("Planner") }) { Text("Open planner"); Icon(Icons.Filled.ArrowForward, null) }
   }
  }
 }
}

@Composable private fun LegacyTasks(r: Repository) {
 val tick by r.changed.collectAsState()
 var title by remember { mutableStateOf("") }
 var list by remember { mutableStateOf(emptyList<JSONObject>()) }
 var error by remember { mutableStateOf("") }
 val scope = rememberCoroutineScope()
 LaunchedEffect(tick) { list = r.list("tasks") }
 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 44.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
  item { PageHeader("Tasks", "Make space for progress.", "Capture it, then take one clear step.") }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    OutlinedTextField(title, { title = it }, label = { Text("What needs doing?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button({ scope.launch { try { r.edit("tasks", JSONObject().put("title", title.trim()).put("notes", "").put("priority", "NONE").put("status", "TODO")); title = "" } catch (e: Exception) { error = e.message.orEmpty() } } }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.AddTask, null); Spacer(Modifier.width(8.dp)); Text("Add task") }
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
   }
  }
  items(list, key = { it.optString("id") }) { task ->
   Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
     Icon(if(task.optString("status")=="DONE") Icons.Filled.CheckCircle else if(task.optString("status")=="IN_PROGRESS") Icons.Filled.TrendingUp else Icons.Filled.RadioButtonUnchecked, task.optString("status", "TODO").lowercase().replace('_', ' '), Modifier.size(20.dp), tint = if(task.optString("status")=="DONE") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
     Spacer(Modifier.width(12.dp))
     Text(task.optString("title"), Modifier.weight(1f))
     if (task.optBoolean("_pending")) Text("Syncing", style = MaterialTheme.typography.labelSmall)
    }
   }
  }
 }
}
