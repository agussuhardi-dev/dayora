package app.dayora.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.*
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

@Entity(primaryKeys=["owner","kind","id"])
data class Record(val owner:String,val kind:String,val id:String,val json:String,val pending:Boolean=false,val deleted:Boolean=false)
@Entity data class Operation(@PrimaryKey val id:String=UUID.randomUUID().toString(),val owner:String,val kind:String,val recordId:String,val method:String,val path:String,val body:String,val state:String="pending")
@Dao interface Store {
 @Query("SELECT * FROM Record WHERE owner=:owner AND kind=:kind AND deleted=0") suspend fun list(owner:String,kind:String):List<Record>
 @Query("SELECT * FROM Record WHERE owner=:owner AND kind=:kind AND id=:id") suspend fun get(owner:String,kind:String,id:String):Record?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(record:Record)
 @Insert suspend fun enqueue(operation:Operation)
 @Query("SELECT * FROM Operation WHERE owner=:owner ORDER BY rowid") suspend fun queue(owner:String):List<Operation>
 @Query("DELETE FROM Operation WHERE id=:id") suspend fun remove(id:String)
 @Query("UPDATE Operation SET state=:state WHERE id=:id") suspend fun state(id:String,state:String)
 @Query("DELETE FROM Record WHERE owner=:owner AND kind=:kind AND id=:id") suspend fun delete(owner:String,kind:String,id:String)
}
@androidx.room.Database(entities=[Record::class,Operation::class],version=1,exportSchema=false) abstract class Database:RoomDatabase(){abstract fun store():Store}

class Vault(context:Context) {
 private val prefs=context.getSharedPreferences("session",Context.MODE_PRIVATE)
 private fun key():SecretKey { val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}; return (ks.getKey("dayora",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("dayora",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey() }
 fun save(value:String){val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());prefs.edit().putString("value",Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray()),Base64.NO_WRAP)).commit()}
 fun load():String = try {val bytes=Base64.decode(prefs.getString("value","")!!,Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)))}catch(_:Exception){"{}"}
}
class HttpFailure(val status:Int,message:String):Exception(message)
class Repository private constructor(val context:Context) {
 companion object { @Volatile private var instance:Repository?=null; fun get(context:Context)=instance?:synchronized(this){instance?:Repository(context.applicationContext).also{instance=it}} }
 private val vault=Vault(context)
 private var session=JSONObject(vault.load())
 val owner get()=session.optString("owner")
 val origin get()=session.optString("origin")
 val authenticated=MutableStateFlow(owner.isNotEmpty())
 val message=MutableStateFlow("")
 val pushStatus=MutableStateFlow("")
 val changed=MutableStateFlow(0)
 val appearance=MutableStateFlow("system" to "#7561d8")
 val db=Room.databaseBuilder(context,Database::class.java,"dayora.db").build()
 private val client=OkHttpClient.Builder().followRedirects(false).pingInterval(30,java.util.concurrent.TimeUnit.SECONDS).build()
 private val realtimeScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val syncMutex=Mutex()
 private var socket:WebSocket?=null
 private var realtimeJob:Job?=null
 @Volatile private var foreground=false
 fun setForeground(value:Boolean){foreground=value;if(value)startRealtime()}
 private fun persist(){vault.save(session.toString())}
 fun startRealtime(){
  if(realtimeJob?.isActive==true)return
  realtimeJob=realtimeScope.launch{authenticated.collectLatest{auth->
   if(!auth)return@collectLatest
   while(authenticated.value){
    val closed=CompletableDeferred<Unit>()
    val events=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    val listener=object:WebSocketListener(){
     override fun onOpen(webSocket:WebSocket,response:Response){android.util.Log.i("DayoraRealtime","WebSocket connected");events.trySend(Unit)}
     override fun onMessage(webSocket:WebSocket,text:String){if(runCatching{JSONObject(text).optString("type")}.getOrNull()=="INVALIDATE"){android.util.Log.d("DayoraRealtime","Invalidation received");events.trySend(Unit)}}
     override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){android.util.Log.w("DayoraRealtime","WebSocket failed: HTTP ${response?.code ?: "none"}",t);if(response?.code==401)authenticated.value=false;closed.complete(Unit)}
     override fun onClosing(webSocket:WebSocket,code:Int,reason:String){webSocket.close(code,reason);closed.complete(Unit)}
     override fun onClosed(webSocket:WebSocket,code:Int,reason:String){closed.complete(Unit)}
    }
    val pull=launch{for(event in events){try{sync()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message.value=e.message?:"Sync failed";delay(5000);events.trySend(Unit)}}}
    try{
     val wsOrigin=origin.replaceFirst("https://","wss://").replaceFirst("http://","ws://")
     socket=client.newWebSocket(Request.Builder().url("$wsOrigin/ws/events").header("Cookie",session.optString("cookies")).build(),listener)
     events.trySend(Unit)
     closed.await()
    }finally{socket?.cancel();socket=null;pull.cancel();events.close()}
    delay(5000)
   }
  }}
 }
 fun stopRealtime(){realtimeJob?.cancel();realtimeJob=null}
 suspend fun request(method:String,path:String,body:String?=null,key:String?=null,focused:Boolean=false):String=withContext(Dispatchers.IO){
  require(path.startsWith("/api/"));val request=Request.Builder().url(origin+path).header("Cookie",session.optString("cookies"))
  if(method!="GET"){request.header(session.optString("csrfHeader","X-CSRF-TOKEN"),session.optString("csrf"));if(key!=null)request.header("Idempotency-Key",key)}
  request.method(method,if(method=="GET")null else (body?:"{}").toRequestBody((if(path.endsWith("/login"))"application/x-www-form-urlencoded" else "application/json").toMediaType()))
  val call=client.newCall(request.build());if(focused)call.timeout().timeout(6,java.util.concurrent.TimeUnit.SECONDS)
  call.execute().use{response ->
   val cookies=session.optString("cookies").split("; ").filter{it.contains("=")}.associate{it.substringBefore("=") to it.substringAfter("=")}.toMutableMap()
   response.headers.values("Set-Cookie").forEach{val cookie=it.substringBefore(';');cookies[cookie.substringBefore('=')]=cookie.substringAfter('=')};session.put("cookies",cookies.entries.joinToString("; "){"${it.key}=${it.value}"});persist()
   val text=response.body?.string().orEmpty();if(!response.isSuccessful){if(response.code==401){authenticated.value=false;message.value="Session expired. Sign in to resume this account’s pending edits."};throw HttpFailure(response.code,"HTTP ${response.code}: ${text.take(200)}")};text
  }
 }
 suspend fun csrf(){val token=JSONObject(request("GET","/api/auth/csrf"));session.put("csrfHeader",token.getString("headerName")).put("csrf",token.getString("token"));persist()}
 suspend fun login(url:String,user:String,password:String,register:Boolean){
  require(url.startsWith("https://") && java.net.URI(url).host!=null){"Use an HTTPS backend URL"}
  val normalized=url.trimEnd('/');if(origin!=normalized){session=JSONObject().put("origin",normalized)}
  csrf();if(register)request("POST","/api/auth/register",JSONObject().put("username",user).put("password",password).put("timezone",java.time.ZoneId.systemDefault().id).toString())
  request("POST","/api/auth/login","username=${java.net.URLEncoder.encode(user,"UTF-8")}&password=${java.net.URLEncoder.encode(password,"UTF-8")}");csrf()
  // Origin is part of the account namespace: identical UUIDs on distinct servers never share records.
  session.put("owner",normalized+"|"+JSONObject(request("GET","/api/auth/me")).getString("id"));persist();authenticated.value=true;schedule();sync()
 }
 private val pushPrefs=context.getSharedPreferences("push",Context.MODE_PRIVATE)
 fun cachePushToken(token:String){pushPrefs.edit().putString("token",token).apply()}
 suspend fun registerPushToken(token:String=pushPrefs.getString("token",null).orEmpty()){
  val actual=token.ifBlank{
   runCatching{FirebaseMessaging.getInstance().token.await()}.onFailure{
    if(it is kotlinx.coroutines.CancellationException)throw it
    android.util.Log.w("DayoraPush","FCM token unavailable: ${it.javaClass.simpleName}");pushStatus.value="Push unavailable: could not obtain FCM token"
   }.getOrDefault("")
  }
  if(actual.isBlank()){if(pushStatus.value.isBlank())pushStatus.value="Push unavailable: no FCM token";return}
  cachePushToken(actual)
  if(!authenticated.value)return
  // ponytail: PUT on each sync repairs server-side token loss; add acknowledgement caching only if this becomes measurable.
  request("PUT","/api/push/token",JSONObject().put("token",actual).toString())
  pushPrefs.edit().putString("registeredToken",actual).putString("registeredOwner",owner).apply();pushStatus.value="Push enabled"
 }
 suspend fun refreshFocus()=syncMutex.withLock{
  if(!authenticated.value)return@withLock
  val raw=request("GET","/api/focus/current",focused=true)
  if(raw.isBlank()){FocusForegroundService.stop(context);changed.value++;return@withLock}
  val focus=JSONObject(raw);db.store().put(Record(owner,"focus",focus.getString("id"),focus.toString()));updateFocusService(focus);changed.value++
 }
 suspend fun logout(){csrf();val token=context.getSharedPreferences("push",0).getString("token",null);if(token!=null)request("DELETE","/api/push/token",JSONObject().put("token",token).toString());request("POST","/api/auth/logout");session.remove("cookies");session.remove("owner");persist();authenticated.value=false;pushPrefs.edit().remove("registeredToken").remove("registeredOwner").apply();FocusForegroundService.stop(context)}
 suspend fun list(kind:String)=db.store().list(owner,kind).map{JSONObject(it.json).put("_pending",it.pending)}
 suspend fun edit(kind:String,value:JSONObject,delete:Boolean=false){
  val account=owner;require(account.isNotEmpty());val id=value.optString("id").ifEmpty{UUID.randomUUID().toString()};val existing=db.store().get(account,kind,id)
  require(existing?.pending!=true){"Wait for this item’s pending edit to sync before editing again."}
  value.put("id",id)
  val create=existing==null;val route=if(kind=="settings")"/api/settings" else "/api/${if(kind=="focus")"focus" else kind}"+(if(create)"" else "/$id?baseRevision=${value.optLong("revision")}")
  val method=if(delete)"DELETE" else if(create&&kind!="settings")"POST" else "PUT"
  db.withTransaction{db.store().put(Record(account,kind,id,value.toString(),true,delete));db.store().enqueue(Operation(owner=account,kind=kind,recordId=id,method=method,path=route,body=value.toString()))};changed.value++;schedule()
 }
 fun schedule(){WorkManager.getInstance(context).enqueueUniqueWork("dayora-sync",ExistingWorkPolicy.APPEND_OR_REPLACE,OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())}
 suspend fun sync()=syncMutex.withLock{
  if(!authenticated.value)return@withLock;val account=owner
  csrf();runCatching{registerPushToken()}.onFailure{if(it is kotlinx.coroutines.CancellationException)throw it;android.util.Log.w("DayoraPush","Token registration failed: ${if(it is HttpFailure) "HTTP ${it.status}" else it.javaClass.simpleName}");pushStatus.value="Push registration failed; retrying on next sync"};val actual=JSONObject(request("GET","/api/auth/me")).getString("id");check(account==origin+"|"+actual){"Account changed; refusing queued writes"}
  for(op in db.store().queue(account)){
   if(op.state!="pending")continue
   // Non-idempotent creates/actions cannot be replayed after an ambiguous network failure.
   val unsafe=op.method=="POST"&&op.kind!="tasks"
   if(unsafe)db.store().state(op.id,"uncertain")
   try{val response=request(op.method,op.path,op.body,if(op.kind=="tasks"&&op.method=="POST")op.recordId else null)
    db.withTransaction{db.store().delete(account,op.kind,op.recordId);if(response.isNotBlank()){val value=JSONObject(response);db.store().put(Record(account,op.kind,value.optString("id",op.recordId),value.toString()))};db.store().remove(op.id)}
   }catch(e:HttpFailure){if(e.status==401){if(unsafe)db.store().state(op.id,"pending");throw e};db.store().state(op.id,if(e.status==409)"conflict" else "error");message.value="${op.kind}: ${e.message}. Local edit retained; review Sync issues."}catch(e:Exception){if(unsafe)message.value="Uncertain ${op.kind} request retained. Review server before retrying.";throw e}
  }
  val currentFocus=request("GET","/api/focus/current")
  if(currentFocus.isNotBlank()){
   val focus=JSONObject(currentFocus)
   db.store().put(Record(account,"focus",focus.getString("id"),focus.toString()))
   updateFocusService(focus)
  }else FocusForegroundService.stop(context)
  for(kind in listOf("workspaces","tasks","calendar","focus/history","settings")){
   val path=when(kind){"tasks"->"/api/tasks?page=";"calendar"->"/api/calendar?from=${java.time.Instant.now()}&to=${java.time.Instant.now().plusSeconds(6*86400)}";"focus/history"->"/api/focus/history?since=${java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()}";else->"/api/$kind"}
   val values=mutableListOf<JSONObject>()
   if(kind=="tasks"){var page=0;do{val result=JSONObject(request("GET",path+page+"&size=100"));val array=result.getJSONArray("content");for(i in 0 until array.length())values.add(array.getJSONObject(i));page++}while(page<result.optInt("totalPages",1))}
   else {val raw=request("GET",path);if(kind=="settings")values.add(JSONObject(raw).put("id","settings")) else {val array=JSONArray(raw);for(i in 0 until array.length())values.add(array.getJSONObject(i))}}
   if(kind=="focus/history" && currentFocus.isNotBlank()) { val active=JSONObject(currentFocus); if(values.none{it.optString("id")==active.optString("id")})values.add(active) }
   val localKind=if(kind=="focus/history")"focus" else kind
   db.withTransaction {val ids=values.map{it.getString("id")}.toSet();db.store().list(account,localKind).filter{!it.pending&&it.id !in ids}.forEach{db.store().delete(account,localKind,it.id)};values.forEach{v->if(db.store().get(account,localKind,v.getString("id"))?.pending!=true)db.store().put(Record(account,localKind,v.getString("id"),v.toString()))}}
   if(kind=="settings")values.firstOrNull()?.optJSONObject("settings")?.optJSONObject("appearance")?.let{appearance.value=it.optString("theme","system") to it.optString("accent","#7561d8")}
  }
  // Focus blocking uses /api/focus/current only; history may contain expired sessions.
  changed.value++
  runCatching { WearBridge(context).syncConnected() }.onFailure { android.util.Log.w("DayoraWear", "Watch sync failed", it) }
 }
 suspend fun issues()=db.store().queue(owner).filter{it.state!="pending"}
 suspend fun discard(op:Operation){require(op.owner==owner);db.withTransaction{db.store().delete(owner,op.kind,op.recordId);db.store().remove(op.id)};sync()}
 suspend fun focusAction(path:String,body:JSONObject=JSONObject()){csrf();val response=JSONObject(request("POST",path,body.toString()));db.store().put(Record(owner,"focus",response.getString("id"),response.toString()));changed.value++;updateFocusService(response);schedule()}
 private fun updateFocusService(value:JSONObject){val state=value.optString("state");val phase=value.optString("phase");if(state=="RUNNING"&&phase=="FOCUS"){val remaining=value.optLong("durationSeconds")-value.optLong("activeDurationSeconds");val since=java.time.Instant.parse(value.getString("runningSince")).toEpochMilli();FocusForegroundService.start(context,since+remaining*1000);notifyFocusPhase(context,value.optString("id"),phase,value.optInt("completedFocusCount",0)+1)}else{FocusForegroundService.stop(context);if(state=="RUNNING"&&(phase=="SHORT_BREAK"||phase=="LONG_BREAK"))notifyFocusPhase(context,value.optString("id"),phase,value.optInt("completedFocusCount",0))}}
}
class SyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork():Result=try{Repository.get(applicationContext).sync();Result.success()}catch(e:HttpFailure){if(e.status==401)Result.success() else Result.retry()}catch(_:Exception){Result.retry()}}
