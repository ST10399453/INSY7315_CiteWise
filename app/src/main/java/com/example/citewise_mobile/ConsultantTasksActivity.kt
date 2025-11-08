package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.example.citewise_mobile.adapters.TaskAdapter
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsultantTasksActivity : BaseActivity() {

    // Same enum used in the dashboard
    enum class Urgency {ALL, URGENT, MEDIUM, LOW}

    private lateinit var fullTaskList: List<ServiceRequestDto>
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var urgencyFilterGroup: MaterialButtonToggleGroup

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consultant_tasks)

        // Find and Setup Bottom Nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        // Set the active item to the Tasks tab
        setupBottomNav(bottomNav, R.id.nav_role_action)

        //Find UI Components
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView)
        urgencyFilterGroup = findViewById(R.id.urgencyFilterGroup)
        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        //Setup Task List (Adapter)
        setupTaskListAdapter()

        //Setup Filtering
        setupUrgencyFilter()

        //Load Data
        fetchAssignedTasks()
    }

    //Data Fetching
    private fun fetchAssignedTasks() {
        val consultantUid = auth.currentUser?.uid
        if (consultantUid.isNullOrBlank()) {
            fullTaskList = emptyList()
            filterTasks(Urgency.ALL)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = repo.listGeneralRequests(consultantUid)

            withContext(Dispatchers.Main) {
                fullTaskList = when (result) {
                    is com.example.citewise_mobile.data.NetResult.Ok -> result.data.orEmpty()
                    is com.example.citewise_mobile.data.NetResult.Err -> {
                        Toast.makeText(this@ConsultantTasksActivity,
                            "Failed to load tasks: ${result.message}", Toast.LENGTH_LONG).show()
                        emptyList()
                    }
                }
                // Initial update loads all data
                filterTasks(Urgency.ALL)
            }
        }
    }

    //Adapter Setup
    private fun setupTaskListAdapter() {
        val clickHandler: (ServiceRequestDto) -> Unit = { requestDto ->
            // Reuses the navigation to the details screen
            openTaskDetails(requestDto)
        }

        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        taskAdapter = TaskAdapter(emptyList(), clickHandler)
        tasksRecyclerView.adapter = taskAdapter
    }

    //Filtering Logic
    private fun setupUrgencyFilter() {
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
                // updateFilterButtonStyles(checkedId)
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

    private fun openTaskDetails(requestDto: ServiceRequestDto) {
        val intent = Intent(this, ConsultantTaskDetailsActivity::class.java).apply {
            putExtra(ConsultantTaskDetailsActivity.EXTRA_REQUEST, requestDto)
        }
        startActivity(intent)
    }
}