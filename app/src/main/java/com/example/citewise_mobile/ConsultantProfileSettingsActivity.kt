package com.example.citewise_mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.HistoryAdapter
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.ConsultantStats
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.bumptech.glide.Glide

class ConsultantProfileSettingsActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }
    private val gson by lazy { Gson() }

    // Toggle / Sections
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var sectionOverview: View
    private lateinit var sectionSettings: View

    // Overview
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvExpertise: TextView
    private lateinit var tvStatRequests: TextView
    private lateinit var tvStatCompleted: TextView
    private lateinit var tvStatTurnaround: TextView
    private lateinit var tvStatRating: TextView
    private lateinit var tvRatingStars: TextView
    private lateinit var rvServiceHistory: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var chart: WebView

    // Settings
    private lateinit var rowEditProfile: View
    private lateinit var rowChangePassword: View
    private lateinit var rowUploadCV: View
    private lateinit var rowLanguage: View
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        val base = findViewById<ViewGroup>(R.id.baseContent)
        layoutInflater.inflate(R.layout.activity_consultant_profile_settings, base, true)

        setupBottomNav(findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_profile)
        bindViews()
        setupToggle()
        setupOverviewList()
        setupSettingsActions()
        loadOverview()
    }

    private fun bindViews() {
        toggleGroup = findViewById(R.id.toggleSettings)
        sectionOverview = findViewById(R.id.sectionOverview)
        sectionSettings = findViewById(R.id.sectionSettings)

        tvName = findViewById(R.id.tvOverviewName)
        tvEmail = findViewById(R.id.tvOverviewEmail)
        tvExpertise = findViewById(R.id.tvExpertise)
        tvStatRequests = findViewById(R.id.tvStatRequests)
        tvStatCompleted = findViewById(R.id.tvStatCompleted)
        tvStatTurnaround = findViewById(R.id.tvStatTurnaround)
        tvStatRating = findViewById(R.id.tvStatRating)
        tvRatingStars = findViewById(R.id.tvRatingStars)
        rvServiceHistory = findViewById(R.id.rvServiceHistory)
        chart = findViewById(R.id.turnaroundTimeChartWebView)

        rowEditProfile = findViewById(R.id.rowEditProfile)
        rowChangePassword = findViewById(R.id.rowChangePassword)
        rowUploadCV = findViewById(R.id.rowUploadCV)
        rowLanguage = findViewById(R.id.rowLanguage)
        btnLogout = findViewById(R.id.btnLogout)

        chart.settings.javaScriptEnabled = true
        chart.webViewClient = WebViewClient()
        chart.loadUrl("file:///android_asset/chart_template.html")
    }

    private fun setupToggle() {
        // default to Overview
        toggleGroup.check(R.id.btnOverview)
        showTab(true)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showTab(checkedId == R.id.btnOverview)
        }
    }

    private fun showTab(isOverview: Boolean) {
        sectionOverview.visibility = if (isOverview) View.VISIBLE else View.GONE
        sectionSettings.visibility = if (isOverview) View.GONE else View.VISIBLE
    }

    private fun setupOverviewList() {
        historyAdapter = HistoryAdapter(emptyList())
        rvServiceHistory.layoutManager = LinearLayoutManager(this)
        rvServiceHistory.adapter = historyAdapter
    }

    private fun setupSettingsActions() {
        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        rowChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        rowUploadCV.setOnClickListener {
            startActivity(Intent(this, UploadCvActivity::class.java))
        }
        rowLanguage.setOnClickListener {
            // hook when ready
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadOverview() {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val snap = rtdb.reference.child("users").child(uid).get().await()
            val first = snap.child("firstName").getValue(String::class.java).orEmpty()
            val last = snap.child("surname").getValue(String::class.java).orEmpty()
            val expertise = snap.child("specialisation").getValue(String::class.java)
            val email = auth.currentUser?.email.orEmpty()

            // ✅ Fetch Google photo if stored in DB
            val photoUrl = snap.child("photoUrl").getValue(String::class.java)

            val res = repo.listGeneralRequests(consultantId = uid, status = "completed")
            val tasks = if (res is NetResult.Ok) res.data.orEmpty() else emptyList()
            val stats = computeStats(tasks)

            withContext(Dispatchers.Main) {
                tvName.text = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Consultant" }
                tvEmail.text = email.ifBlank { "N/A" }
                tvExpertise.text = expertise ?: "Not Specified"

                // ✅ Load photo into ivAvatar
                val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this@ConsultantProfileSettingsActivity)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_user_avatar)
                        .circleCrop()
                        .into(ivAvatar)
                } else {
                    // Create initials drawable
                    ivAvatar.setImageDrawable(createInitialsDrawable(first, last))
                }

                // Update stats and chart
                historyAdapter.updateList(tasks)
                tvStatRequests.text = tasks.size.toString()
                tvStatCompleted.text = tasks.count { it.status.equals("completed", true) }.toString()
                tvStatTurnaround.text = String.format("%.1f", stats.averageTurnaroundDays)
                tvStatRating.text = String.format("%.1f", stats.averageRating)
                tvRatingStars.text = stars(stats.averageRating)
                renderChart(stats.monthlyTurnaroundTimes)
            }
        }
    }


    private fun computeStats(tasks: List<ServiceRequestDto>): ConsultantStats {
        if (tasks.isEmpty()) return ConsultantStats(averageRating = 5.0)

        val monthly = mutableMapOf<String, MutableList<Long>>()
        tasks.forEach { t ->
            val start = t.createdAt?.epochMillis ?: return@forEach
            val end = t.updatedAt?.epochMillis ?: return@forEach
            if (end > start) {
                val ym = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                    .format(java.util.Date(start))
                monthly.getOrPut(ym) { mutableListOf() }.add(end - start)
            }
        }

        val monthlyAvgDays = monthly.mapValues { it.value.average() / (1000.0 * 60 * 60 * 24) }
        val monthlySeries = monthlyAvgDays.entries.sortedBy { it.key }.map { e ->
            val label = java.text.SimpleDateFormat("MMM", java.util.Locale.US)
                .format(java.text.SimpleDateFormat("yyyy-MM").parse(e.key)!!)
            label to e.value
        }

        val overall = monthlyAvgDays.values.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgRating = 4.4 // replace with server value when available

        return ConsultantStats(
            averageTurnaroundDays = overall,
            averageRating = avgRating,
            monthlyTurnaroundTimes = monthlySeries
        )
    }

    private fun renderChart(points: List<Pair<String, Double>>) {
        val table = mutableListOf<List<Any>>(listOf("Month", "Avg Days"))
        points.forEach { (m, d) -> table.add(listOf(m, d)) }
        val json = Gson().toJson(table)
        val js = """
            javascript:(function(){
                chartData = $json;
                drawChart();
            })()
        """.trimIndent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            chart.evaluateJavascript(js, null)
        } else {
            chart.loadUrl(js)
        }
    }

    private fun stars(rating: Double): String {
        val filled = rating.roundToInt().coerceIn(0, 5)
        return "★".repeat(filled) + "☆".repeat(5 - filled)
    }

    private fun createInitialsDrawable(first: String, last: String, sizeDp: Int = 64): Drawable {
        val initials = listOf(first, last)
            .filter { it.isNotBlank() }
            .map { it.first().uppercaseChar() }
            .joinToString("")

        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.parseColor("#0027B2")
            isAntiAlias = true
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = sizePx / 2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val yPos = (canvas.height / 2 - (textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(initials, sizePx / 2f, yPos, textPaint)

        return BitmapDrawable(resources, bitmap)
    }
}
