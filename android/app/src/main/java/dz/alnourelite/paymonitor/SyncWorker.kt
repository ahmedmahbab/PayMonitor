package dz.alnourelite.paymonitor

import android.content.Context
import androidx.work.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class SyncWorker(ctx:Context,params:WorkerParameters):Worker(ctx,params){
 override fun doWork():Result{
  val base=SecureConfig.host(applicationContext)?:return Result.failure()
  val token=SecureConfig.token(applicationContext)?:return Result.failure()
  val db=PaymentDb(applicationContext)
  for(p in db.pending()){
   try{
    val url=URL("$base/api/ingest.php")
    if(url.protocol!="https")return Result.failure()
    val conn=url.openConnection() as HttpURLConnection
    try{
     conn.requestMethod="POST";conn.connectTimeout=12000;conn.readTimeout=12000;conn.doOutput=true
     conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
     conn.setRequestProperty("Authorization","Bearer $token")
     val json=JSONObject().put("event_id",p.eventId).put("sms_fingerprint",p.fingerprint)
      .put("amount_centimes",p.amount).put("received_at",p.receivedAt).put("message_type","CREDIT")
     conn.outputStream.use{it.write(json.toString().toByteArray(Charsets.UTF_8))}
     val status=conn.responseCode
     if(status==200||status==201)db.remove(p.id)
     else if(status==401||status==403||status==422)return Result.failure()
     else return Result.retry()
    }finally{conn.disconnect()}
   }catch(e:Exception){return Result.retry()}
  }
  return Result.success()
 }
 companion object{
  fun enqueue(ctx:Context){val req=OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(ctx).enqueueUniqueWork("sms-sync",ExistingWorkPolicy.KEEP,req)}
  fun periodic(ctx:Context){val req=PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sms-sync-periodic",ExistingPeriodicWorkPolicy.KEEP,req)}
 }
}
