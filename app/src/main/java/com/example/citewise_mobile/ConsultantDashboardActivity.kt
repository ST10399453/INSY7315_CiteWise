package com.example.citewise_mobile

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
import com.example.citewise_mobile.utils.DateUtils.isSameDay
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsultantDashboardActivity : BaseActivity() {

    enum class Urgency { ALL, URGENT, MEDIUM, LOW }

    private var fullTaskList: List<ServiceRequestDto> = emptyList()
    private lateinit var tvViewAllTasks: TextView

    // Adapters
    private lateinit var serviceReviewAdapter: ServiceReviewAdapter
//    private lateinit var scheduleAdapter: ScheduleTaskAdapter
//    private lateinit var quoteRequestAdapter: QuoteRequestAdapter

    // UI
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var scheduleRecyclerView: RecyclerView
    private lateinit var quoteRequestsRecyclerView: RecyclerView
    private lateinit var urgencyFilterGroup: MaterialButtonToggleGroup
    private lateinit var tvGreeting: TextView

    private lateinit var tvEmptyRequests: TextView

    // Data
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shared shell
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate page into shell
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_consultant_dashboard, baseContent, false)
        baseContent.addView(content)

        // Bottom nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        // UI setup
        tvGreeting = content.findViewById(R.id.tvGreeting)
        tasksRecyclerView = content.findViewById(R.id.tasksRecyclerView)
        tvViewAllTasks = content.findViewById(R.id.tvViewAllTasks)
        scheduleRecyclerView = content.findViewById(R.id.scheduleRecyclerView)
        quoteRequestsRecyclerView = content.findViewById(R.id.quoteRequestsRecyclerView)
        urgencyFilterGroup = content.findViewById(R.id.urgencyFilterGroup)
        tvEmptyRequests = content.findViewById(R.id.tvEmptyRequests)


        // active tasks need to change
        tvViewAllTasks.setOnClickListener {
            val intent = Intent(this, ConsultantTasksActivity::class.java)
            startActivity(intent)
        }

        // Default checked
        urgencyFilterGroup.check(R.id.filterAll)

        setupAdapters()
        setupUrgencyFilter()
        fetchAssignedTasks()

        // Greeting
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
        } else tvGreeting.text = "Hi"
    }

    // ---- Fetch Data ----
    private fun fetchAssignedTasks() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            fullTaskList = emptyList()
            //updateAllSections()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = repo.listGeneralRequests(uid)
            withContext(Dispatchers.Main) {
                fullTaskList = when (result) {
                    is com.example.citewise_mobile.data.NetResult.Ok  -> result.data.orEmpty()
                    is com.example.citewise_mobile.data.NetResult.Err -> {
                        Toast.makeText(
                            this@ConsultantDashboardActivity,
                            "Failed to load tasks: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        emptyList()
                    }
                }
                //updateAllSections()
            }
        }
    }

//    private fun updateAllSections() {
//        filterTasks(Urgency.ALL)
//        scheduleAdapter.updateList(getTasksForDate(System.currentTimeMillis()))
//        quoteRequestAdapter.updateList(getPendingQuotes())
//    }

    // ---- Setup ----
    private fun setupAdapters() {
        val onTaskClick: (ServiceRequestDto) -> Unit = { openTaskDetails(it) }

        // ─── MAIN TASKS (Using ServiceReviewAdapter now) ───
        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        serviceReviewAdapter = ServiceReviewAdapter(mutableListOf(), onTaskClick)
        tasksRecyclerView.adapter = serviceReviewAdapter

        // ─── Schedule List ───
        scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        //scheduleAdapter = ScheduleTaskAdapter(emptyList(), onTaskClick)
       // scheduleRecyclerView.adapter = scheduleAdapter

        // ─── Quote Requests ───
        quoteRequestsRecyclerView.layoutManager = LinearLayoutManager(this)
       // quoteRequestAdapter = QuoteRequestAdapter(emptyList(), onTaskClick)
        //quoteRequestsRecyclerView.adapter = quoteRequestAdapter
    }

    private fun setupUrgencyFilter() {
        urgencyFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val urgency = when (checkedId) {
                R.id.filterUrgent -> Urgency.URGENT
                R.id.filterMedium -> Urgency.MEDIUM
                R.id.filterLow    -> Urgency.LOW
                else              -> Urgency.ALL
            }
            filterTasks(urgency)
        }
    }

    // ---- Logic ----
    private fun filterTasks(urgency: Urgency) {
        val filtered = when (urgency) {
            Urgency.ALL    -> fullTaskList
            Urgency.URGENT -> fullTaskList.filter { it.priority == ServicePriority.HIGH }
            Urgency.MEDIUM -> fullTaskList.filter { it.priority == ServicePriority.MEDIUM }
            Urgency.LOW    -> fullTaskList.filter { it.priority == ServicePriority.LOW }
        }
        serviceReviewAdapter.reset(filtered)

        tvEmptyRequests.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun getTasksForDate(dateMillis: Long): List<ServiceRequestDto> =
        fullTaskList.filter { r ->
            r.deadline?.epochMillis?.let { dl -> isSameDay(dl, dateMillis) } ?: false
        }

    private fun getPendingQuotes(): List<ServiceRequestDto> =
        fullTaskList.filter {
            it.status?.equals("AWAITING_QUOTE", ignoreCase = true) == true ||
                    it.status?.equals("QUOTE_REVISE", ignoreCase = true) == true
        }

    // ---- Navigation ----
    private fun openTaskDetails(requestDto: ServiceRequestDto) {
        startActivity(
            Intent(this, TaskDetailsActivity::class.java)
                .putExtra(TaskDetailsActivity.EXTRA_REQUEST, requestDto)
        )
    }
}
