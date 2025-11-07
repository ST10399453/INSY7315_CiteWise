package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.citewise_mobile.offline.OfflineReset
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class AdminDashboardActivity : BaseActivity() {

    // Header
    private lateinit var tvGreeting: TextView
    private lateinit var btnMenu: ImageButton

    // KPIs
    private lateinit var tvTotalConsultants: TextView
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvActiveTasksCount: TextView

    // Charts
    private lateinit var pieActiveTasks: PieChart

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use shared base shell that contains the BottomNavigationView
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate the admin dashboard into the shell container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val childRoot = layoutInflater.inflate(
            R.layout.activity_admin_dashboard,
            baseContent,
            true
        )

        // ---- Bind views from admin layout ----
        tvGreeting          = childRoot.findViewById(R.id.tvGreeting)
        btnMenu             = childRoot.findViewById(R.id.btnMenu)

        tvTotalConsultants  = childRoot.findViewById(R.id.tvConsultantsValue)
        tvTotalStudents     = childRoot.findViewById(R.id.tvStudentsValue)
        tvActiveTasksCount  = childRoot.findViewById(R.id.tvActiveTasksCount)

        pieActiveTasks      = childRoot.findViewById(R.id.pieActiveTasks)

        // Bottom nav (role-aware)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, selectedItemId = R.id.nav_dashboard)

        // ---- Greeting pulled from Realtime DB ----
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

        // ---- Overflow menu: Sign out + full local wipe ----
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
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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

        // ---- (Demo) KPI numbers — replace with real data sources ----
        tvTotalConsultants.text = "30"
        tvTotalStudents.text    = "127"

        // ---- Active tasks pie chart ----
        // Replace these with your repository values
        val submitted = 18
        val inProgress = 32
        val awaiting = 12
        val completed = 10
        setupActiveTasksPie(submitted, inProgress, awaiting, completed)
    }

    /** Configure the Active Tasks donut (MPAndroidChart). */
    private fun setupActiveTasksPie(submitted: Int, inProgress: Int, awaitingFeedback: Int, completed: Int) {
        val total = submitted + inProgress + awaitingFeedback + completed
        tvActiveTasksCount.text = total.toString()

        val entries = listOf(
            PieEntry(submitted.toFloat(), "Submitted"),
            PieEntry(inProgress.toFloat(), "In Progress"),
            PieEntry(awaitingFeedback.toFloat(), "Awaiting"),
            PieEntry(completed.toFloat(), "Completed")
        )

        val c1 = ContextCompat.getColor(this, R.color.blue_400)
        val c2 = ContextCompat.getColor(this, R.color.indigo_500)
        val c3 = ContextCompat.getColor(this, R.color.purple_400)
        val c4 = ContextCompat.getColor(this, R.color.green_500)

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(c1, c2, c3, c4)
            sliceSpace = 2f
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(this@AdminDashboardActivity, R.color.text_dark)
        }

        pieActiveTasks.apply {
            data = PieData(dataSet)
            isDrawHoleEnabled = true
            holeRadius = 55f
            transparentCircleRadius = 60f
            setUsePercentValues(true)
            legend.isEnabled = false
            setDrawEntryLabels(false)
            description = Description().apply { text = "" }
            setNoDataText("")
            setTouchEnabled(false)
            animateY(700)
            invalidate()
        }
    }
}
