package com.example.citewise_mobile

import com.example.citewise_mobile.R
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ChatPreview
import com.example.citewise_mobile.adapters.ChatsAdapter
import com.example.citewise_mobile.adapters.ContactsAdapter
import com.example.citewise_mobile.api.MessageDto
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.MessagesRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.offline.CloudDataSources
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.UserEntity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class ChatsActivity : BaseActivity() {

    private lateinit var recentAdapter: ChatsAdapter
    private var fullChatList: List<ChatPreview> = emptyList()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { MessagesRepository(RetrofitInstance.messagesApi) }
    private val local by lazy { LocalRepos(this) }
    private val cloud by lazy { CloudDataSources() }

    private val fs by lazy { FirebaseFirestore.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_chats, baseContent, false)
        baseContent.addView(content)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_messages)

        // Recent list
        content.findViewById<RecyclerView>(R.id.rvRecent).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            recentAdapter = ChatsAdapter { chat ->
                startConversation(chat.peerUid, chat.displayName)
            }
            adapter = recentAdapter
            addItemDecoration(SpacesItemDecoration(8))
        }

        // Actions
        content.findViewById<ImageButton>(R.id.btnAddChat).setOnClickListener { showNewChatSheet() }
        content.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener { showSearchChatsSheet() }

        content.findViewById<TextInputEditText>(R.id.etSearchChats)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChats(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshRecent()
    }

    override fun onResume() {
        super.onResume()
        refreshRecent()
    }

    private fun filterChats(query: String) {
        if (query.isBlank()) {
            recentAdapter.submitList(fullChatList)
        } else {
            val filtered = fullChatList.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                        it.lastMessage.contains(query, ignoreCase = true)
            }
            recentAdapter.submitList(filtered)
        }
    }

    // ───────────────────────── Recent (Firestore Chats) ─────────────────────────
    private fun refreshRecent() {
        val myUid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val chatsSnapshot = withContext(Dispatchers.IO) {
                    fs.collection("Chats")
                        .whereArrayContains("participants", myUid)
                        .limit(50)
                        .get()
                        .await()
                }

                val peerUids = chatsSnapshot.documents.mapNotNull { doc ->
                    val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                    participants.firstOrNull { it != myUid }?.toString()
                }.toSet()

                val users = withContext(Dispatchers.IO) { resolveUsersOneByOne(peerUids) }
                val nameMap = users.associate { u ->
                    val name = "${u.firstName} ${u.surname}".trim().ifEmpty { u.email.ifEmpty { "User ${u.uid.take(6)}" } }
                    u.uid to name
                }

                val previews = chatsSnapshot.documents.mapNotNull { doc ->
                    val chatId = doc.id
                    val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                    val peerUid = participants.firstOrNull { it != myUid }?.toString() ?: return@mapNotNull null

                    val lastMessage = doc.get("lastMessage") as? Map<*, *>
                    val lastMessageText = lastMessage?.get("preview")?.toString() ?: "No messages yet"
                    val lastTimestamp = lastMessage?.get("createdAt") as? Long ?: 0L

                    val displayName = nameMap[peerUid] ?: "User ${peerUid.take(6)}"

                    ChatPreview(
                        chatId = chatId,
                        peerUid = peerUid,
                        displayName = displayName,
                        lastMessage = lastMessageText,
                        lastTimestamp = lastTimestamp
                    )
                }
                    .sortedByDescending { it.lastTimestamp }

                fullChatList = previews

                val emptyState = findViewById<View>(R.id.emptyChatsState)
                val recentLabel = findViewById<View>(R.id.tvRecent)
                if (previews.isEmpty()) {
                    emptyState?.visibility = View.VISIBLE
                    recentLabel?.visibility = View.GONE
                } else {
                    emptyState?.visibility = View.GONE
                    recentLabel?.visibility = View.VISIBLE
                }

                recentAdapter.submitList(previews)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "refreshRecent exception", t)
            }
        }
    }

    // ───────────────────────── Bottom sheet: New Chat ─────────────────────────
    private fun showNewChatSheet() {
        val dlg = BottomSheetDialog(this, R.style.AppBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_new_chat, null)
        dlg.setContentView(view)
        forceExpand(dlg)

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dlg.dismiss() }

        lifecycleScope.launch {
            val myUid = auth.currentUser?.uid ?: return@launch
            val role = withContext(Dispatchers.IO) { fetchRoleFromRtdb(myUid) }?.lowercase(Locale.US)

            if (role == "student") {
                view.findViewById<View>(R.id.chipAll)?.visibility = View.GONE
            }
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        val progress = view.findViewById<View>(R.id.progressContacts)
        val empty = view.findViewById<View>(R.id.emptyState)

        val adapter = ContactsAdapter { user ->
            dlg.dismiss()
            startConversation(
                peerUid = user.uid,
                displayName = "${user.firstName} ${user.surname}".trim().ifEmpty { user.email }
            )
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                android.util.Log.d("ContactsAdapter", "[NEW CHAT] onChanged total=${adapter.itemCount}")
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                android.util.Log.d("ContactsAdapter", "[NEW CHAT] inserted @$positionStart x$itemCount total=${adapter.itemCount}")
            }
        })

        progress?.visibility = View.VISIBLE
        rv.visibility = View.GONE
        empty?.visibility = View.GONE

        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { loadRoleAwareContactsDirectFromFirestore() }
            progress?.visibility = View.GONE

            if (contacts.isEmpty()) {
                rv.visibility = View.GONE
                empty?.visibility = View.VISIBLE
            } else {
                adapter.submitListSafe(contacts)
                rv.visibility = View.VISIBLE
                empty?.visibility = View.GONE
                rv.itemAnimator = null
                rv.setHasFixedSize(true)
                rv.post {
                    android.util.Log.d("RV", "height=${rv.height} itemCount=${adapter.itemCount}")
                    rv.requestLayout()
                    rv.invalidateItemDecorations()
                    rv.scrollToPosition(0)
                }
                view.findViewById<TextInputEditText>(R.id.etSearch)
                    ?.addTextChangedListener(filterWatcher(adapter))
            }
        }

        dlg.show()
    }

    // ───────────────────────── Bottom sheet: Search Chat ─────────────────────────
    private fun showSearchChatsSheet() {
        val dlg = BottomSheetDialog(this, R.style.AppBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_search_chat, null)
        dlg.setContentView(view)
        forceExpand(dlg)

        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dlg.dismiss() }

        lifecycleScope.launch {
            val myUid = auth.currentUser?.uid ?: return@launch
            val role = withContext(Dispatchers.IO) { fetchRoleFromRtdb(myUid) }?.lowercase(Locale.US)

            if (role == "student") {
                view.findViewById<View>(R.id.chipAllSearch)?.visibility = View.GONE
            }
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        val progress = view.findViewById<View>(R.id.progressResults)
        val empty = view.findViewById<View>(R.id.emptyResults)

        val adapter = ContactsAdapter { user ->
            dlg.dismiss()
            startConversation(
                peerUid = user.uid,
                displayName = "${user.firstName} ${user.surname}".trim().ifEmpty { user.email }
            )
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                android.util.Log.d("ContactsAdapter", "[SEARCH] onChanged total=${adapter.itemCount}")
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                android.util.Log.d("ContactsAdapter", "[SEARCH] inserted @$positionStart x$itemCount total=${adapter.itemCount}")
            }
        })

        progress?.visibility = View.VISIBLE
        rv.visibility = View.GONE
        empty?.visibility = View.GONE
        rv.post { rv.scrollToPosition(0) }

        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { loadRoleAwareContactsDirectFromFirestore() }
            progress?.visibility = View.GONE

            if (contacts.isEmpty()) {
                rv.visibility = View.GONE
                empty?.visibility = View.VISIBLE
            } else {
                adapter.submitListSafe(contacts)
                rv.visibility = View.VISIBLE
                empty?.visibility = View.GONE
                rv.itemAnimator = null
                rv.setHasFixedSize(true)
                rv.post {
                    android.util.Log.d("RV", "[SEARCH] height=${rv.height} itemCount=${adapter.itemCount}")
                    rv.requestLayout()
                    rv.invalidateItemDecorations()
                    rv.scrollToPosition(0)
                }
                view.findViewById<TextInputEditText>(R.id.etSearch)
                    ?.addTextChangedListener(filterWatcher(adapter))
            }
        }

        dlg.show()
    }

    private fun forceExpand(dlg: BottomSheetDialog) {
        dlg.behavior.skipCollapsed = true
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dlg.setOnShowListener {
            val sheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val beh = BottomSheetBehavior.from(it)
                beh.peekHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                beh.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun filterWatcher(adapter: ContactsAdapter) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString().orEmpty()) }
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

    private suspend fun fetchRoleFromRtdb(uid: String): String? = withContext(Dispatchers.IO) {
        try { rtdb.reference.child("users").child(uid).child("role").get().await()
            .getValue(String::class.java)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "fetchRoleFromRtdb($uid) failed", t); null
        }
    }

    private suspend fun loadRoleAwareContactsDirectFromFirestore(): List<UserEntity> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid ?: return@withContext emptyList<UserEntity>()
        val role = fetchRoleFromRtdb(myUid)?.lowercase(Locale.US)
        android.util.Log.d(TAG, "Effective role (new chat): ${role?.uppercase(Locale.US)}")

        val relatedUids: Set<String> = when (role) {
            "student" -> {
                val snap = runCatching {
                    fs.collection(COL_REVIEWS).whereEqualTo(F_USER_ID, myUid).get().await()
                }.getOrNull()
                snap?.documents?.mapNotNull { it.getString(F_CONSULTANT_ID) }?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet()
            }
            "consultant" -> {
                val studentSnap = runCatching {
                    fs.collection(COL_REVIEWS).whereEqualTo(F_CONSULTANT_ID, myUid).get().await()
                }.getOrNull()
                val studentUids = studentSnap?.documents?.mapNotNull { it.getString(F_USER_ID) }?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet()

                // Fetch all admin UIDs from RTDB
                val adminUids = runCatching {
                    val usersSnap = rtdb.reference.child("users").get().await()
                    buildSet {
                        usersSnap.children.forEach { userSnap ->
                            val userRole = userSnap.child("role").getValue(String::class.java)?.lowercase(Locale.US)
                            if (userRole == "admin") {
                                userSnap.key?.let { add(it) }
                            }
                        }
                    }
                }.getOrNull() ?: emptySet()

                studentUids + adminUids
            }
            "admin" -> {
                val snap = runCatching { fs.collection(COL_REVIEWS).limit(500).get().await() }.getOrNull()
                buildSet {
                    snap?.documents?.forEach { d ->
                        d.getString(F_CONSULTANT_ID)?.takeIf { it.isNotBlank() }?.let(::add)
                        d.getString(F_USER_ID)?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            else -> emptySet()
        }

        if (relatedUids.isEmpty()) return@withContext emptyList()

        val users = resolveUsersOneByOne(relatedUids).filter { it.uid != myUid }
        users.sortedWith(compareBy({ it.firstName.lowercase(Locale.getDefault()) },
            { it.surname.lowercase(Locale.getDefault()) }))
    }

    private suspend fun resolveUsersOneByOne(uids: Set<String>): List<UserEntity> = withContext(Dispatchers.IO) {
        uids.map { uid ->
            async {
                val fsDoc = runCatching { fs.collection("users").document(uid).get().await() }.getOrNull()
                fsDoc?.takeIf { it.exists() }?.toUserEntityFromFs(uid)
                    ?: fetchRtdbUser(uid)
                    ?: UserEntity(uid, "", "", "", "", 0L)
            }
        }.awaitAll()
    }

    private fun DocumentSnapshot.toUserEntityFromFs(fallbackUid: String): UserEntity {
        val uid = (getString("uid") ?: id).ifBlank { fallbackUid }
        val first = (getString("firstName") ?: getString("firstname") ?: "").trim()
        val sur = (getString("surname") ?: getString("lastName") ?: "").trim()
        val nameRaw = (getString("name") ?: "").trim()
        val (firstName, surname) = if (first.isNotEmpty() || sur.isNotEmpty()) first to sur
        else if (nameRaw.contains(" ")) {
            val parts = nameRaw.split(Regex("\\s+"), limit = 2)
            (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
        } else nameRaw to ""
        val email = getString("email").orEmpty()
        val role = (getString("role") ?: "").trim().lowercase(Locale.getDefault())
        val updatedAt = readMillisFlexible("updatedAt")?.takeIf { it > 0 }
            ?: readMillisFlexible("createdAt") ?: 0L
        return UserEntity(uid, firstName, surname, email, role, updatedAt)
    }

    private suspend fun fetchRtdbUser(uid: String): UserEntity? {
        return try {
            val snap = rtdb.reference.child("users").child(uid).get().await()
            if (!snap.exists()) return null
            val first = snap.child("firstName").getValue(String::class.java) ?: ""
            val sur = snap.child("surname").getValue(String::class.java) ?: ""
            val email = snap.child("email").getValue(String::class.java) ?: ""
            val role = (snap.child("role").getValue(String::class.java) ?: "")
                .trim().lowercase(Locale.getDefault())
            val updatedAt = snap.child("updatedAt").getValue(Long::class.java)
                ?: snap.child("createdAt").getValue(Long::class.java) ?: 0L
            UserEntity(uid, first, sur, email, role, updatedAt)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "fetchRtdbUser($uid) failed", t); null
        }
    }

    private fun DocumentSnapshot.readMillisFlexible(field: String): Long? {
        val v = get(field) ?: return null
        return when (v) {
            is Number -> v.toLong()
            is Timestamp -> v.toDate().time
            is Date -> v.time
            is String -> try { java.time.Instant.parse(v).toEpochMilli() } catch (_: Throwable) { v.toLongOrNull() }
            else -> null
        }
    }

    private fun buildChatId(a: String, b: String) = if (a <= b) "${a}_$b" else "${b}_$a"

    companion object {
        private const val TAG = "ChatsActivity"
        private const val COL_REVIEWS = "ServiceReviews"
        private const val F_CONSULTANT_ID = "consultantId"
        private const val F_USER_ID = "userId"
    }
}

class SpacesItemDecoration(private val spaceDp: Int) : RecyclerView.ItemDecoration() {
    private fun Int.dp(view: View) = (this * view.resources.displayMetrics.density).toInt()
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val s = spaceDp.dp(view)
        outRect.set(0, s, 0, 0)
        if (parent.getChildAdapterPosition(view) == 0) outRect.top = s
    }
}
