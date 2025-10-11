package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.example.citewise_mobile.adapters.ServiceReviewAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

class StudentDashboardActivity : BaseActivity() {

    private lateinit var btnRequestService: MaterialButton
    private lateinit var vpServiceCarousel: ViewPager2
    private lateinit var tabsDots: TabLayout
    private lateinit var tvGreeting: TextView
    private lateinit var btnMenu: ImageButton

    // Loading/empty/content views for the requests section
    private lateinit var requestsSection: View
    private lateinit var progressRequests: View
    private lateinit var emptyRequests: View

    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var dotsMediator: TabLayoutMediator? = null

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
        vpServiceCarousel  = childRoot.findViewById(R.id.vpServiceCarousel)
        tabsDots           = childRoot.findViewById(R.id.tabsDots)

        // NEW: state views
        requestsSection    = childRoot.findViewById(R.id.requestsSection)
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

        // 8) Carousel visuals (transformer)
        prepareCarouselVisuals()
    }

    override fun onStart() {
        super.onStart()
        showLoading()
        loadFromApiPrimary()
    }

    // --- state helpers ---
    private fun showLoading() {
        progressRequests.visibility = View.VISIBLE
        requestsSection.visibility  = View.GONE
        emptyRequests.visibility    = View.GONE
    }

    private fun showEmpty() {
        progressRequests.visibility = View.GONE
        requestsSection.visibility  = View.GONE
        emptyRequests.visibility    = View.VISIBLE
    }

    private fun showContent() {
        progressRequests.visibility = View.GONE
        requestsSection.visibility  = View.VISIBLE
        emptyRequests.visibility    = View.GONE
    }

    /** PRIMARY: Load from Render API using the signed-in Firebase user. */
    private fun loadFromApiPrimary(status: String? = null) {
        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val res = withTimeout(15_000L) { repo.listMyRequests(status, uid) }

                when (res) {
                    is NetResult.Ok  -> {
                        if (res.data.isNotEmpty()) bindServiceCarousel(res.data)
                        else loadFromFirestoreFallback()
                    }
                    is NetResult.Err -> loadFromFirestoreFallback()
                }
            } catch (_: TimeoutCancellationException) {
                loadFromFirestoreFallback()
            } catch (_: Throwable) {
                loadFromFirestoreFallback()
            }
        }
    }

    /** SECONDARY (optional): Firestore fallback to ServiceReviews for the signed-in user. */
    private fun loadFromFirestoreFallback() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            bindServiceCarousel(emptyList())
            return
        }

        var q: Query = db.collection("ServiceReviews")
            .whereEqualTo("userId", uid)

        // If this requires an index and fails, either remove this orderBy or create the index in console.
        q = q.orderBy("updatedAt", Query.Direction.DESCENDING)

        q.get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { doc ->
                    val d = doc.data ?: emptyMap<String, Any?>()

                    // IMPORTANT: ServiceRequestDto now uses FlexTime? for time fields.
                    // We set them to null here to avoid type mismatches; the adapter
                    // will render "—" for missing dates.
                    ServiceRequestDto(
                        id           = doc.id,
                        status       = d["status"] as? String,
                        documentId   = (d["documentId"] as? String) ?: (d["fileId"] as? String),
                        userId       = d["userId"] as? String,
                        consultantId = d["consultantId"] as? String,
                        serviceType  = null, // unknown enum from fallback → let adapter show "Other"
                        description  = d["description"] as? String,
                        priority     = null,
                        deadline     = null,  // FlexTime?
                        createdAt    = null,  // FlexTime?
                        updatedAt    = null,  // FlexTime?
                        feedback     = d["feedback"] as? String
                    )
                }
                bindServiceCarousel(list)
            }
            .addOnFailureListener {
                bindServiceCarousel(emptyList())
            }
    }

    /** Safely set/replace adapter and (re)attach dots after adapter is present. */
    private fun bindServiceCarousel(items: List<ServiceRequestDto>) {
        dotsMediator?.detach()
        dotsMediator = null

        if (items.isEmpty()) {
            vpServiceCarousel.adapter = ServiceReviewAdapter(emptyList())
            showEmpty()
            return
        }

        vpServiceCarousel.adapter = ServiceReviewAdapter(items)
        (vpServiceCarousel.getChildAt(0) as? RecyclerView)?.overScrollMode =
            RecyclerView.OVER_SCROLL_NEVER

        dotsMediator = TabLayoutMediator(tabsDots, vpServiceCarousel) { _, _ -> }
        dotsMediator!!.attach()
        showContent()
    }

    /** Subtle scale/alpha page transformer + side paddings. */
    private fun prepareCarouselVisuals() {
        vpServiceCarousel.offscreenPageLimit = 3
        val pagePadding = resources.getDimensionPixelSize(R.dimen.vp_page_padding)
        val pageMargin  = resources.getDimensionPixelSize(R.dimen.vp_page_margin)
        vpServiceCarousel.setPadding(pagePadding, 0, pagePadding, 0)
        vpServiceCarousel.clipToPadding = false
        vpServiceCarousel.clipChildren = false

        val transformer = CompositePageTransformer().apply {
            addTransformer { page, position ->
                val r = 1 - abs(position)
                page.scaleY = 0.92f + r * 0.08f
                page.alpha  = 0.85f + r * 0.15f
                page.translationX = position * pageMargin
            }
        }
        vpServiceCarousel.setPageTransformer(transformer)
    }
}
