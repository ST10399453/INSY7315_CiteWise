package com.example.citewise_mobile

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.HistoryAdapter
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.ConsultantStats
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ConsultantProfileSettingsActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db = FirebaseDatabase.getInstance()
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }
    private val gson by lazy { Gson() }

    // UI & Toggle
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var sectionOverview: View
    private lateinit var sectionSettings: View

    // Overview UI Elements
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvExpertise: TextView
    private lateinit var rvServiceHistory: RecyclerView
    private lateinit var tvRatingStars: TextView
    private lateinit var turnaroundTimeChartWebView: WebView
    private lateinit var tvStatRequests: TextView
    private lateinit var tvStatCompleted: TextView
    private lateinit var tvStatTurnaround: TextView
    private lateinit var tvStatRating: TextView

    // Settings elements
    private lateinit var rowEditProfile: View
    private lateinit var rowChangePassword: View
    private lateinit var rowUploadCV: View
    private lateinit var rowLanguage: View
    private lateinit var btnLogout: Button

    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Set the base layout
        setContentView(R.layout.activity_base)

        //Find the FrameLayout container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)

        //This creates a 'profileView' variable that we can search inside.
        val profileView = layoutInflater.inflate(R.layout.activity_consultant_profile_settings, baseContent, false)

        //Find Top-Level Views
        toggle          = profileView.findViewById(R.id.toggleSettings)
        sectionOverview = profileView.findViewById(R.id.sectionOverview)
        sectionSettings = profileView.findViewById(R.id.sectionSettings)

        //Find Overview Views
        tvName = profileView.findViewById(R.id.tvOverviewName)
        tvEmail = profileView.findViewById(R.id.tvOverviewEmail)
        tvExpertise = profileView.findViewById(R.id.tvExpertise)
        tvRatingStars = profileView.findViewById(R.id.tvRatingStars)
        rvServiceHistory = profileView.findViewById(R.id.rvServiceHistory)

        //Use the correct IDs from the consultant_profile_settings.xml
        tvStatRequests = profileView.findViewById(R.id.tvStatRequests)
        tvStatCompleted = profileView.findViewById(R.id.tvStatCompleted)
        tvStatTurnaround = profileView.findViewById(R.id.tvStatTurnaround)
        tvStatRating = profileView.findViewById(R.id.tvStatRating)

        //Setup WebView
        turnaroundTimeChartWebView = profileView.findViewById(R.id.turnaroundTimeChartWebView)
        turnaroundTimeChartWebView.settings.javaScriptEnabled = true
        turnaroundTimeChartWebView.webViewClient = WebViewClient()
        turnaroundTimeChartWebView.loadUrl("file:///android_asset/chart_template.html")

        // Setup History RecyclerView
        historyAdapter = HistoryAdapter(emptyList())
        rvServiceHistory.layoutManager = LinearLayoutManager(this)
        rvServiceHistory.adapter = historyAdapter

        //add the profileView to the screen
        baseContent.addView(profileView)

        //Setup Nav & Tabs
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_profile)

        toggle.check(R.id.btnOverview)
        showTab("overview")
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnOverview        -> showTab("overview")
                R.id.btnProfileSettings -> showTab("settings")
            }
        }

        //Pass 'profileView' to the helper methods
        bindOverview(profileView)
        initSettingsSection(profileView)
    }

    private fun showTab(which: String) {
        val isOverview = which == "overview"
        sectionOverview.visibility = if (isOverview) View.VISIBLE else View.GONE
        sectionSettings.visibility = if (isOverview) View.GONE else View.VISIBLE
    }

    // ---------------- Overview binding (LIVE DATA) ----------------
    private fun bindOverview(page: View) {
        val consultantUid = auth.currentUser?.uid
        if (consultantUid.isNullOrBlank()) {
            tvName.text = "Error Loading"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            //Fetch Profile Details
            val profileSnapshot = db.reference.child("users").child(consultantUid).get().await()
            val email = auth.currentUser?.email

            val firstName = profileSnapshot.child("firstName").getValue(String::class.java)
            val surname = profileSnapshot.child("surname").getValue(String::class.java)
            val specialization = profileSnapshot.child("specialisation").getValue(String::class.java)

            //Fetch Completed Service History (status="completed")
            val historyResult = repo.listGeneralRequests(consultantId = consultantUid, status = "completed")
            val allTasks =
                if (historyResult is com.example.citewise_mobile.data.NetResult.Ok) historyResult.data.orEmpty() else emptyList()

            //Calculate Stats
            val stats = calculateConsultantStats(allTasks)

            withContext(Dispatchers.Main) {
                //Update Account Info Card
                tvName.text = "${firstName.orEmpty()} ${surname.orEmpty()}".trim()
                tvEmail.text = email ?: "N/A"
                tvExpertise.text = specialization ?: "Not Specified"

                // Update History List
                historyAdapter.updateList(allTasks)

                // Update Stat Row
                val completedCount = allTasks.count { it.status == "completed" }
                tvStatRequests.text = allTasks.size.toString()
                tvStatCompleted.text = completedCount.toString()
                tvStatTurnaround.text = String.format("%.1f", stats.averageTurnaroundDays)
                tvStatRating.text = String.format("%.1f", stats.averageRating)

                // Update Rating and Graph
                tvRatingStars.text = formatRatingStars(stats.averageRating)
                renderTurnaroundGraph(stats.monthlyTurnaroundTimes)
            }
        }
    }

    // ---------------- Stats Calculation Logic ----------------
    private fun calculateConsultantStats(tasks: List<ServiceRequestDto>): ConsultantStats {
        if (tasks.isEmpty()) return ConsultantStats(averageRating = 5.0)

        val monthlyDataMap = mutableMapOf<String, MutableList<Long>>()
        for (task in tasks) {
            val start = task.createdAt?.epochMillis ?: continue
            val end = task.updatedAt?.epochMillis ?: continue
            if (end > start) {
                val tatMillis = end - start
                val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(start))
                monthlyDataMap.getOrPut(monthKey) { mutableListOf() }.add(tatMillis)
            }
        }

        //Calculate Monthly Averages (in Days)
        val monthlyAverages = monthlyDataMap.mapValues { (_, tatList) ->
            tatList.average() / (1000.0 * 60 * 60 * 24)
        }

        //Prepare Data for Chart
        val chartData = monthlyAverages
            .entries
            .sortedBy { it.key }
            .map { entry ->
                val monthLabel = java.text.SimpleDateFormat("MMM", java.util.Locale.US).format(java.text.SimpleDateFormat("yyyy-MM").apply { isLenient = true }.parse(entry.key)!!)
                monthLabel to entry.value
            }

        //Calculate Overall Average
        val overallAvgTatDays = if (monthlyAverages.values.isNotEmpty()) monthlyAverages.values.average() else 0.0
        val avgRating = 4.3 // Mocked for now

        return ConsultantStats(
            averageTurnaroundDays = overallAvgTatDays,
            averageRating = avgRating,
            monthlyTurnaroundTimes = chartData
        )
    }

    // ---------------- Graph Rendering Logic (WebView Injection) ----------------
    private fun renderTurnaroundGraph(monthlyData: List<Pair<String, Double>>) {
        val jsData = mutableListOf<List<Any>>()
        jsData.add(listOf("Month", "Avg Days"))

        monthlyData.forEach { (monthLabel, days) ->
            jsData.add(listOf(monthLabel, days))
        }

        val jsonString = gson.toJson(jsData)
        val jsInjectionCode = """
            javascript: (function() {
                chartData = $jsonString;
                drawChart(); 
            })()
        """.trimIndent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            turnaroundTimeChartWebView.evaluateJavascript(jsInjectionCode, null)
        } else {
            turnaroundTimeChartWebView.loadUrl(jsInjectionCode)
        }
    }

    // ---------------- Utility Methods ----------------
    private fun formatRatingStars(rating: Double): String {
        val fullStar = '★'
        val emptyStar = '☆'
        val maxStars = 5
        val filled = rating.roundToInt().coerceIn(0, maxStars)
        val empty = maxStars - filled
        return fullStar.toString().repeat(filled) + emptyStar.toString().repeat(empty)
    }

    private fun initSettingsSection(page: View) {
        rowEditProfile = page.findViewById(R.id.rowEditProfile)
        rowChangePassword = page.findViewById(R.id.rowChangePassword)
        rowUploadCV = page.findViewById(R.id.rowUploadCV)
        rowLanguage = page.findViewById(R.id.rowLanguage)
        btnLogout = page.findViewById(R.id.btnLogout)

        //Labeling navigation rows
        rowEditProfile.findViewById<TextView>(R.id.rowLabel).text = "Edit profile"
        rowChangePassword.findViewById<TextView>(R.id.rowLabel).text = "Change password"
        rowUploadCV.findViewById<TextView>(R.id.rowLabel).text = "Upload CV"

        //Notifications and Language
        page.findViewById<View>(R.id.rowPushNotifications).findViewById<TextView>(R.id.rowLabel).text = "Push notifications"
        page.findViewById<View>(R.id.rowAppNotifications).findViewById<TextView>(R.id.rowLabel).text = "App notifications"
        page.findViewById<View>(R.id.rowEmailNotifications).findViewById<TextView>(R.id.rowLabel).text = "Email notifications"
        page.findViewById<View>(R.id.rowLanguage).findViewById<TextView>(R.id.tvLabel).text = "Language"
        page.findViewById<View>(R.id.rowLanguage).findViewById<TextView>(R.id.tvValue).text = "English"

        // Clicks
        rowEditProfile.setOnClickListener {

            startActivity(Intent(this, EditProfileActivity::class.java))
            Toast.makeText(this, "Edit Profile clicked", Toast.LENGTH_SHORT).show()
        }
        rowChangePassword.setOnClickListener {

            startActivity(Intent(this, ChangePasswordActivity::class.java))
            Toast.makeText(this, "Change Password clicked", Toast.LENGTH_SHORT).show()
        }
        rowUploadCV.setOnClickListener {
            startActivity(Intent(this, UploadCvActivity::class.java))
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
