package com.example.citewise_mobile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.adapters.RateConsultantAdapter
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.toServiceRequestDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RateConsultantListActivity : AppCompatActivity() {

    private lateinit var rvRequests: RecyclerView
    private val items = mutableListOf<ServiceRequestDto>()
    private val local by lazy { LocalRepos(this) }
    private lateinit var adapter: RateConsultantAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_consultant_list)

        rvRequests = findViewById(R.id.recyclerViewRequests)
        rvRequests.layoutManager = LinearLayoutManager(this)

        // Adapter is expected to call back into saveConsultantRating(...)
        adapter = RateConsultantAdapter(items, this)
        rvRequests.adapter = adapter

        loadStudentRequests()
    }

    /** Load all requests for this student that have an assigned consultant. */
    private fun loadStudentRequests() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            local.requests.observeAll().collectLatest { entities ->
                val studentEntities = entities
                    .filter { it.userId == uid }                 // only this student
                    .filter { !it.consultantId.isNullOrBlank() } // must have consultant

                val list = mutableListOf<ServiceRequestDto>()
                for (entity in studentEntities) {
                    val dto = entity.toServiceRequestDto()
                    val consultantName = dto.consultantId
                        ?.let { consultantId ->
                            fetchConsultantName(consultantId)
                        }
                        ?: "Unassigned"

                    list.add(dto.copy(studentName = consultantName))
                }

                items.clear()
                items.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }

    /** Get consultant's display name from RTDB. */
    suspend fun fetchConsultantName(consultantId: String): String? {
        val node = FirebaseDatabase.getInstance().reference
            .child("users")
            .child(consultantId)
            .get()
            .await()

        if (!node.exists()) return null

        val first = node.child("firstName").getValue(String::class.java).orEmpty()
        val sur   = node.child("surname").getValue(String::class.java).orEmpty()

        return if (first.isNotBlank() || sur.isNotBlank()) {
            "$first $sur"
        } else {
            node.child("name").getValue(String::class.java).orEmpty()
        }
    }

    /**
     * Save a student's rating for a consultant in RTDB,
     * then recompute and update the consultant's entry in Firestore TopConsultants.
     */
    fun saveConsultantRating(consultantId: String, rating: Float, position: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(consultantId)
            .child("ratings")
            .child(uid) // one rating per student

        ref.setValue(rating)
            .addOnSuccessListener {
                Toast.makeText(this, "Rating saved!", Toast.LENGTH_SHORT).show()

                // Remove from list so user cannot rate again
                if (position in items.indices) {
                    items.removeAt(position)
                    adapter.notifyItemRemoved(position)
                }

                // Recalculate and update TopConsultants entry
                lifecycleScope.launch {
                    try {
                        updateTopConsultantEntry(consultantId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to save rating: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /**
     * Recalculate average rating for this consultant (from RTDB /users/{id}/ratings)
     * and push/update to Firestore collection TopConsultants.
     */
    private suspend fun updateTopConsultantEntry(consultantId: String) {
        val dbRoot = FirebaseDatabase.getInstance().reference
        val consultantNode = dbRoot.child("users").child(consultantId)

        // 1) Get all ratings for this consultant
        val ratingsSnap = consultantNode.child("ratings").get().await()
        var sum = 0f
        var count = 0

        for (child in ratingsSnap.children) {
            val value = child.getValue(Float::class.java) ?: continue
            sum += value
            count++
        }

        if (count == 0) {
            // No ratings left; you could optionally remove from TopConsultants:
            // FirebaseFirestore.getInstance().collection("TopConsultants")
            //     .document(consultantId)
            //     .delete()
            //     .await()
            return
        }

        val avg = sum / count.toFloat()

        // 2) Get consultant name for display
        val name = fetchConsultantName(consultantId) ?: "Unknown"

        // 3) Store/update in Firestore TopConsultants
        val doc = hashMapOf(
            "name"   to name,
            "rating" to avg
        )

        FirebaseFirestore.getInstance()
            .collection("TopConsultants")
            .document(consultantId)
            .set(doc)
            .await()
    }
}
