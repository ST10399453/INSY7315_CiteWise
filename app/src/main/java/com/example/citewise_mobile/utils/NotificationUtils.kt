package com.example.citewise_mobile.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.citewise_mobile.R

object NotificationUtils {

    /**
     * Safely shows a notification, honoring runtime permission (Android 13+),
     * global notification enablement, and potential SecurityExceptions.
     *
     * @return true if a notification was posted, false otherwise.
     */
    fun show(
        context: Context,
        title: String,
        message: String,
        channelId: String,
        notificationId: Int,
        contentIntent: android.content.Intent? = null
    ): Boolean {
        // Android 13+ requires POST_NOTIFICATIONS runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        // Respect the user's global notification toggle for the app
        val nmCompat = NotificationManagerCompat.from(context)
        if (!nmCompat.areNotificationsEnabled()) return false

        // Create/ensure notification channel (Android 8.0+)
        ensureChannel(context, channelId)

        val pendingIntent: PendingIntent? = contentIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for heads-up display
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Message category for chat
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibrate, lights
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        return try {
            nmCompat.notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            // Can still happen on some devices/OEMs if permission/state changes mid-call
            false
        }
    }

    private fun ensureChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.channel_messages_name),
                    NotificationManager.IMPORTANCE_HIGH // High importance for heads-up notifications
                ).apply {
                    description = context.getString(R.string.channel_messages_desc)
                    enableVibration(true) // Enable vibration
                    enableLights(true) // Enable LED indicator
                    setShowBadge(true) // Show badge on app icon
                }
                nm.createNotificationChannel(channel)
            } catch (_: SecurityException) {
                // Gracefully ignore; channel creation isn't allowed in some restricted contexts
            }
        }
    }
}
