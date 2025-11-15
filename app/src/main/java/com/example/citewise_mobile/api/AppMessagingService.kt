package com.example.citewise_mobile.api

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.citewise_mobile.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AppMessagingService"
        private const val CHANNEL_MESSAGES = "messages"
        const val ACTION_NEW_MESSAGE = "com.example.citewise_mobile.NEW_MESSAGE"
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_PEER_UID = "peerUid"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        Log.d(TAG, "[FCM] onNewToken called with token: ${token.take(20)}...")

        if (uid.isNullOrEmpty()) {
            Log.w(TAG, "[FCM] No user logged in; skipping token save")
            return
        }

        Log.d(TAG, "[FCM] Saving token for user=$uid to Firestore")

        try {
            val tokenRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)

            Log.d(TAG, "[FCM] Firestore path: users/$uid/fcmTokens/$token")

            tokenRef.set(mapOf(
                "createdAt" to System.currentTimeMillis(),
                "platform" to "android",
                "lastRefreshed" to System.currentTimeMillis()
            ))
                .addOnSuccessListener {
                    Log.d(TAG, "[FCM] ✓ Token saved successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "[FCM] ✗ Failed to save token", e)
                }
        } catch (se: SecurityException) {
            Log.e(TAG, "[FCM] SecurityException while saving token", se)
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] Unexpected error while saving token", e)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)


        // Prefer FCM "notification" payload, then fall back to "data"
        val title = message.notification?.title ?: message.data["title"] ?: "Notification"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""


        val chatId    = message.data["chatId"]
        val chatTitle = message.data["chatTitle"] ?: title
        val peerUid   = message.data["peerUid"]
        val fromUid   = message.data["fromUid"]

        val effectivePeerUid = peerUid ?: fromUid
        if (!effectivePeerUid.isNullOrEmpty()) {
            val broadcastIntent = Intent(ACTION_NEW_MESSAGE).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_PEER_UID, effectivePeerUid)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        }

        // Only show if we have permission on API 33+
        val canPost = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        if (!canPost) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping foreground notification")
            return
        }

        Log.d(TAG, "Notification permission granted, showing notification")

        ensureMessageChannel()

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (!chatId.isNullOrEmpty()) {
            val intent = Intent(this, Class.forName("com.example.citewise_mobile.ConversationActivity")).apply {
                putExtra("chatId", chatId)
                putExtra("chatTitle", chatTitle)
                putExtra("peerUid", effectivePeerUid)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.setContentIntent(pendingIntent)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())

        Log.d(TAG, "Notification displayed successfully")
    }

    private fun ensureMessageChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_messages_name)
            val descriptionText = getString(R.string.channel_messages_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_MESSAGES, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

/*
 * REFERENCES
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 */