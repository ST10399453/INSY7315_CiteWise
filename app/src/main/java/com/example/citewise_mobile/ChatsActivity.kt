package com.example.citewise_mobile

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ChatPreview
import com.example.citewise_mobile.adapters.ChatsAdapter

class ChatsActivity : AppCompatActivity() {
    private lateinit var adapter: ChatsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        val rv = findViewById<RecyclerView>(R.id.recyclerChats)
        adapter = ChatsAdapter { chat ->
            // TODO: open conversation screen
        }
        rv.adapter = adapter
        rv.addItemDecoration(SpacesItemDecoration(8))

        // Demo data
        adapter.submitList(
            listOf(
                ChatPreview("1", "Alice Johnson", "See you soon!", System.currentTimeMillis() - 600000),
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
