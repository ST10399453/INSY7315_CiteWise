package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ChatPreview
import com.example.citewise_mobile.adapters.ChatsAdapter
import com.example.citewise_mobile.adapters.ContactsAdapter
import com.example.citewise_mobile.api.MessageDto
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.MessagesRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.offline.CloudDataSources
import com.example.citewise_mobile.offline.UserEntity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsActivity : BaseActivity() {

    private lateinit var recentAdapter: ChatsAdapter
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { MessagesRepository(RetrofitInstance.messagesApi) }
    private val cloud by lazy { CloudDataSources() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_chats, baseContent, false)
        baseContent.addView(content)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_messages)

        // --- recent list ---
        content.findViewById<RecyclerView>(R.id.rvRecent).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            recentAdapter = ChatsAdapter { chat ->
                startConversation(chat.peerUid, chat.displayName)
            }
            adapter = recentAdapter
            addItemDecoration(SpacesItemDecoration(8))
        }

        // --- pinned list (horizontal) ---
        content.findViewById<RecyclerView>(R.id.rvPinned).apply {
            layoutManager = LinearLayoutManager(
                this@ChatsActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        }

        // --- sheet actions ---
        content.findViewById<ImageButton>(R.id.btnAddChat).setOnClickListener { showNewChatSheet() }
        content.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener { showSearchChatsSheet() }

        // Load real recent chats (grouped by peer)
        refreshRecent()
    }

    private fun refreshRecent() {
        val myUid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            when (val res = repo.since(0L)) { // pull all involving me; server-side delta endpoint
                is NetResult.Ok -> {
                    val grouped = groupByPeerAndLatest(res.data, myUid)
                    recentAdapter.submitList(grouped)
                }
                is NetResult.Err -> {
                    // Optional: show a toast/snackbar with res.message
                }
            }
        }
    }

    // ---- Bottom sheets ----

    private fun showNewChatSheet() {
        val dlg = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_new_chat, null)
        dlg.setContentView(view)

        // Close button
        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dlg.dismiss() }

        // Recycler + adapter
        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        val adapter = ContactsAdapter { user ->
            dlg.dismiss()
            startConversation(peerUid = user.uid, displayName = "${user.firstName} ${user.surname}".trim().ifEmpty { user.email })
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Load users (exclude me)
        lifecycleScope.launch {
            val myUid = auth.currentUser?.uid
            val users = withContext(Dispatchers.IO) { cloud.fetchUsers() }
                .filter { it.uid != myUid }
                .sortedBy { it.firstName.lowercase() }
            adapter.submitList(users)
        }

        // Search
        val et = view.findViewById<TextInputEditText>(R.id.etSearch)
        et?.addTextChangedListener(filterWatcher(adapter))

        dlg.show()
    }

    private fun showSearchChatsSheet() {
        val dlg = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_search_chat, null)
        dlg.setContentView(view)

        // Close button
        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dlg.dismiss() }

        val rv = view.findViewById<RecyclerView>(R.id.rvResults)
        val adapter = ContactsAdapter { user ->
            dlg.dismiss()
            startConversation(
                peerUid = user.uid,
                displayName = "${user.firstName} ${user.surname}".trim().ifEmpty { user.email })
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        lifecycleScope.launch {
            val myUid = auth.currentUser?.uid
            val users = withContext(Dispatchers.IO) { cloud.fetchUsers() }
                .filter { it.uid != myUid }
                .sortedBy { it.firstName.lowercase() }
            adapter.submitList(users)
        }

        val et = view.findViewById<TextInputEditText>(R.id.etSearch)
        et?.addTextChangedListener(filterWatcher(adapter))

        dlg.show()
    }

    private fun filterWatcher(adapter: ContactsAdapter) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            adapter.filter(s?.toString().orEmpty())
        }
    }

    private fun startConversation(peerUid: String, displayName: String) {
        val myUid = auth.currentUser?.uid ?: return
        val chatId = buildChatId(myUid, peerUid)
        val intent = Intent(this@ChatsActivity, ConversationActivity::class.java).apply {
            putExtra(ConversationActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ConversationActivity.EXTRA_CHAT_TITLE, displayName)
            putExtra(ConversationActivity.EXTRA_PEER_UID, peerUid)
        }
        startActivity(intent)
    }

    // ---- helpers ----

    private fun buildChatId(a: String, b: String): String =
        if (a <= b) "${a}_$b" else "${b}_$a"

    /**
     * Creates ChatPreview items by grouping messages per peer.
     * displayName currently uses the peerUid (replace with your user cache if available).
     */
    private suspend fun groupByPeerAndLatest(all: List<MessageDto>, myUid: String): List<ChatPreview> =
        withContext(Dispatchers.Default) {
            // Keep only conversations involving me
            val mine = all.filter { it.fromUid == myUid || it.toUid == myUid }

            // Group by peer uid
            val byPeer = mine.groupBy { m -> if (m.fromUid == myUid) m.toUid else m.fromUid }

            // Build ChatPreview using latest message per peer
            val previews = byPeer.map { (peerUid, msgs) ->
                val latest = msgs.maxByOrNull { it.createdAt ?: 0L }
                val chatId = buildChatId(myUid, peerUid)
                ChatPreview(
                    chatId = chatId,
                    peerUid = peerUid,
                    displayName = peerUid,
                    lastMessage = latest?.body.orEmpty(),
                    lastTimestamp = latest?.createdAt ?: 0L
                )
            }

            // Sort by last activity desc
            previews.sortedByDescending { it.lastTimestamp }
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


