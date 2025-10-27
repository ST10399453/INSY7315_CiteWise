package com.example.citewise_mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.data.Message
import com.example.citewise_mobile.data.MessagesAdapter
import com.google.android.material.appbar.MaterialToolbar

class ConversationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_CHAT_TITLE = "chatTitle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        // Toolbar title from ChatsActivity
        val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: "Chat"
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = chatTitle
        toolbar.setNavigationOnClickListener { finish() }

        // Recycler + adapter
        val recycler = findViewById<RecyclerView>(R.id.recyclerMessages)
        val adapter = MessagesAdapter()
        recycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // start from bottom like chat apps
        }
        recycler.adapter = adapter

        // Demo data so you can SEE something immediately
        val demo = listOf(
            Message("1", "Hey, are you coming?", isMine = false, timestamp = System.currentTimeMillis() - 60_000),
            Message(
                "2",
                "Yep, on my way 🚗",
                isMine = true,
                timestamp = System.currentTimeMillis() - 45_000
            ),
            Message("3", "Great, see you soon!", isMine = false, timestamp = System.currentTimeMillis() - 30_000)
        )
        adapter.submitList(demo)

        // TODO: wire send button to append to adapter + scroll
        // findViewById<MaterialButton>(R.id.btnSend).setOnClickListener { ... }
    }
}
