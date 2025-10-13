// app/src/main/java/com/example/citewise_mobile/reviews/ServiceReviewsActivity.kt
package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.adapters.ServiceReviewAdapter
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.RequestsPullWorker
import com.example.citewise_mobile.offline.ServiceRequestEntity
import com.example.citewise_mobile.offline.toServiceRequestDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ServiceReviewsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ALL = "all"
        const val MODE_RECENT = "recent"
        private const val RECENT_DAYS = 5L
    }

    private lateinit var btnBack: ImageButton
    private lateinit var title: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View

    private val items = mutableListOf<ServiceRequestDto>()
    private lateinit var adapter: ServiceReviewAdapter

    private var roomCollectJob: Job? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This layout should mirror your notifications-style header (no AppBar)
        setContentView(R.layout.activity_service_reviews)

        // Header wiring (no Toolbar)
        btnBack = findViewById(R.id.btnBack)
        title = findViewById(R.id.title)
        title.text = "View requests"
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        recycler = findViewById(R.id.recyclerReviews)
        emptyView = findViewById(R.id.emptyState)

        // Open the full-screen task details page on click
        adapter = ServiceReviewAdapter(items) { clicked ->
            startActivity(
                Intent(this, TaskDetailsActivity::class.java)
                    .putExtra(TaskDetailsActivity.EXTRA_REQUEST, clicked)
            )
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Offline-first: show Room immediately
        startCollectingRoom()
    }

    override fun onStart() {
        super.onStart()
        // Pull fresh data into Room after showing cached content
        RequestsPullWorker.oneShot(this)
    }

    override fun onStop() {
        super.onStop()
        roomCollectJob?.cancel()
    }

    private fun startCollectingRoom() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
        val local = LocalRepos(this)

        roomCollectJob?.cancel()
        roomCollectJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                local.requests.observeAll().collectLatest { entities ->
                    val list = when (mode) {
                        MODE_RECENT -> mapRecent(entities)
                        else -> mapAll(entities)
                    }

                    items.clear()
                    items.addAll(list)
                    adapter.notifyDataSetChanged()
                    emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
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
