package dz.alnourelite.paymonitor

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.*
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Manual, permission-free test client. The account is chosen by the server-side device token. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var message: EditText
    private val paymentPattern = Regex(
        "^Cher\\s+client,\\s+votre\\s+Compte\\s+est\\s+Crédité\\s+de\\s+([0-9]+(?:[.,][0-9]{2}))\\s+DA\\.?$",
        RegexOption.IGNORE_CASE
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        fun label(t: String) = TextView(this).apply {
            text = t
            textSize = 16f
            setPadding(0, 12, 0, 8)
            page.addView(this)
        }
        label("Al Nour Pay Monitor — اختبار يدوي آمن")
        label("هذه النسخة لا تقرأ SMS تلقائياً ولا تطلب إذن الوصول إلى الرسائل. لا تستخدم رموز التحويل أو OTP. الدفعات المضافة يدوياً تحتاج مراجعة المحاسبة.")
        val host = EditText(this).apply {
            hint = "https://213dev.site/pay-monitor"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(SecureConfig.host(this@MainActivity) ?: "https://213dev.site/pay-monitor")
            page.addView(this)
        }
        val token = EditText(this).apply {
            hint = "مفتاح الهاتف: 64 خانة"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            page.addView(this)
        }
        page.addView(Button(this).apply {
            text = "حفظ إعدادات الاتصال"
            setOnClickListener {
                val h = host.text.toString().trim().trimEnd('/')
                val t = token.text.toString().trim()
                if (!h.startsWith("https://") || !Regex("[0-9a-fA-F]{64}").matches(t)) {
                    Toast.makeText(this@MainActivity, "تحقق من HTTPS والمفتاح", Toast.LENGTH_LONG).show()
                } else {
                    SecureConfig.save(this@MainActivity, h, t)
                    token.setText("")
                    SyncWorker.periodic(this@MainActivity)
                    SyncWorker.enqueue(this@MainActivity)
                    update()
                }
            }
        })
        label("ألصق إشعار وصول المبلغ فقط (وليس رمز التحويل):")
        message = EditText(this).apply {
            hint = "Cher client, votre Compte est Crédité de 7000.00 DA."
            minLines = 3
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            page.addView(this)
        }
        page.addView(Button(this).apply {
            text = "مراجعة الدفعة وإرسالها"
            setOnClickListener { reviewPayment() }
        })
        page.addView(Button(this).apply {
            text = "إعادة محاولة إرسال العمليات المعلقة"
            setOnClickListener { SyncWorker.enqueue(this@MainActivity); update() }
        })
        status = label("الحالة")
        setContentView(ScrollView(this).apply { addView(page) })
        SyncWorker.periodic(this)
        update()
    }
    private fun reviewPayment() {
        if (SecureConfig.host(this) == null || SecureConfig.token(this) == null) {
            Toast.makeText(this, "احفظ إعدادات الاتصال أولاً", Toast.LENGTH_LONG).show()
            return
        }
        val raw = message.text.toString().trim()
        val match = paymentPattern.matchEntire(raw)
        if (match == null) {
            Toast.makeText(this, "رسالة غير مطابقة لإشعار الإيداع؛ لن يتم إرسال أي بيانات", Toast.LENGTH_LONG).show()
            return
        }
        val amount = match.groupValues[1].replace(',', '.')
        val pieces = amount.split('.')
        val whole = pieces[0].toLongOrNull()
        val cents = pieces.getOrNull(1)?.toLongOrNull()
        if (whole == null || cents == null || whole !in 0..1_000_000_000L || cents !in 0..99L) return
        val amountCentimes = whole * 100 + cents
        if (amountCentimes <= 0) return
        AlertDialog.Builder(this)
            .setTitle("تأكيد إضافة إشعار يدوي")
            .setMessage("المبلغ: $amount DA\n\nهذه عملية يدوية وغير مثبتة بهوية الدافع. هل تريد إرسالها إلى سجل المراجعة؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تسجيل للاختبار") { _, _ ->
                val eventId = UUID.randomUUID().toString()
                // Manual events have distinct event identifiers and are never assumed to be bank-verified.
                val timestamp = OffsetDateTime.now(ZoneId.of("Africa/Algiers"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
                PaymentDb(this).add(eventId, eventId.replace("-", ""), amountCentimes, timestamp)
                message.setText("")
                SyncWorker.enqueue(this)
                update()
                Toast.makeText(this, "تمت إضافة العملية إلى قائمة الإرسال", Toast.LENGTH_LONG).show()
            }.show()
    }
    private fun update() {
        status.text = "الوضع: يدوي بدون صلاحية SMS\nالرابط: ${SecureConfig.host(this) ?: "غير مضبوط"}\nعمليات معلقة: ${PaymentDb(this).count()}"
    }
    override fun onResume() { super.onResume(); if (::status.isInitialized) update() }
}
