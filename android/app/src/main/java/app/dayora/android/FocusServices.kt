package app.dayora.android

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.os.IBinder
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import android.widget.Toast
import android.os.Handler
import android.os.Looper

private const val CHANNEL="focus"
private const val NOTIFICATION=42
private const val PHASE_NOTIFICATION=43
private fun focusPrefs(c:Context)=c.getSharedPreferences("focus",Context.MODE_PRIVATE)
fun activeFocus(c:Context)=focusPrefs(c).getBoolean("blockedEnabled",false)&&focusPrefs(c).getLong("blockedUntil",0)>System.currentTimeMillis()
fun focusRemaining(c:Context)=(focusPrefs(c).getLong("blockedUntil",0)-System.currentTimeMillis()).coerceAtLeast(0)
fun setFocusBlock(c:Context,enabled:Boolean,until:Long=0){focusPrefs(c).edit().putBoolean("blockedEnabled",enabled).putLong("blockedUntil",if(enabled)until else 0).commit()}
fun notifyFocusPhase(c:Context,sessionId:String,phase:String,count:Int){
 val prefs=focusPrefs(c);val key="$sessionId:$phase";if(prefs.getString("lastPhaseNotification",null)==key)return
 val title=when(phase){"FOCUS"->"Focus started";"SHORT_BREAK"->"Short break started";"LONG_BREAK"->"Long break started";else->return}
 val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).putExtra("openFocus",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 val notification=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setColor(0xFF7561D8.toInt()).setContentIntent(open).setAutoCancel(true).setContentTitle(title).setContentText(if(phase=="FOCUS")"Focus session $count" else "Time to take a break").build()
 c.getSystemService(NotificationManager::class.java).notify(PHASE_NOTIFICATION,notification);prefs.edit().putString("lastPhaseNotification",key).apply()
}

class FocusForegroundService:Service(){
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  when(intent?.action){"STOP"->{android.util.Log.i("DayoraFocus","Block state stop");setFocusBlock(this,false)};"START"->{val end=intent.getLongExtra("endsAt",0);val active=end>System.currentTimeMillis();android.util.Log.i("DayoraFocus","Block state start active=$active");setFocusBlock(this,active,end)}}
  if(keepBackgroundService(activeFocus(this),focusPrefs(this).getBoolean("alwaysBackground",false))){startForeground(NOTIFICATION,notification());return START_STICKY}
  stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY
 }
 private fun notification():Notification {
  val end=focusPrefs(this).getLong("blockedUntil",0);val focusing=activeFocus(this)
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("openFocus",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setColor(0xFF7561D8.toInt()).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setShowWhen(focusing)
   .setContentTitle(if(focusing)"Focus in progress" else "Dayora running in background")
   .setContentText(if(focusing)"Stay with this moment" else "Change background mode in Settings")
   .apply{if(focusing){setWhen(end).setUsesChronometer(true);if(Build.VERSION.SDK_INT>=24)setChronometerCountDown(true)}}.build()
 }
 override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Focus timer",NotificationManager.IMPORTANCE_LOW))}
 companion object {
  fun configureBackground(c:Context,enabled:Boolean){
   val prefs=focusPrefs(c);val previous=prefs.getBoolean("alwaysBackground",false)
   prefs.edit().putBoolean("alwaysBackground",enabled).apply()
   try{if(enabled||activeFocus(c))c.startForegroundService(Intent(c,FocusForegroundService::class.java).setAction("REFRESH")) else c.stopService(Intent(c,FocusForegroundService::class.java))}
   catch(e:Exception){prefs.edit().putBoolean("alwaysBackground",previous).apply();throw e}
  }
  private fun refresh(c:Context){
   try{if(keepBackgroundService(activeFocus(c),focusPrefs(c).getBoolean("alwaysBackground",false)))c.startForegroundService(Intent(c,FocusForegroundService::class.java).setAction("REFRESH")) else c.stopService(Intent(c,FocusForegroundService::class.java))}
   catch(e:IllegalStateException){android.util.Log.w("DayoraFocus","Background service start restricted",e)}
   catch(e:SecurityException){android.util.Log.w("DayoraFocus","Foreground service unavailable",e)}
  }
  fun start(c:Context,end:Long){setFocusBlock(c,end>System.currentTimeMillis(),end);refresh(c)}
  fun stop(c:Context){setFocusBlock(c,false);refresh(c)}
 }
}
class FocusBlockerService:AccessibilityService(){
 private var lastBlockedPackage=""
 private var lastBlockAt=0L
 private var foregroundPackage=""
 private val handler=Handler(Looper.getMainLooper())
 private val preferenceListener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->
  if(key=="blockedEnabled"||key=="blockedUntil"||key=="blocked")handler.post{checkForeground()}
 }
 override fun onServiceConnected(){
  super.onServiceConnected()
  focusPrefs(this).registerOnSharedPreferenceChangeListener(preferenceListener)
  android.util.Log.i("DayoraFocus","Accessibility connected; manufacturer=${Build.MANUFACTURER}, model=${Build.MODEL}, android=${Build.VERSION.RELEASE}")
  checkForeground()
  try{PushSyncWorker.enqueue(this,false)}catch(e:Exception){android.util.Log.w("DayoraFocus","Initial sync enqueue failed",e)}
 }
 override fun onDestroy(){focusPrefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener);handler.removeCallbacksAndMessages(null);super.onDestroy()}
 override fun onAccessibilityEvent(event:AccessibilityEvent){
  if(event.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&event.eventType!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return
  val eventPackage=event.packageName?.toString().orEmpty()
  if(eventPackage.isBlank())return
  foregroundPackage=foregroundPackage(foregroundPackage,eventPackage)
  android.util.Log.d("DayoraFocus","Foreground event package=$eventPackage cached=$foregroundPackage")
  checkForeground()
 }
 private fun checkForeground(){
  val pkg=foregroundPackage
  val active=activeFocus(this)
  if(!shouldBlockPackage(active,pkg,packageName,focusPrefs(this).getStringSet("blocked",emptySet()).orEmpty())){
   android.util.Log.d("DayoraFocus","Foreground check skipped active=$active package=$pkg")
   return
  }
  android.util.Log.d("DayoraFocus","Foreground package selected package=$pkg")
  val now=System.currentTimeMillis()
  if(pkg==lastBlockedPackage&&now-lastBlockAt<1000)return
  lastBlockedPackage=pkg;lastBlockAt=now
  showBlockPage(pkg)
 }
 private fun showBlockPage(expected:String){
  val pkg=delayedBlockPackage(expected,foregroundPackage)
  if(pkg==null){android.util.Log.d("DayoraFocus","Delayed block skipped; foreground changed expected=$expected current=$foregroundPackage");return}
  val active=activeFocus(this)
  if(!shouldBlockPackage(active,pkg,packageName,focusPrefs(this).getStringSet("blocked",emptySet()).orEmpty())){
   android.util.Log.d("DayoraFocus","Delayed block skipped active=$active package=$pkg");return
  }
  android.util.Log.i("DayoraFocus","Block activation package=$pkg")
  try{startActivity(Intent(this,FocusBlockActivity::class.java).putExtra("blockedPackage",pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
  catch(_:Exception){performGlobalAction(GLOBAL_ACTION_HOME);Toast.makeText(this,"This app is blocked while Dayora focus is active.",Toast.LENGTH_LONG).show()}
 }
 override fun onInterrupt(){}
 companion object {fun enabled(c:Context):Boolean{
  val component=ComponentName(c,FocusBlockerService::class.java)
  return Settings.Secure.getString(c.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').any{ComponentName.unflattenFromString(it)==component}
 }}
}
