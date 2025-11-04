package com.example.citewise_mobile.api

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.citewise_mobile.ConversationActivity
import com.example.citewise_mobile.offline.MessagesSyncWorker
import com.example.citewise_mobile.utils.NotificationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.w(TAG, "onNewToken: No user logged in; skipping token save")
            return
        }

        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(mapOf("createdAt" to System.currentTimeMillis()))
                .addOnSuccessListener { Log.d(TAG, "Token saved for user=$uid") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to save token", e) }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while saving token", se)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Prefer FCM "notification" payload, then fall back to "data"
        val title = message.notification?.title ?: message.data["title"] ?: "Notification"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""

        // Optional deep link data from server (recommended for chat)
        val chatId    = message.data["chatId"]
        val chatTitle = message.data["chatTitle"] ?: title
        val peerUid   = message.data["peerUid"]   // if your server sends it

        // Only show if we have permission on API 33+
        val canPost = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        if (!canPost) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping foreground notification")
            return
        }

        // Kick a quick background sync so the UI is fresh when the user opens the app
        try {
            MessagesSyncWorker.oneShot(applicationContext)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to enqueue MessagesSyncWorker: ${t.message}")
        }

        // Build an intent to open the conversation if chat info is present
        val contentIntent = if (!chatId.isNullOrBlank()) {
            Intent(this, ConversationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ConversationActivity.EXTRA_CHAT_ID, chatId)
                putExtra(ConversationActivity.EXTRA_CHAT_TITLE, chatTitle)
                if (!peerUid.isNullOrBlank()) {
                    putExtra(ConversationActivity.EXTRA_PEER_UID, peerUid)
                }
            }
        } else {
            null
        }

        try {
            NotificationUtils.show(
                context = this,
                title = title,
                message = body,
                channelId = CHANNEL_MESSAGES,
                notificationId = (chatId ?: title).hashCode(),
                contentIntent = contentIntent
            )
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while showing notification", se)
        }
    }

    companion object {
        private const val TAG = "AppFMS"
        const val CHANNEL_MESSAGES = "messages"
    }
}
