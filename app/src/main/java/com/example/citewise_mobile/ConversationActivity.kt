package com.example.citewise_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ChatPreview
import com.example.citewise_mobile.adapters.MessageItem
import com.example.citewise_mobile.adapters.MessagesAdapter
import com.example.citewise_mobile.api.AppMessagingService
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.MessagesRepository
import com.example.citewise_mobile.data.NetResult
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class ConversationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_CHAT_TITLE = "chatTitle"
        const val EXTRA_PEER_UID = "peerUid"
        private const val TAG = "ConversationActivity"
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { MessagesRepository(RetrofitInstance.messagesApi) }

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessagesAdapter

    private var pollJob: Job? = null
    private var lastSeen: Long = 0L
    private var myUid: String? = null
    private var fullChatList: List<ChatPreview> = emptyList()
    private var messageReceiver: BroadcastReceiver? = null
    private var currentPeerUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        myUid = auth.currentUser?.uid
        if (myUid == null) {
            Log.e(TAG, "User not authenticated")
            finish()
            return
        }

        val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: "Chat"
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val peerUid = intent.getStringExtra(EXTRA_PEER_UID) ?: return
        currentPeerUid = peerUid

        Log.d(TAG, " My UID: $myUid, Peer UID: $peerUid, Chat ID: $chatId")

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvChatTitle).text = chatTitle

        // Extract initials from chat title
        val initials = chatTitle.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
        findViewById<TextView>(R.id.tvInitials).text = initials

        recycler = findViewById(R.id.recyclerMessages)
        adapter = MessagesAdapter()
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        // Load initial conversation
        loadInitial(peerUid)

        // Send
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val et = findViewById<TextInputEditText>(R.id.etMessage)
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                sendMessage(peerUid, text)
                et.setText("")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, " onStart: registering broadcast receiver for peer=$currentPeerUid")
        setupMessageReceiver(currentPeerUid ?: "")
        startPolling()
    }

    override fun onStop() {
        Log.d(TAG, " onStop: stopping polling and unregistering receiver")
        pollJob?.cancel()
        messageReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
                Log.d(TAG, " ✓ Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, " Error unregistering receiver", e)
            }
        }
        messageReceiver = null
        super.onStop()
    }

    // ---- actions ----

    private fun loadInitial(peerUid: String) {
        val uid = myUid ?: return
        lifecycleScope.launch {
            when (val res = repo.withPeer(peerUid, limit = 100, before = null)) {
                is NetResult.Ok -> {
                    val messages = res.data
                        .sortedBy { it.createdAt ?: 0L }
                        .map { d ->
                            val isMine = d.fromUid == uid
                            Log.d(TAG, " Initial: id=${d.id}, fromUid=${d.fromUid}, toUid=${d.toUid}, myUid=$uid, isMine=$isMine")
                            com.example.citewise_mobile.adapters.Message(
                                id = d.id ?: "${d.fromUid}_${d.toUid}_${d.createdAt ?: 0}",
                                text = d.body ?: "",
                                isMe = isMine,
                                timestamp = d.createdAt ?: 0L
                            )
                        }
                    val withDividers = MessagesAdapter.insertDateDividers(messages)
                    adapter.submitList(withDividers)
                    lastSeen = messages.maxOfOrNull { it.timestamp } ?: 0L
                    scrollToBottom()
                }
                is NetResult.Err -> {
                    Log.e(TAG, "Failed to load initial messages: ${res.message}")
                }
            }
        }
    }

    private fun sendMessage(peerUid: String, text: String) {
        val uid = myUid ?: return
        lifecycleScope.launch {
            // Optimistic append
            val tempId = "local-${System.nanoTime()}"
            val optimistic = com.example.citewise_mobile.adapters.Message(
                id = tempId,
                text = text,
                isMe = true,
                timestamp = System.currentTimeMillis()
            )
            Log.d(TAG, " Sending optimistic message: $tempId")

            val currentMessages = adapter.currentList
                .filterIsInstance<MessageItem.MessageData>()
                .map { it.message }
                .toMutableList()
                .apply { add(optimistic) }
            val withDividers = MessagesAdapter.insertDateDividers(currentMessages)
            adapter.submitList(withDividers)
            scrollToBottom()

            when (val r = repo.send(toUid = peerUid, body = text, clientId = null)) {
                is NetResult.Ok -> {
                    val responseData = r.data
                    val d = responseData

                    val isMine = (d.fromUid == uid)
                    Log.d(TAG, " Sent confirmed: id=${d.id}, fromUid=${d.fromUid}, toUid=${d.toUid}, myUid=$uid, isMine=$isMine, body=${d.body}")

                    val real = com.example.citewise_mobile.adapters.Message(
                        id = d.id ?: tempId,
                        text = d.body ?: text,
                        isMe = isMine,
                        timestamp = d.createdAt ?: optimistic.timestamp
                    )

                    val replaced = currentMessages
                        .map { if (it.id == tempId) real else it }
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    val replacedWithDividers = MessagesAdapter.insertDateDividers(replaced)
                    adapter.submitList(replacedWithDividers)
                    lastSeen = max(lastSeen, real.timestamp)
                    scrollToBottom()

                    loadInitial(peerUid)
                }
                is NetResult.Err -> {
                    Log.e(TAG, "Failed to send message: ${r.message}")
                    val reverted = currentMessages.filterNot { it.id == tempId }
                    val revertedWithDividers = MessagesAdapter.insertDateDividers(reverted)
                    adapter.submitList(revertedWithDividers)
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(4000)
                val since = lastSeen
                when (val res = repo.since(since)) {
                    is NetResult.Ok -> {
                        val uid = myUid ?: return@launch
                        val newMessages = res.data
                            .filter { (it.createdAt ?: 0L) > since }
                            .map { d ->
                                val isMine = d.fromUid == uid
                                Log.d(TAG, " Polled: id=${d.id}, fromUid=${d.fromUid}, toUid=${d.toUid}, myUid=$uid, isMine=$isMine")
                                com.example.citewise_mobile.adapters.Message(
                                    id = d.id ?: "${d.fromUid}_${d.toUid}_${d.createdAt ?: 0}",
                                    text = d.body ?: "",
                                    isMe = isMine,
                                    timestamp = d.createdAt ?: 0L
                                )
                            }
                            .sortedBy { it.timestamp }

                        if (newMessages.isNotEmpty()) {
                            val currentMessages = adapter.currentList
                                .filterIsInstance<MessageItem.MessageData>()
                                .map { it.message }
                            val merged = (currentMessages + newMessages)
                                .distinctBy { it.id }
                                .sortedBy { it.timestamp }
                            val mergedWithDividers = MessagesAdapter.insertDateDividers(merged)
                            adapter.submitList(mergedWithDividers) {
                                scrollToBottom()
                            }
                            lastSeen = newMessages.maxOf { it.timestamp }
                        }
                    }
                    is NetResult.Err -> {
                        Log.w(TAG, "Polling error: ${res.message}")
                    }
                }
            }
        }
    }

    private fun scrollToBottom() {
        recycler.post {
            if (adapter.itemCount > 0) {
                recycler.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun setupMessageReceiver(peerUid: String) {
        messageReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }

        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                val receivedPeerUid = intent?.getStringExtra(AppMessagingService.EXTRA_PEER_UID)
                Log.d(TAG, " ===== BROADCAST RECEIVED =====")
                Log.d(TAG, " Received peerUid: $receivedPeerUid")
                Log.d(TAG, " Current peerUid: $peerUid")
                Log.d(TAG, " My UID: $myUid")

                // Refresh if message is from current conversation peer
                if (receivedPeerUid == peerUid) {
                    Log.d(TAG, " ✓ Message from current chat peer, refreshing immediately")
                    loadInitial(peerUid)
                } else {
                    Log.d(TAG, " ✗ Message from different chat, ignoring")
                }
            }
        }

        val filter = IntentFilter(AppMessagingService.ACTION_NEW_MESSAGE)
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver!!, filter)
        Log.d(TAG, " ✓ Broadcast receiver registered for action: ${AppMessagingService.ACTION_NEW_MESSAGE}")
    }
}


