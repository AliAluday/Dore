package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.local.BookingEntity
import com.example.data.model.TicketStage
import java.text.NumberFormat
import java.util.Locale

object IntentHelpers {

    fun formatDinar(amount: Int): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return "${formatter.format(amount)} د.ع"
    }

    fun dialPhone(context: Context, phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        safeStartActivity(context, intent, "تعذر فتح تطبيق الاتصال")
    }

    fun openMapLocation(context: Context, lat: Double, lng: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        safeStartActivity(context, intent, "تعذر فتح الخريطة")
    }

    fun openMessaging(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace("+", "").replace(" ", "")
        val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
        }
        val started = runCatching {
            context.startActivity(whatsappIntent)
            true
        }.getOrDefault(false)

        if (!started) {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", message)
            }
            safeStartActivity(context, smsIntent, "لا يوجد تطبيق رسائل متاح")
        }
    }

    fun shareTicketViaWhatsApp(context: Context, booking: BookingEntity) {
        val stageLabel = TicketStage.entries.find { it.name == booking.stage }?.label ?: booking.stage
        val text = buildString {
            appendLine("🎫 تذكرة دوري الذكية")
            appendLine("━━━━━━━━━━━━━━━")
            appendLine("المكان: ${booking.venueName} (${booking.venueCity})")
            appendLine("رقم التذكرة: ${booking.ticketCode}")
            appendLine("الخدمة: ${booking.selectedOption}")
            appendLine("الاسم: ${booking.customerName}")
            appendLine("الحالة: $stageLabel")
            appendLine("الوقت المتوقع: ${if (booking.estimatedMinutes == 0) "الآن" else "${booking.estimatedMinutes} دقيقة"}")
            appendLine("السعر: ${formatDinar(booking.priceDinar)}")
        }

        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val launchedWhatsApp = runCatching {
            context.startActivity(whatsappIntent)
            true
        }.getOrDefault(false)

        if (!launchedWhatsApp) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            safeStartActivity(
                context,
                Intent.createChooser(shareIntent, "مشاركة التذكرة عبر"),
                "تعذر فتح نافذة المشاركة"
            )
        }
    }

    private fun safeStartActivity(context: Context, intent: Intent, errorMsg: String) {
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
