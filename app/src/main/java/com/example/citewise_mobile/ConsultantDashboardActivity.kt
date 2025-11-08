package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.QuoteRequestAdapter
import com.example.citewise_mobile.adapters.ScheduleTaskAdapter
import com.example.citewise_mobile.adapters.TaskAdapter
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.example.citewise_mobile.utils.DateUtils.isSameDay
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsultantDashboardActivity : BaseActivity() {

    //Enums and Data Holders
    enum class Urgency {ALL, URGENT, MEDIUM, LOW}

    private lateinit var fullTaskList: List<ServiceRequestDto>

    //Adapters
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var scheduleAdapter: ScheduleTaskAdapter
    private lateinit var quoteRequestAdapter: QuoteRequestAdapter

    //UI Components
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var scheduleRecyclerView: RecyclerView
    private lateinit var quoteRequestsRecyclerView: RecyclerView
    private lateinit var urgencyFilterGroup: MaterialButtonToggleGroup

    //Repositories/Auth
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }


    override fun onCreate(savedInstanceState: Bundle?) {
        // BaseActivity handles enableEdgeToEdge() and super.onCreate()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultant_dashboard)

        //Find the Bottom Nav and set it up immediately
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        //Initialize UI Components
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView)
        scheduleRecyclerView = findViewById(R.id.scheduleRecyclerView)
        quoteRequestsRecyclerView = findViewById(R.id.quoteRequestsRecyclerView)
        urgencyFilterGroup = findViewById(R.id.urgencyFilterGroup)

        //Setup Adapters and RecyclerViews
        setupTaskAdapters()

        //Setup Urgency Filtering
        setupUrgencyFilter()

        //Load Data
        fetchAssignedTasks()
    }

    // -------------------------------------------------------------------------
    //  Data Fetching and Updates
    // -------------------------------------------------------------------------

    private fun fetchAssignedTasks() {
        val consultantUid = auth.currentUser?.uid
        if (consultantUid.isNullOrBlank()) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            fullTaskList = emptyList()
            updateAllDashboardLists()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = repo.listGeneralRequests(consultantUid)

            withContext(Dispatchers.Main) {
                fullTaskList = when (result) {
                    is com.example.citewise_mobile.data.NetResult.Ok -> result.data.orEmpty()
                    is com.example.citewise_mobile.data.NetResult.Err -> {
                        Toast.makeText(this@ConsultantDashboardActivity,
                            "Failed to load tasks: ${result.message}", Toast.LENGTH_LONG).show()
                        emptyList()
                    }
                }
                updateAllDashboardLists()
            }
        }
    }

    private fun updateAllDashboardLists() {
        filterTasks(Urgency.ALL)
        scheduleAdapter.updateList(getTasksForDate(System.currentTimeMillis()))
        quoteRequestAdapter.updateList(getPendingQuotes())
    }

    // -------------------------------------------------------------------------
    //  Adapter Setup
    // -------------------------------------------------------------------------

    private fun setupTaskAdapters() {
        val clickHandler: (ServiceRequestDto) -> Unit = { requestDto ->
            openTaskDetails(requestDto)
        }

        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        taskAdapter = TaskAdapter(emptyList(), clickHandler)
        tasksRecyclerView.adapter = taskAdapter

        scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleTaskAdapter(emptyList(), clickHandler)
        scheduleRecyclerView.adapter = scheduleAdapter

        quoteRequestsRecyclerView.layoutManager = LinearLayoutManager(this)
        quoteRequestAdapter = QuoteRequestAdapter(emptyList(), clickHandler)
        quoteRequestsRecyclerView.adapter = quoteRequestAdapter
    }

    // -------------------------------------------------------------------------
    //  Filtering Logic
    // -------------------------------------------------------------------------

    private fun setupUrgencyFilter() {
        // MaterialButtonToggleGroup uses addOnButtonCheckedListener
        urgencyFilterGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val selectedUrgency = when (checkedId) {
                    R.id.filterUrgent -> Urgency.URGENT
                    R.id.filterMedium -> Urgency.MEDIUM
                    R.id.filterLow -> Urgency.LOW
                    R.id.filterAll -> Urgency.ALL
                    else -> Urgency.ALL
                }
                filterTasks(selectedUrgency)
            }
        }
    }

    private fun filterTasks(urgency: Urgency) {
        val filteredList = if (urgency == Urgency.ALL) {
            fullTaskList
        } else {
            val targetPriority = when(urgency) {
                Urgency.URGENT -> ServicePriority.HIGH
                Urgency.MEDIUM -> ServicePriority.MEDIUM
                Urgency.LOW -> ServicePriority.LOW
                else -> null
            }
            fullTaskList.filter { it.priority == targetPriority }
        }
        taskAdapter.updateList(filteredList)
    }

    private fun getTasksForDate(dateMillis: Long): List<ServiceRequestDto> {
        return fullTaskList.filter { request ->
            request.deadline?.epochMillis?.let { deadlineMillis ->
                isSameDay(deadlineMillis, dateMillis)
            } ?: false
        }
    }

    private fun getPendingQuotes(): List<ServiceRequestDto> {
        return fullTaskList.filter {
            it.status?.equals("AWAITING_QUOTE", ignoreCase = true) == true ||
                    it.status?.equals("QUOTE_REVISE", ignoreCase = true) == true
        }
    }

    // -------------------------------------------------------------------------
    //  Navigation
    // -------------------------------------------------------------------------

    private fun openTaskDetails(requestDto: ServiceRequestDto) {
        val intent = Intent(this, ConsultantTaskDetailsActivity::class.java).apply {
            putExtra(ConsultantTaskDetailsActivity.EXTRA_REQUEST, requestDto)
        }
        startActivity(intent)
    }
}