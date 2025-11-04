package com.example.citewise_mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.Message
import com.example.citewise_mobile.data.MessagesAdapter
import com.example.citewise_mobile.data.MessagesRepository
import com.example.citewise_mobile.data.NetResult
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
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
        const val EXTRA_PEER_UID = "peerUid" // add this when launching
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { MessagesRepository(RetrofitInstance.messagesApi) }

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessagesAdapter

    private var pollJob: Job? = null
    private var lastSeen: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: "Chat"
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val peerUid = intent.getStringExtra(EXTRA_PEER_UID) ?: return

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = chatTitle
        toolbar.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerMessages)
        adapter = MessagesAdapter()
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        // Load initial conversation (with peer API)
        loadInitial(peerUid)

        // Send
        findViewById<MaterialButton>(R.id.btnSend).setOnClickListener {
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
        startPolling()
    }

    override fun onStop() {
        pollJob?.cancel()
        super.onStop()
    }

    // ---- actions ----

    private fun loadInitial(peerUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            when (val res = repo.withPeer(peerUid, limit = 100, before = null)) {
                is NetResult.Ok -> {
                    val ui = res.data
                        .sortedBy { it.createdAt ?: 0L }
                        .map { d ->
                            Message(
                                id = d.id ?: "${d.fromUid}_${d.toUid}_${d.createdAt ?: 0}",
                                text = d.body,
                                isMine = d.fromUid == myUid,
                                timestamp = d.createdAt ?: 0L
                            )
                        }
                    adapter.submitList(ui)
                    lastSeen = ui.maxOfOrNull { it.timestamp } ?: 0L
                    scrollToBottom()
                }
                is NetResult.Err -> {
                    // Optional: toast/snackbar with res.message
                }
            }
        }
    }

    private fun sendMessage(peerUid: String, text: String) {
        val myUid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            // Optimistic append
            val tempId = "local-${System.nanoTime()}"
            val optimistic = Message(
                id = tempId,
                text = text,
                isMine = true,
                timestamp = System.currentTimeMillis()
            )
            val current = adapter.currentList.toMutableList().apply { add(optimistic) }
            adapter.submitList(current)
            scrollToBottom()

            when (val r = repo.send(toUid = peerUid, body = text, clientId = null)) {
                is NetResult.Ok -> {
                    // Replace optimistic by server message
                    val d = r.data
                    val real = Message(
                        id = d.id ?: tempId,
                        text = d.body,
                        isMine = d.fromUid == myUid,
                        timestamp = d.createdAt ?: optimistic.timestamp
                    )
                    val replaced = adapter.currentList
                        .map { if (it.id == tempId) real else it }
                        .sortedBy { it.timestamp }
                    adapter.submitList(replaced)
                    lastSeen = max(lastSeen, real.timestamp)
                    scrollToBottom()
                }
                is NetResult.Err -> {
                    // Remove the optimistic item (or mark as failed)
                    val reverted = adapter.currentList.filterNot { it.id == tempId }
                    adapter.submitList(reverted)
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (true) {
                delay(4000) // light poll; complements push if you wire FCM
                val since = lastSeen
                when (val res = repo.since(since)) {
                    is NetResult.Ok -> {
                        val myUid = auth.currentUser?.uid ?: return@launch
                        val newMessages = res.data
                            .filter { (it.createdAt ?: 0L) > since }
                            .map { d ->
                                Message(
                                    id = d.id ?: "${d.fromUid}_${d.toUid}_${d.createdAt ?: 0}",
                                    text = d.body,
                                    isMine = d.fromUid == myUid,
                                    timestamp = d.createdAt ?: 0L
                                )
                            }
                            .sortedBy { it.timestamp }

                        if (newMessages.isNotEmpty()) {
                            val merged = (adapter.currentList + newMessages).distinctBy { it.id }
                            adapter.submitList(merged)
                            lastSeen = newMessages.maxOf { it.timestamp }
                            scrollToBottom()
                        }
                    }
                    is NetResult.Err -> {
                        // swallow transient errors; next loop will retry
                    }
                }
            }
        }
    }

    private fun scrollToBottom() {
        recycler.scrollToPosition(max(0, adapter.itemCount - 1))
    }
}
