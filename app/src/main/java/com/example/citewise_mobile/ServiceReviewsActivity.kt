////app/src/main/java/com/example/citewise_mobile/reviews/ServiceReviewsActivity.kt
//package com.example.citewise_mobile
//
//import android.content.Intent
//import android.os.Bundle
//import android.view.View
//import android.widget.ImageButton
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.lifecycleScope
//import androidx.lifecycle.repeatOnLifecycle
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.example.citewise_mobile.adapters.ServiceReviewAdapter
//import com.example.citewise_mobile.api.ServiceRequestDto
//import com.example.citewise_mobile.offline.LocalRepos
//import com.example.citewise_mobile.offline.RequestsPullWorker
//import com.example.citewise_mobile.offline.ServiceRequestEntity
//import com.example.citewise_mobile.offline.toServiceRequestDto
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.flow.collectLatest
//import kotlinx.coroutines.launch
//import java.util.concurrent.TimeUnit
//
//class ServiceReviewsActivity : AppCompatActivity() {
//
//    companion object {
//        const val EXTRA_MODE = "mode"
//        const val MODE_ALL = "all"
//        const val MODE_RECENT = "recent"
//        private const val RECENT_DAYS = 5L
//    }
//
//    private lateinit var btnBack: ImageView
//    private lateinit var title: TextView
//    private lateinit var recycler: RecyclerView
//    private lateinit var emptyView: View
//
//    private val items = mutableListOf<ServiceRequestDto>()
//    private lateinit var adapter: ServiceReviewAdapter
//
//    private var roomCollectJob: Job? = null
//
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // This layout should mirror your notifications-style header (no AppBar)
//        setContentView(R.layout.activity_service_reviews)
//
//        // Header wiring (no Toolbar)
//        btnBack = findViewById(R.id.btnBack)
//        title = findViewById(R.id.title)
//        title.text = "Active Tasks"
//        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
//
//        recycler = findViewById(R.id.recyclerReviews)
//        emptyView = findViewById(R.id.emptyState)
//
//        // Open the full-screen task details page on click
//        adapter = ServiceReviewAdapter(items) { clicked ->
//            startActivity(
//                Intent(this, TaskDetailsActivity::class.java)
//                    .putExtra(TaskDetailsActivity.EXTRA_REQUEST, clicked)
//            )
//        }
//
//        recycler.layoutManager = LinearLayoutManager(this)
//        recycler.adapter = adapter
//
//        // Offline-first: show Room immediately
//        startCollectingRoom()
//    }
//
//    override fun onStart() {
//        super.onStart()
//        // Pull fresh data into Room after showing cached content
//        RequestsPullWorker.oneShot(this)
//    }
//
//    override fun onStop() {
//        super.onStop()
//        roomCollectJob?.cancel()
//    }
//
//    private fun startCollectingRoom() {
//        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
//        val local = LocalRepos(this)
//
//        roomCollectJob?.cancel()
//        roomCollectJob = lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                local.requests.observeAll().collectLatest { entities ->
//                    val list = when (mode) {
//                        MODE_RECENT -> mapRecent(entities)
//                        else -> mapAll(entities)
//                    }
//
//                    items.clear()
//                    items.addAll(list)
//                    adapter.notifyDataSetChanged()
//                    emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
//                }
//            }
//        }
//    }
//
//    private fun mapAll(entities: List<ServiceRequestEntity>): List<ServiceRequestDto> =
//        entities.sortedByDescending { it.updatedAt }
//            .map { it.toServiceRequestDto() }
//
//    private fun mapRecent(entities: List<ServiceRequestEntity>): List<ServiceRequestDto> {
//        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RECENT_DAYS)
//        return entities
//            .filter { it.updatedAt >= cutoff }
//            .sortedByDescending { it.updatedAt }
//            .take(5)
//            .map { it.toServiceRequestDto() }
//    }
//}
package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ServiceReviewAdapter
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.RequestsPullWorker
import com.example.citewise_mobile.offline.ServiceRequestEntity
import com.example.citewise_mobile.offline.toServiceRequestDto
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ServiceReviewsActivity : AppCompatActivity() {

    enum class Urgency { ALL, URGENT, MEDIUM, LOW }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ALL = "all"
        const val MODE_RECENT = "recent"
        private const val RECENT_DAYS = 5L
    }

    private lateinit var btnBack: ImageView
    private lateinit var title: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var urgencyFilterGroup: MaterialButtonToggleGroup

    private val items = mutableListOf<ServiceRequestDto>()
    private var fullList: List<ServiceRequestDto> = emptyList()
    private lateinit var adapter: ServiceReviewAdapter
    private var roomCollectJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_reviews)

        // Header setup
        btnBack = findViewById(R.id.btnBack)
        title = findViewById(R.id.title)
        title.text = "Active Tasks"
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        recycler = findViewById(R.id.recyclerReviews)
        emptyView = findViewById(R.id.emptyState)
        urgencyFilterGroup = findViewById(R.id.urgencyFilterGroup)

        adapter = ServiceReviewAdapter(items) { clicked ->
            startActivity(
                Intent(this, TaskDetailsActivity::class.java)
                    .putExtra(TaskDetailsActivity.EXTRA_REQUEST, clicked)
            )
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        urgencyFilterGroup.check(R.id.filterAll)
        setupUrgencyFilter()

        startCollectingRoom()
    }

    override fun onStart() {
        super.onStart()
        RequestsPullWorker.oneShot(this)
    }

    override fun onStop() {
        super.onStop()
        roomCollectJob?.cancel()
    }

    private fun startCollectingRoom() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
        val local = LocalRepos(this)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        roomCollectJob?.cancel()
        roomCollectJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                local.requests.observeAll().collectLatest { entities ->
                    // ✅ Filter by userId before mapping
                    val filtered = if (uid != null)
                        entities.filter { it.userId == uid }
                    else
                        emptyList()

                    fullList = when (mode) {
                        MODE_RECENT -> mapRecent(filtered)
                        else -> mapAll(filtered)
                    }

                    // Update UI
                    filterTasks(Urgency.ALL)
                }
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
        val filtered = when (urgency) {
            Urgency.ALL -> fullList
            Urgency.URGENT -> fullList.filter { it.priority == ServicePriority.HIGH }
            Urgency.MEDIUM -> fullList.filter { it.priority == ServicePriority.MEDIUM }
            Urgency.LOW -> fullList.filter { it.priority == ServicePriority.LOW }
        }

        items.clear()
        items.addAll(filtered)
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun mapAll(entities: List<ServiceRequestEntity>): List<ServiceRequestDto> =
        entities.sortedByDescending { it.updatedAt }
            .map { it.toServiceRequestDto() }

    private fun mapRecent(entities: List<ServiceRequestEntity>): List<ServiceRequestDto> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RECENT_DAYS)
        return entities
            .filter { it.updatedAt >= cutoff }
            .sortedByDescending { it.updatedAt }
            .take(5)
            .map { it.toServiceRequestDto() }
    }
}
