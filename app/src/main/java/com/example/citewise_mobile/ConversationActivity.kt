package com.example.citewise_mobile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.Message
import com.example.citewise_mobile.adapters.MessagesAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class ConversationActivity : AppCompatActivity() {
    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        val rv = findViewById<RecyclerView>(R.id.recyclerMessages)
        adapter = MessagesAdapter()
        rv.adapter = adapter

        // Demo messages
        val now = System.currentTimeMillis()
        adapter.submitList(
            listOf(
                Message("1", "Hey!", now - 120_000, false),
                Message("2", "Hi, how are you?", now - 110_000, true),
                Message("3", "All good, you?", now - 100_000, false),
                Message("4", "Doing great!", now - 90_000, true)
            )
        )

        val btnSend = findViewById<MaterialButton>(R.id.btnSend)
        val et = findViewById<TextInputEditText>(R.id.etMessage)
        btnSend.setOnClickListener {
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                val new = Message(UUID.randomUUID().toString(), text, System.currentTimeMillis(), true)
                val current = adapter.currentList.toMutableList()
                current.add(new)
                adapter.submitList(current)
                rv.scrollToPosition(current.lastIndex)
                et.setText("")
            }
        }
    }
}
