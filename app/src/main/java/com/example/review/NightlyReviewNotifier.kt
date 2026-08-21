package com.example.review

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

interface NightlyReviewNotifier {
    fun notify(review: NightlyReview)
}

class AndroidNightlyReviewNotifier(private val context: Context) : NightlyReviewNotifier {
    override fun notify(review: NightlyReview) {
        if (!canNotify()) error("Falta el permiso de notificaciones")
        createChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NIGHTLY_REVIEW
            putExtra(MainActivity.EXTRA_REVIEW_DATE, review.date.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            review.date.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nightly_review_notification)
            .setContentTitle("Revisión de hoy")
            .setContentText(review.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(review.summary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Revisión nocturna de salud",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Resumen factual diario generado desde Health Connect"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "nightly_health_review"
        private const val NOTIFICATION_ID = 22_030
    }
}
