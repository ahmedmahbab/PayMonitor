package dz.alnourelite.paymonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.*
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class SmsReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
  val result=goAsync()
  Thread {try {
   val messages=Telephony.Sms.Intents.getMessagesFromIntent(intent)
   if(messages.isEmpty())return@Thread
   // Multipart messages are received as one broadcast. Do not treat each segment as a separate payment.
   val sender=messages.first().displayOriginatingAddress?.trim()?.lowercase()?.replace(" ","")?:return@Thread
   if(sender!="algposte")return@Thread
   val body=messages.joinToString(""){it.displayMessageBody?:""}.trim()
   // Exact whitelist: never queue transfer codes, OTPs, debits or unknown wording.
   val match=Regex("^Cher\\s+client,\\s+votre\\s+Compte\\s+est\\s+Crédité\\s+de\\s+([0-9]+(?:[.,][0-9]{2}))\\s+DA\\.?$",RegexOption.IGNORE_CASE).matchEntire(body)?:return@Thread
   val parts=match.groupValues[1].replace(',','.').split('.')
   val whole=parts[0].toLongOrNull()?:return@Thread
   if(whole>1_000_000_000L)return@Thread
   val cents=whole*100+(parts.getOrNull(1)?.toLongOrNull()?:0L)
   if(cents<=0||cents>100000000000L)return@Thread
   val time=messages.minOf{it.timestampMillis}
   val fingerprint=MessageDigest.getInstance("SHA-256").digest("$sender|$time|$body".toByteArray()).joinToString(""){ "%02x".format(it) }
   val eventId=UUID.nameUUIDFromBytes(fingerprint.toByteArray()).toString()
   val date=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.of("Africa/Algiers")).format(Instant.ofEpochMilli(time))
   PaymentDb(context).add(eventId,fingerprint,cents,date)
   SyncWorker.enqueue(context)
  } catch(e:Exception){android.util.Log.e("ANE-SMS","SMS processing failed",e)}finally{result.finish()}}.start()
 }
}
