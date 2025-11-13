package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.TopConsultantAdapter
import com.example.citewise_mobile.adapters.TopConsultant
import com.example.citewise_mobile.offline.OfflineReset
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AdminDashboardActivity : BaseActivity() {

    // Header
    private lateinit var tvGreeting: TextView
    private lateinit var btnMenu: ImageButton

    // KPIs
    private lateinit var tvTotalConsultants: TextView
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvActiveTasksCount: TextView

    // Legend labels
    private lateinit var tvLegendSubmitted: TextView
    private lateinit var tvLegendAssigned: TextView
    private lateinit var tvLegendCompleted: TextView

    // Top consultants
    private lateinit var rvTopConsultants: RecyclerView
    private lateinit var emptyTopConsultants: TextView
    private val topConsultants = mutableListOf<TopConsultant>()
    private lateinit var topConsultantAdapter: TopConsultantAdapter

    // Chart & progress
    private lateinit var pieActiveTasks: PieChart
    private lateinit var progressRequests: CircularProgressIndicator

    // Firestore listeners
    private var reviewsListener: ListenerRegistration? = null
    private var consultantsListener: ListenerRegistration? = null
    private var pieJob: Job? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Base shell with bottom nav
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate dashboard into shell
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val childRoot = layoutInflater.inflate(
            R.layout.activity_admin_dashboard,
            baseContent,
            true
        )

        // Bind views
        tvGreeting          = childRoot.findViewById(R.id.tvGreeting)
        btnMenu             = childRoot.findViewById(R.id.btnMenu)

        tvTotalConsultants  = childRoot.findViewById(R.id.tvConsultantsValue)
        tvTotalStudents     = childRoot.findViewById(R.id.tvStudentsValue)
        tvActiveTasksCount  = childRoot.findViewById(R.id.tvActiveTasksCount)

        tvLegendSubmitted   = childRoot.findViewById(R.id.tvLegendSubmitted)
        tvLegendAssigned    = childRoot.findViewById(R.id.tvLegendAssigned)
        tvLegendCompleted   = childRoot.findViewById(R.id.tvLegendCompleted)

        rvTopConsultants    = childRoot.findViewById(R.id.rvTopConsultants)
        emptyTopConsultants = childRoot.findViewById(R.id.emptyTopConsultants)

        pieActiveTasks      = childRoot.findViewById(R.id.pieActiveTasks)
        progressRequests    = childRoot.findViewById(R.id.progressRequests)

        // Bottom nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, selectedItemId = R.id.nav_dashboard)

        // Setup Top Consultants RecyclerView
        rvTopConsultants.layoutManager = LinearLayoutManager(this)
        topConsultantAdapter = TopConsultantAdapter(topConsultants)
        rvTopConsultants.adapter = topConsultantAdapter

        // Greeting from RTDB
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("firstName")
                .get()
                .addOnSuccessListener { snap ->
                    val first = snap.getValue(String::class.java)?.trim().orEmpty()
                    tvGreeting.text = if (first.isNotEmpty()) "Hi, $first" else "Hi"
                }
                .addOnFailureListener { tvGreeting.text = "Hi" }
        } else {
            tvGreeting.text = "Hi"
        }

        // Overflow menu (sign out + local wipe)
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
                        lifecycleScope.launch {
                            try {
                                FirebaseAuth.getInstance().signOut()
                                getSharedPreferences("user_prefs", MODE_PRIVATE)
                                    .edit().clear().apply()
                                OfflineReset.resetLocalData(applicationContext)
                                startActivity(
                                    Intent(this@AdminDashboardActivity, LoginActivity::class.java)
                                        .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        )
                                )
                                finish()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // Placeholders
        tvTotalConsultants.text = "—"
        tvTotalStudents.text = "—"

        // Fetch KPI counts (RTDB)
        fetchUserCounts()

        // Setup chart
        setupPieUi()

        // Streams
        startActiveTasksStream()
        startTopConsultantsStream()
    }

    override fun onStop() {
        super.onStop()
        reviewsListener?.remove()
        consultantsListener?.remove()
        reviewsListener = null
        consultantsListener = null
        pieJob?.cancel()
    }

    /** One-shot totals from Realtime Database (/users). */
    private fun fetchUserCounts() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val usersRef = FirebaseDatabase.getInstance().reference.child("users")
                val snap = usersRef.get().await()

                var studentCount = 0
                var consultantCount = 0

                for (child in snap.children) {
                    val role = child.child("role")
                        .getValue(String::class.java)
                        ?.trim()
                        ?.lowercase()
                    when (role) {
                        "student"    -> studentCount++
                        "consultant" -> consultantCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    tvTotalStudents.text = studentCount.toString()
                    tvTotalConsultants.text = consultantCount.toString()
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    tvTotalStudents.text = "0"
                    tvTotalConsultants.text = "0"
                }
            }
        }
    }

    /** Static styling for the pie widget. */
    private fun setupPieUi() = with(pieActiveTasks) {
        description = Description().apply { text = "" }
        legend.isEnabled = false
        setUsePercentValues(false)
        setDrawEntryLabels(false)
        isDrawHoleEnabled = true
        holeRadius = 55f
        transparentCircleRadius = 60f
        setNoDataText("No data")
        setTouchEnabled(true)
    }

    /** Live stream of ServiceReviews -> Submitted / Assigned / Completed (Firestore). */
    private fun startActiveTasksStream() {
        val db = FirebaseFirestore.getInstance()
        val query = db.collection("ServiceReviews")

        showLoading(true)

        // lifecycle-aware
        pieJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reviewsListener?.remove()
                reviewsListener = query.addSnapshotListener { snap, err ->
                    if (err != null) {
                        renderPie(0, 0, 0)
                        showLoading(false)
                        return@addSnapshotListener
                    }

                    val docs = snap?.documents ?: emptyList()
                    var submitted = 0
                    var assigned = 0
                    var completed = 0

                    for (d in docs) {
                        when ((d.getString("status") ?: "").trim()) {
                            "Submitted" -> submitted++
                            "Assigned"  -> assigned++
                            "Completed" -> completed++
                        }
                    }
                    renderPie(submitted, assigned, completed)
                    showLoading(false)
                }
            }
        }
    }

    /**
     * Render donut + legend, with colors:
     *  Submitted -> priority_Medium (orange)
     *  Assigned  -> blue_400
     *  Completed -> green_500
     */
    private fun renderPie(submitted: Int, assigned: Int, completed: Int) {
        val total = submitted + assigned + completed
        tvActiveTasksCount.text = total.toString()

        // Update legend text + colors
        val cSubmitted = color(R.color.priority_Medium)
        val cAssigned  = color(R.color.blue_400)
        val cCompleted = color(R.color.green_500)

        tvLegendSubmitted.text = "Submitted ($submitted)"
        tvLegendAssigned.text  = "Assigned ($assigned)"
        tvLegendCompleted.text = "Completed ($completed)"

        tvLegendSubmitted.setTextColor(cSubmitted)
        tvLegendAssigned.setTextColor(cAssigned)
        tvLegendCompleted.setTextColor(cCompleted)

        // Build entries only for non-zero slices
        val entries = mutableListOf<PieEntry>().apply {
            if (submitted > 0) add(PieEntry(submitted.toFloat(), "Submitted"))
            if (assigned  > 0) add(PieEntry(assigned.toFloat(),  "Assigned"))
            if (completed > 0) add(PieEntry(completed.toFloat(), "Completed"))
        }

        if (entries.isEmpty()) {
            pieActiveTasks.clear()
            pieActiveTasks.invalidate()
            return
        }

        val colors = entries.map { e ->
            when (e.label) {
                "Submitted" -> cSubmitted
                "Assigned"  -> cAssigned
                else        -> cCompleted
            }
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 2f
            setDrawValues(true)
            valueTextColor = color(R.color.text_dark)
            valueTextSize = 12f
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieActiveTasks))
        }

        pieActiveTasks.data = data
        pieActiveTasks.highlightValues(null)
        pieActiveTasks.animateY(700)
        pieActiveTasks.invalidate()
    }

    /** Live TopConsultants stream with empty state handling (TOP 3). */
    private fun startTopConsultantsStream() {
        val db = FirebaseFirestore.getInstance()
        val query = db.collection("TopConsultants")
            .orderBy("rating", Query.Direction.DESCENDING)
            .limit(3) // TOP 3

        consultantsListener?.remove()
        consultantsListener = query.addSnapshotListener { snap, err ->
            if (err != null) {
                emptyTopConsultants.visibility = View.VISIBLE
                rvTopConsultants.visibility = View.GONE
                topConsultantAdapter.setData(emptyList())
                return@addSnapshotListener
            }

            val docs = snap?.documents.orEmpty()
            if (docs.isEmpty()) {
                emptyTopConsultants.visibility = View.VISIBLE
                rvTopConsultants.visibility = View.GONE
                topConsultantAdapter.setData(emptyList())
                return@addSnapshotListener
            }

            val mapped = docs.mapNotNull { doc ->
                val id = doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                val rating = doc.getDouble("rating") ?: 0.0
                TopConsultant(
                    id = id,
                    name = name,
                    rating = rating
                )
            }

            emptyTopConsultants.visibility = View.GONE
            rvTopConsultants.visibility = View.VISIBLE

            topConsultantAdapter.setData(mapped)
        }
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun showLoading(loading: Boolean) {
        progressRequests.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
