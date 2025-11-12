package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.TaskAdapter
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

    private lateinit var taskAdapter: TaskAdapter
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var urgencyFilterGroup: MaterialButtonToggleGroup

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_consultant_tasks, baseContent, false)
        baseContent.addView(content)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_active_tasks)

        tasksRecyclerView  = content.findViewById(R.id.tasksRecyclerView)
        urgencyFilterGroup = content.findViewById(R.id.urgencyFilterGroup)

        content.findViewById<View>(R.id.btnBack)
            .setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Default selected
        urgencyFilterGroup.check(R.id.filterAll)

        setupTaskListAdapter()
        setupUrgencyFilter()
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
                    is com.example.citewise_mobile.data.NetResult.Ok  -> result.data.orEmpty()
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

    private fun setupTaskListAdapter() {
        val click: (ServiceRequestDto) -> Unit = { req ->
            val intent = Intent(this, TaskDetailsActivity::class.java)
                .putExtra(TaskDetailsActivity.EXTRA_REQUEST, req)
            startActivity(intent)
        }
        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        taskAdapter = TaskAdapter(emptyList(), click)
        tasksRecyclerView.adapter = taskAdapter
    }

    private fun setupUrgencyFilter() {
        urgencyFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val u = when (checkedId) {
                R.id.filterUrgent -> Urgency.URGENT
                R.id.filterMedium -> Urgency.MEDIUM
                R.id.filterLow    -> Urgency.LOW
                else              -> Urgency.ALL
            }
            filterTasks(u)
        }
    }

    private fun filterTasks(urgency: Urgency) {
        val filtered = when (urgency) {
            Urgency.ALL    -> fullTaskList
            Urgency.URGENT -> fullTaskList.filter { it.priority == ServicePriority.HIGH }
            Urgency.MEDIUM -> fullTaskList.filter { it.priority == ServicePriority.MEDIUM }
            Urgency.LOW    -> fullTaskList.filter { it.priority == ServicePriority.LOW }
        }
        taskAdapter.updateList(filtered)
    }
}
