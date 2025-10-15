// app/src/main/java/com/example/citewise_mobile/StudentDashboardActivity.kt
package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ServiceReviewAdapter
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.RequestsPullWorker
import com.example.citewise_mobile.offline.ServiceRequestEntity
import com.example.citewise_mobile.offline.toServiceRequestDto
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class StudentDashboardActivity : BaseActivity() {

    private lateinit var btnRequestService: MaterialButton
    private lateinit var tvGreeting: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var tvMyRequests: TextView

    private lateinit var rvRequests: RecyclerView
    private lateinit var progressRequests: CircularProgressIndicator
    private lateinit var emptyRequests: View

    // Offline-first
    private val local by lazy { LocalRepos(this) }

    // Adapter data
    private val items = mutableListOf<ServiceRequestDto>()
    private lateinit var adapter: ServiceReviewAdapter

    // Dashboard shows recent items
    private val RECENT_DAYS = 7L
    private val MAX_ITEMS = 10

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Base shell
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // 2) Inflate child INTO base container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val childRoot = layoutInflater.inflate(
            R.layout.activity_student_dashboard,
            baseContent,
            true
        )

        // 3) Views
        btnRequestService  = childRoot.findViewById(R.id.btnRequestService)
        tvGreeting         = childRoot.findViewById(R.id.tvGreeting)
        btnMenu            = childRoot.findViewById(R.id.btnMenu)
        tvMyRequests       = childRoot.findViewById(R.id.tvMyRequests)

        rvRequests         = childRoot.findViewById(R.id.rvRequests)
        progressRequests   = childRoot.findViewById(R.id.progressRequests)
        emptyRequests      = childRoot.findViewById(R.id.emptyRequests)

        // 4) Bottom nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        // 5) CTA
        btnRequestService.setOnClickListener {
            startActivity(Intent(this, RequestServiceStepsActivity::class.java))
        }

        // 6) Greeting (Realtime DB)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("firstName")
                .get()
                .addOnSuccessListener { snap ->
                    val first = snap.getValue(String::class.java)?.trim().orEmpty()
                    tvGreeting.text = if (first.isNotEmpty()) "Hi $first" else "Hi"
                }
                .addOnFailureListener { tvGreeting.text = "Hi" }
        } else {
            tvGreeting.text = "Hi"
        }

        // 7) Overflow / sign out
        btnMenu.setOnClickListener { anchor ->
            val popup = android.widget.PopupMenu(this, anchor)
            MenuInflater(this).inflate(R.menu.menu_dashboard_overflow, popup.menu)
            try {
                val f = android.widget.PopupMenu::class.java.getDeclaredField("mPopup")
                f.isAccessible = true
                val helper = f.get(popup)
                helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(helper, true)
            } catch (_: Throwable) {}
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sign_out -> {
                        FirebaseAuth.getInstance().signOut()
                        getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
                        startActivity(
                            Intent(this, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // 8) Horizontal list setup (snap like a pager)
        rvRequests.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        PagerSnapHelper().attachToRecyclerView(rvRequests)
        rvRequests.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        rvRequests.setHasFixedSize(true)

        // Spacing between items (12dp left/right; first item also gets left padding)
        val spacePx = (12f * resources.displayMetrics.density).toInt()
        rvRequests.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val pos = parent.getChildAdapterPosition(view)
                outRect.right = spacePx
                if (pos == 0) outRect.left = spacePx
            }
        })

        // 9) Adapter (click opens TaskDetailsActivity)
        adapter = ServiceReviewAdapter(items) { clicked ->
            startActivity(
                Intent(this, TaskDetailsActivity::class.java)
                    .putExtra(TaskDetailsActivity.EXTRA_REQUEST, clicked)
            )
        }
        rvRequests.adapter = adapter

        // 10) CORRECT repeatOnLifecycle usage — launch ONCE here; it auto-starts/stops with STARTED state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                showLoading()
                local.requests.observeAll().collectLatest { entities ->
                    val list = mapRecent(entities)
                    items.clear()
                    items.addAll(list)
                    adapter.notifyDataSetChanged()
                    if (items.isEmpty()) showEmpty() else showHasRequests(items.size)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Trigger background refresh; UI is already bound to Room via repeatOnLifecycle above
        RequestsPullWorker.oneShot(this)
    }

    // --- state helpers ---
    private fun showLoading() {
        progressRequests.visibility = View.VISIBLE
        rvRequests.visibility       = View.GONE
        emptyRequests.visibility    = View.GONE
    }

    private fun showEmpty() {
        progressRequests.visibility = View.GONE
        rvRequests.visibility       = View.GONE
        emptyRequests.visibility    = View.VISIBLE
        tvMyRequests.text = "My Requests"
    }

    private fun showHasRequests(count: Int) {
        progressRequests.visibility = View.GONE
        emptyRequests.visibility    = View.GONE
        rvRequests.visibility       = View.VISIBLE
        tvMyRequests.text = "My Requests ($count)"
    }

    /** Recent items updated within RECENT_DAYS, limited to MAX_ITEMS. */
    private fun mapRecent(entities: List<ServiceRequestEntity>): List<ServiceRequestDto> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RECENT_DAYS)
        return entities
            .filter { it.updatedAt >= cutoff }
            .sortedByDescending { it.updatedAt }
            .take(MAX_ITEMS)
            .map { it.toServiceRequestDto() }
    }
}
