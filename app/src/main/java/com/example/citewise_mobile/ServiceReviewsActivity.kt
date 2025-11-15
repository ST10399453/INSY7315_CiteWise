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

    private lateinit var emptyAll: TextView
    private lateinit var emptyUrgent: TextView
    private lateinit var emptyMedium: TextView
    private lateinit var emptyLow: TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_reviews)

        // Header setup
        btnBack = findViewById(R.id.btnBack)
        title = findViewById(R.id.title)
        title.text = "Active Tasks"
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        recycler = findViewById(R.id.recyclerReviews)
//        emptyView = findViewById(R.id.emptyState)
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

        emptyAll = findViewById(R.id.emptyAll)
        emptyUrgent = findViewById(R.id.emptyUrgent)
        emptyMedium = findViewById(R.id.emptyMedium)
        emptyLow = findViewById(R.id.emptyLow)


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

        // Hide all first
        emptyAll.visibility = View.GONE
        emptyUrgent.visibility = View.GONE
        emptyMedium.visibility = View.GONE
        emptyLow.visibility = View.GONE

        // Show the correct one if list is empty
        if (items.isEmpty()) {
            when (urgency) {
                Urgency.ALL -> emptyAll.visibility = View.VISIBLE
                Urgency.URGENT -> emptyUrgent.visibility = View.VISIBLE
                Urgency.MEDIUM -> emptyMedium.visibility = View.VISIBLE
                Urgency.LOW -> emptyLow.visibility = View.VISIBLE
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

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 *
 * Android Developer. 2024. “Grant Partial Access to Photos and Videos”.
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Knowledge. 2023a. “Bottom Navigation Bar in Android Studio Using Java | Explanation”.
 * https://www.youtube.com/watch?v=0x5kmLY16qE
 * [accessed 20 September 2023].
 *
 * Android Knowledge. 2023b. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
 * https://www.youtube.com/watch?v=oGyQMBKPuNY
 * [accessed 21 September 2025].
 *
 * Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
 * https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494
 * [accessed 30 September 2025].
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2017. “How to Use Glide Image Loader Library in Android Apps?”
 * https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/
 * [accessed 30 September 2025].
 *
 * GeeksforGeeks. 2020. “SimpleAdapter in Android with Example”.
 * https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/
 * [accessed 19 August 2025].
 *
 * GeeksforGeeks. 2021. “State ProgressBar in Android”.
 * https://www.geeksforgeeks.org/android/state-progressbar-in-android/
 * [accessed 22 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”.
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */

