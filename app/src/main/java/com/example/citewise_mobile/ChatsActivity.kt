package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ChatPreview
import com.example.citewise_mobile.adapters.ChatsAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView

class ChatsActivity : BaseActivity() {

    private lateinit var recentAdapter: ChatsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_chats, baseContent, false)
        baseContent.addView(content)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_messages)

        content.findViewById<RecyclerView>(R.id.rvRecent).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            recentAdapter = ChatsAdapter { chat ->
                // use chat.chatId and chat.displayName
                val intent = Intent(this@ChatsActivity, ConversationActivity::class.java).apply {
                    putExtra(ConversationActivity.EXTRA_CHAT_ID, chat.chatId)
                    putExtra(ConversationActivity.EXTRA_CHAT_TITLE, chat.displayName)
                }
                startActivity(intent)
            }
            adapter = recentAdapter
            addItemDecoration(SpacesItemDecoration(8))
        }

        content.findViewById<RecyclerView>(R.id.rvPinned).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        // Demo data unchanged (constructor already matches chatId/displayName)
        recentAdapter.submitList(
            listOf(
                ChatPreview("1", "Alice Johnson", "See you soon!", System.currentTimeMillis() - 600_000),
                ChatPreview("2", "Bob King", "Thanks!", System.currentTimeMillis() - 86_400_000)
            )
        )
    }
}

/** Simple spacing decoration */
class SpacesItemDecoration(private val spaceDp: Int) : RecyclerView.ItemDecoration() {
    private fun Int.dp(view: View) = (this * view.resources.displayMetrics.density).toInt()
    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val s = spaceDp.dp(view)
        outRect.set(0, s, 0, 0)
        if (parent.getChildAdapterPosition(view) == 0) outRect.top = s
    }
}
