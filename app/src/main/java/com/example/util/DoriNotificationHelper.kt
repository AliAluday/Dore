package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.BookingEntity
import com.example.data.model.LiveTurnAlert
import com.example.data.model.TicketStage

object DoriNotificationHelper {

    private const val CHANNEL_ID = "dori_queue_alerts"
    private const val CHANNEL_NAME = "تنبيهات الطابور والتذاكر"
    private const val CHANNEL_DESC = "إشعارات فورية لتأكيد الحجز واقتراب الدور وحلوله"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showBookingCreatedNotification(context: Context, booking: BookingEntity) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val etaText = if (booking.estimatedMinutes <= 0) "دورك الآن" else "الانتظار المتوقع ${booking.estimatedMinutes} دقيقة"
        val title = "🎫 تم إصدار تكت ${booking.ticketCode}"
        val body = "${booking.venueName} • ${booking.selectedOption} ($etaText)"

        postNotification(
            context = context,
            notificationId = booking.id.toInt() + 1000,
            title = title,
            body = body
        )
    }

    fun showTurnAlertNotification(context: Context, alert: LiveTurnAlert) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val prefix = if (alert.stage == TicketStage.YOUR_TURN) "🔔 حان دورك الآن!" else "⏳ اقترب دورك!"
        postNotification(
            context = context,
            notificationId = alert.bookingId.toInt() + 2000,
            title = "$prefix (${alert.ticketCode})",
            body = alert.message
        )
    }

    private fun postNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
