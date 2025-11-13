package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ServiceReviewAdapter
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsultantTasksActivity : BaseActivity() {

    enum class Urgency { ALL, URGENT, MEDIUM, LOW }

    private var fullTaskList: List<ServiceRequestDto> = emptyList()
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var urgencyFilterGroup: MaterialButtonToggleGroup
    private lateinit var adapter: ServiceReviewAdapter

    private lateinit var emptyAll: TextView
    private lateinit var emptyUrgent: TextView
    private lateinit var emptyMedium: TextView
    private lateinit var emptyLow: TextView
    private lateinit var tvEmpty: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Use BaseActivity’s container
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_consultant_tasks, baseContent, false)
        baseContent.addView(content)

        // ✅ Bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_active_tasks)

        // ✅ Initialize views from inflated layout
        tasksRecyclerView = content.findViewById(R.id.tasksRecyclerView)
        urgencyFilterGroup = content.findViewById(R.id.urgencyFilterGroup)

        emptyAll = content.findViewById(R.id.emptyAll)
        emptyUrgent = content.findViewById(R.id.emptyUrgent)
        emptyMedium = content.findViewById(R.id.emptyMedium)
        emptyLow = content.findViewById(R.id.emptyLow)

        tvEmpty = content.findViewById(R.id.tvEmpty)

        // ✅ Setup adapter
        val onTaskClick: (ServiceRequestDto) -> Unit = { req ->
            startActivity(
                Intent(this, TaskDetailsActivity::class.java)
                    .putExtra(TaskDetailsActivity.EXTRA_REQUEST, req)
            )
        }
        adapter = ServiceReviewAdapter(mutableListOf(), onTaskClick)
        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        tasksRecyclerView.adapter = adapter

        // Default filter selection
        urgencyFilterGroup.check(R.id.filterAll)
        setupUrgencyFilter()

        // Fetch tasks
        fetchAssignedTasks()
    }

    private fun fetchAssignedTasks() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            fullTaskList = emptyList()
            filterTasks(Urgency.ALL)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = repo.listGeneralRequests(uid)
            withContext(Dispatchers.Main) {
                fullTaskList = when (result) {
                    is com.example.citewise_mobile.data.NetResult.Ok -> result.data.orEmpty()
                    is com.example.citewise_mobile.data.NetResult.Err -> {
                        Toast.makeText(
                            this@ConsultantTasksActivity,
                            "Failed to load tasks: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        emptyList()
                    }
                }
                filterTasks(Urgency.ALL)
            }
        }
    }

    private fun setupUrgencyFilter() {
        urgencyFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val urgency = when (checkedId) {
                R.id.filterUrgent -> Urgency.URGENT
                R.id.filterMedium -> Urgency.MEDIUM
                R.id.filterLow -> Urgency.LOW
                else -> Urgency.ALL
            }
            filterTasks(urgency)
        }
    }

    private fun filterTasks(urgency: Urgency) {
        // First filter by priority
        val base = when (urgency) {
            Urgency.ALL -> fullTaskList
            Urgency.URGENT -> fullTaskList.filter { it.priority == ServicePriority.HIGH }
            Urgency.MEDIUM -> fullTaskList.filter { it.priority == ServicePriority.MEDIUM }
            Urgency.LOW -> fullTaskList.filter { it.priority == ServicePriority.LOW }
        }

        // Then push completed ones to the bottom
        val filtered = base.sortedBy { it.status == "Completed" }

        adapter.reset(filtered)

        // 🟦 Hide all empty states first
        emptyAll.visibility = View.GONE
        emptyUrgent.visibility = View.GONE
        emptyMedium.visibility = View.GONE
        emptyLow.visibility = View.GONE

        // 🟦 Show correct message if empty
        if (filtered.isEmpty()) {
            when (urgency) {
                Urgency.ALL -> emptyAll.visibility = View.VISIBLE
                Urgency.URGENT -> emptyUrgent.visibility = View.VISIBLE
                Urgency.MEDIUM -> emptyMedium.visibility = View.VISIBLE
                Urgency.LOW -> emptyLow.visibility = View.VISIBLE
            }
        }
    }
}
