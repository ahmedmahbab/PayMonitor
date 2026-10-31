package dz.alnourelite.paymonitor

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.*
import android.view.ViewGroup

class MainActivity:Activity(){
 private lateinit var status:TextView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(35,50,35,35)}
  fun text(s:String)=TextView(this).apply{this.text=s;textSize=17f;setPadding(0,12,0,12);page.addView(this)}
  text("Al Nour Pay Monitor — SMS Gateway")
  text("كل هاتف يحتاج إلى مفتاح مستقل. التطبيق يلتقط إشعارات المبالغ الواردة فقط، ولا يقرأ الرسائل القديمة.")
  val host=EditText(this).apply{hint="https://213dev.site/pay-monitor";setSingleLine(true);setText(SecureConfig.host(this@MainActivity)?:"");page.addView(this)}
  val token=EditText(this).apply{hint="مفتاح الجهاز المكوّن من 64 خانة";setSingleLine(true);inputType=129;page.addView(this)}
  val save=Button(this).apply{text="حفظ إعدادات الجهاز";setOnClickListener{
   val h=host.text.toString().trim().trimEnd('/');val t=token.text.toString().trim()
   if(!h.startsWith("https://")||!Regex("[0-9a-fA-F]{64}").matches(t)){Toast.makeText(this@MainActivity,"تحقق من HTTPS والمفتاح",Toast.LENGTH_LONG).show();return@setOnClickListener}
   SecureConfig.save(this@MainActivity,h,t);token.setText("");SyncWorker.periodic(this@MainActivity);SyncWorker.enqueue(this@MainActivity);update()
  }};page.addView(save)
  val perm=Button(this).apply{text="منح إذن استقبال SMS";setOnClickListener{
   if(Build.VERSION.SDK_INT>=23)requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS),101)
  }};page.addView(perm)
  val sync=Button(this).apply{text="إعادة محاولة مزامنة الدفعات";setOnClickListener{SyncWorker.enqueue(this@MainActivity);update()}};page.addView(sync)
  status=textView();page.addView(status)
  setContentView(ScrollView(this).apply{addView(page)})
  SyncWorker.periodic(this);update()
 }
 private fun textView()=TextView(this).apply{setPadding(0,22,0,0);textSize=16f}
 private fun update(){val authorized=checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED
 status.text="إذن الرسائل: ${if(authorized)"مسموح" else "غير مسموح"}\nالرابط: ${SecureConfig.host(this)?:"غير مضبوط"}\nدفعات في انتظار الإرسال: ${PaymentDb(this).count()}"}
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);update()}
 override fun onResume(){super.onResume();if(::status.isInitialized)update()}
}
