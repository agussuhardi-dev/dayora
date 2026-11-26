package app.dayora.android

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.*

@Composable private fun LegacyPlannerScreen(r:Repository){
 val scope=rememberCoroutineScope();val tick by r.changed.collectAsState();var events by remember{mutableStateOf(emptyList<JSONObject>())};var editing by remember{mutableStateOf<JSONObject?>(null)};var title by remember{mutableStateOf("")};var start by remember{mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0).toString())};var end by remember{mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString())};var error by remember{mutableStateOf("")}
 LaunchedEffect(tick){events=r.list("calendar")};Text("Planner",style=MaterialTheme.typography.headlineLarge)
 OutlinedTextField(title,{title=it},label={Text("Event title")});OutlinedTextField(start,{start=it},label={Text("Start · YYYY-MM-DDTHH:mm")});OutlinedTextField(end,{end=it},label={Text("End · YYYY-MM-DDTHH:mm")});Text("Timezone: ${ZoneId.systemDefault()}")
 Button({scope.launch{try{require(title.trim().length in 1..240);val from=LocalDateTime.parse(start).atZone(ZoneId.systemDefault()).toInstant();val to=LocalDateTime.parse(end).atZone(ZoneId.systemDefault()).toInstant();require(to>from){"End must follow start"};r.edit("calendar",JSONObject(editing?.toString()?:"{}").put("title",title.trim()).put("startsAt",from.toString()).put("endsAt",to.toString()).put("timezone",ZoneId.systemDefault().id));editing=null;title=""}catch(e:Exception){error=e.message?:"Invalid event"}}}){Text(if(editing==null)"Create event" else "Save event")};Text(error)
 LazyColumn{items(events){event->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){Text(event.optString("title"));Text(event.optString("startsAt"));Row{TextButton({editing=event;title=event.getString("title");start=LocalDateTime.ofInstant(Instant.parse(event.getString("startsAt")),ZoneId.systemDefault()).toString();end=LocalDateTime.ofInstant(Instant.parse(event.getString("endsAt")),ZoneId.systemDefault()).toString()}){Text("Edit")};TextButton({scope.launch{try{r.edit("calendar",event,true)}catch(e:Exception){error=e.message.orEmpty()}}}){Text("Delete")}}}}}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(r:Repository){
  val activity=LocalContext.current as? MainActivity
 val scope=rememberCoroutineScope()
 val tick by r.changed.collectAsState()
 val pushStatus by r.pushStatus.collectAsState()
 var selected by remember{mutableStateOf(r.blockedPackages())}
 val apps=remember{r.installedApps()}
 var theme by remember{mutableStateOf("system")}
 var accent by remember{mutableStateOf("#7561d8")}
 var state by remember{mutableStateOf<JSONObject?>(null)}
 var focusMinutes by remember{mutableStateOf("25")}
 var shortBreakMinutes by remember{mutableStateOf("5")}
 var longBreakMinutes by remember{mutableStateOf("15")}
 var pomodoroDirty by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var showApps by remember{mutableStateOf(false)}
 var query by remember{mutableStateOf("")}
 var enabled by remember{mutableStateOf(FocusBlockerService.enabled(r.context))}
 var keepScreenOn by remember{mutableStateOf(r.context.getSharedPreferences("visual-accessibility",0).getBoolean("keepScreenOn",false))}
 var alwaysBackground by remember{mutableStateOf(r.context.getSharedPreferences("focus",0).getBoolean("alwaysBackground",false))}
 val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(!granted)error="Notifications disabled. Enable them in Android settings to see the background notification."}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)enabled=FocusBlockerService.enabled(r.context)}
  lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer)}
 }
 LaunchedEffect(tick){try{state=r.list("settings").firstOrNull();state?.optJSONObject("settings")?.optJSONObject("appearance")?.let{theme=it.optString("theme","system");accent=it.optString("accent","#7561d8")}}catch(e:Exception){error=e.message?:"Could not load appearance"}}
 LaunchedEffect(state){if(!pomodoroDirty)state?.optJSONObject("settings")?.optJSONObject("pomodoro")?.let{focusMinutes=it.optInt("focusMinutes",25).toString();shortBreakMinutes=it.optInt("shortBreakMinutes",5).toString();longBreakMinutes=it.optInt("longBreakMinutes",15).toString()}}
 val editable=!busy&&state!=null&&state?.optBoolean("_pending")!=true
 val validDurations=listOf(focusMinutes,shortBreakMinutes,longBreakMinutes).all{(it.toIntOrNull()?:0) in 1..1440}
 fun savePomodoro(){
  if(!editable||!validDurations)return
  busy=true;error=""
  scope.launch{try{
   val current=JSONObject(r.request("GET","/api/settings")).put("id","settings")
   val settings=current.getJSONObject("settings")
   val pomodoro=settings.optJSONObject("pomodoro")?:JSONObject()
   settings.put("pomodoro",pomodoro.put("focusMinutes",focusMinutes.toInt()).put("shortBreakMinutes",shortBreakMinutes.toInt()).put("longBreakMinutes",longBreakMinutes.toInt()))
   r.edit("settings",current)
   pomodoroDirty=false;state=r.list("settings").firstOrNull()
  }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message?:"Could not save Pomodoro"}finally{busy=false}}
 }
 fun saveAppearance(nextTheme:String=theme,nextAccent:String=accent){
  if(!editable)return
  busy=true;error=""
  scope.launch{try{
   val current=JSONObject(r.request("GET","/api/settings")).put("id","settings")
   val appearance=current.getJSONObject("settings").optJSONObject("appearance")?:JSONObject()
   current.getJSONObject("settings").put("appearance",appearance.put("theme",nextTheme).put("accent",nextAccent))
   r.edit("settings",current)
   theme=nextTheme;accent=nextAccent;r.appearance.value=theme to accent
   state=r.list("settings").firstOrNull()
  }catch(e:Exception){error=e.message?:"Could not save appearance"}finally{busy=false}}
 }
 LazyColumn(contentPadding=PaddingValues(20.dp,24.dp,20.dp,44.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Text("Settings",style=MaterialTheme.typography.headlineLarge)}
  item{GlassCard{
   Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Filled.Timer,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Text("Pomodoro",style=MaterialTheme.typography.titleLarge)}
   Spacer(Modifier.height(12.dp))
   listOf(Triple("Focus",focusMinutes,{value:String->focusMinutes=value}),Triple("Short break",shortBreakMinutes,{value:String->shortBreakMinutes=value}),Triple("Long break",longBreakMinutes,{value:String->longBreakMinutes=value})).forEach{(label,value,update)->
    OutlinedTextField(value,{update(it);pomodoroDirty=true},Modifier.fillMaxWidth(),enabled=editable,label={Text(label)},suffix={Text("min")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),isError=(value.toIntOrNull()?:0) !in 1..1440)
   }
   Button({savePomodoro()},Modifier.fillMaxWidth(),enabled=editable&&pomodoroDirty&&validDurations){Text(if(busy)"Saving…" else "Save")}
   if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
  }}
  item{if(pushStatus.isNotBlank())Text(pushStatus,color=if(pushStatus.startsWith("Push enabled"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}
  item{WearSettings(r)}
  item{GlassCard{
   Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Filled.Palette,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Text("Appearance",style=MaterialTheme.typography.titleLarge)}
   
   Spacer(Modifier.height(12.dp))
   SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){
    val choices=listOf(Triple("system","System",Icons.Filled.SettingsBrightness),Triple("light","Light",Icons.Filled.LightMode),Triple("dark","Dark",Icons.Filled.DarkMode))
    choices.forEachIndexed{index,(value,label,icon)->SegmentedButton(selected=theme==value,onClick={saveAppearance(nextTheme=value)},shape=SegmentedButtonDefaults.itemShape(index,choices.size),enabled=editable,icon={Icon(icon,null,Modifier.size(18.dp))}){Text(label)}}
   }
   
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    listOf("#7561d8" to "Violet","#CB7249" to "Clay","#2E8B78" to "Sage","#4F78B8" to "Blue").forEach{(value,label)->
     androidx.compose.foundation.layout.Box(Modifier.size(48.dp).selectable(accent.equals(value,true),enabled=editable,role=androidx.compose.ui.semantics.Role.RadioButton,onClick={saveAppearance(nextAccent=value)}).semantics{contentDescription=label},contentAlignment=Alignment.Center){
      Surface(Modifier.size(32.dp),shape=androidx.compose.foundation.shape.CircleShape,color=androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value))){if(accent.equals(value,true))Box(contentAlignment=Alignment.Center){Icon(androidx.compose.material.icons.Icons.Filled.Check,null,tint=androidx.compose.ui.graphics.Color.White,modifier=Modifier.size(18.dp))}}
     }
    }
    IconButton(onClick={saveAppearance(nextTheme="system",nextAccent="#7561d8")},enabled=editable){Icon(Icons.Filled.RestartAlt,"Reset theme")}
   }
   if(busy||state?.optBoolean("_pending")==true)Text("Saving appearance…",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
   
  }}
  item{GlassCard{
   Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Filled.PhoneAndroid,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Text("Android",style=MaterialTheme.typography.titleLarge)}
    Row(Modifier.fillMaxWidth().toggleable(keepScreenOn,role=androidx.compose.ui.semantics.Role.Switch){value->r.context.getSharedPreferences("visual-accessibility",0).edit().putBoolean("keepScreenOn",value).apply();keepScreenOn=value;activity?.applyKeepScreenOn()}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Keep screen on");Text("Prevent sleep while Dayora is open",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(keepScreenOn,null)}
    Row(Modifier.fillMaxWidth().toggleable(alwaysBackground,role=androidx.compose.ui.semantics.Role.Switch){value->try{FocusForegroundService.configureBackground(r.context,value);alwaysBackground=value;if(value&&android.os.Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(r.context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}catch(e:Exception){error=e.message?:"Could not change background mode"}}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Always run in background");Text("Uses a permanent notification and more battery",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(alwaysBackground,null)}
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
   }}
   item{GlassCard{
    Text("App blocking",style=MaterialTheme.typography.titleLarge)
   Text(if(enabled)"Accessibility active" else "Accessibility permission needed",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text("${selected.size} apps · blocked during focus",style=MaterialTheme.typography.bodyMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({query="";showApps=true},Modifier.weight(1f)){Text("Choose apps")};if(!enabled)OutlinedButton({r.context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Enable")}}
  }}
  item{OutlinedButton({scope.launch{try{FocusForegroundService.stop(r.context);r.logout()}catch(e:Exception){error=e.message?:"Could not sign out"}}},Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Sign out")}}
 }
 if(showApps)ModalBottomSheet(onDismissRequest={showApps=false}){
  Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text("Blocked apps",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton({showApps=false}){Text("Done")}}
   OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search apps")},singleLine=true)
   val filtered=apps.filter{it.label.contains(query,true)||it.packageName.contains(query,true)}
   if(filtered.isEmpty())Text("No apps found",Modifier.padding(vertical=16.dp))
   LazyColumn(Modifier.fillMaxWidth().weight(1f,false),contentPadding=PaddingValues(vertical=12.dp)){
    items(filtered,key={it.packageName}){app->
     Row(Modifier.fillMaxWidth().toggleable(value=app.packageName in selected,role=androidx.compose.ui.semantics.Role.Switch,onValueChange={checked->
      val next=selected.toMutableSet().apply{if(checked)add(app.packageName)else remove(app.packageName)}
      try{r.setBlockedPackages(next);selected=next}catch(e:Exception){error=e.message?:"Could not save blocked apps";showApps=false}
     }).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(app.label);Text(app.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(app.packageName in selected,null)}
    }
   }
  }
 }
}
