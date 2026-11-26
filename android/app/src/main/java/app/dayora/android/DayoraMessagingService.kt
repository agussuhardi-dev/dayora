package app.dayora.android

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class DayoraMessagingService : FirebaseMessagingService() {
 private fun modelInfo()="manufacturer=${android.os.Build.MANUFACTURER}, model=${android.os.Build.MODEL}, android=${android.os.Build.VERSION.RELEASE}, sdk=${android.os.Build.VERSION.SDK_INT}, app=${BuildConfig.VERSION_NAME}"
 override fun onCreate(){super.onCreate();android.util.Log.i("DayoraPush","FCM client ready: ${modelInfo()}")}
 override fun onNewToken(token:String){
  android.util.Log.i("DayoraPush","FCM token refreshed: ${modelInfo()}")
  Repository.get(applicationContext).cachePushToken(token)
  PushSyncWorker.enqueue(applicationContext, expedited=false)
 }
 override fun onMessageReceived(message:RemoteMessage){
  val type=message.data["type"]
  android.util.Log.i("DayoraPush","FCM received: type=$type, priority=${message.priority}, ${modelInfo()}")
  val high=type=="focus_invalidated" || type=="pomodoro_phase_changed" || message.priority==RemoteMessage.PRIORITY_HIGH
  if(type!="focus_invalidated" && type!="pomodoro_phase_changed" && type!="sync_invalidated")return
  try{if(high) runBlocking { withTimeoutOrNull(8_000) { Repository.get(applicationContext).refreshFocus() } }}
  catch(e:Exception){android.util.Log.w("DayoraPush","Immediate focus refresh failed",e)}
  finally{PushSyncWorker.enqueue(applicationContext,high)}
 }
}

class PushSyncWorker(context:android.content.Context,params:androidx.work.WorkerParameters):androidx.work.CoroutineWorker(context,params){
 override suspend fun doWork():androidx.work.ListenableWorker.Result=try{
  Repository.get(applicationContext).sync()
  androidx.work.ListenableWorker.Result.success()
 }catch(e:HttpFailure){if(e.status==401) androidx.work.ListenableWorker.Result.success() else androidx.work.ListenableWorker.Result.retry()}catch(_:Exception){androidx.work.ListenableWorker.Result.retry()}
 companion object {
  fun enqueue(context:android.content.Context,expedited:Boolean){
   val builder=OneTimeWorkRequestBuilder<PushSyncWorker>()
   if(expedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
   WorkManager.getInstance(context).enqueueUniqueWork("dayora-push-sync",ExistingWorkPolicy.APPEND_OR_REPLACE,builder.build())
  }
 }
}
