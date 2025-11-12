////package com.example.citewise_mobile.reviews
////
////import android.os.Bundle
////import androidx.appcompat.app.AppCompatActivity
////import androidx.lifecycle.lifecycleScope
////import androidx.recyclerview.widget.LinearLayoutManager
////import androidx.recyclerview.widget.RecyclerView
////import com.example.citewise_mobile.R
////import com.example.citewise_mobile.api.ServiceRequestDto
////import com.example.citewise_mobile.offline.LocalRepos
////import com.example.citewise_mobile.offline.ServiceRequestEntity
////import com.example.citewise_mobile.offline.toServiceRequestDto
////import com.google.firebase.auth.FirebaseAuth
////import com.google.firebase.database.FirebaseDatabase
////import kotlinx.coroutines.flow.collectLatest
////import kotlinx.coroutines.launch
////import kotlinx.coroutines.tasks.await
////
////class RateConsultantListActivity : AppCompatActivity() {
////
////    private lateinit var rvRequests: RecyclerView
////    private val items = mutableListOf<ServiceRequestDto>()
////    private val local by lazy { LocalRepos(this) }
////    private lateinit var adapter: RateConsultantAdapter
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        setContentView(R.layout.activity_rate_consultant_list)
////
////        rvRequests = findViewById(R.id.recyclerViewRequests)
////        rvRequests.layoutManager = LinearLayoutManager(this)
////        adapter = RateConsultantAdapter(items)
////        rvRequests.adapter = adapter
////
////        loadStudentRequests()
////    }
////
////    private fun loadStudentRequests() {
////        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
////
////        lifecycleScope.launch {
////            local.requests.observeAll().collectLatest { entities ->
////                val studentEntities = entities
////                    .filter { it.userId == uid } // only this student
////                    .filter { !it.consultantId.isNullOrBlank() } // only requests with consultant assigned
////
////                val list = studentEntities.map { entity ->
////                    val dto = entity.toServiceRequestDto()
////                    // fetch RTDB consultant name
////                    val consultantName = dto.consultantId?.let { fetchConsultantName(it) } ?: "Unassigned"
////                    dto.copy(studentName = consultantName)
////                }
////
////                items.clear()
////                items.addAll(list)
////                adapter.notifyDataSetChanged()
////            }
////        }
////    }
////
////
////    private suspend fun fetchConsultantName(consultantId: String): String? {
////        val node = FirebaseDatabase.getInstance().reference.child("users").child(consultantId).get()
////            .await()
////        if (!node.exists()) return null
////        val first = node.child("firstName").getValue(String::class.java).orEmpty()
////        val sur = node.child("surname").getValue(String::class.java).orEmpty()
////        return if (first.isNotBlank() || sur.isNotBlank()) "$first $sur" else node.child("name")
////            .getValue(String::class.java).orEmpty()
////    }
////}
//
//package com.example.citewise_mobile.reviews
//
//import android.os.Bundle
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.example.citewise_mobile.R
//import com.example.citewise_mobile.adapters.RateConsultantAdapter
//import com.example.citewise_mobile.api.ServiceRequestDto
//import com.example.citewise_mobile.offline.LocalRepos
//import com.example.citewise_mobile.offline.toServiceRequestDto
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.database.FirebaseDatabase
//import kotlinx.coroutines.flow.collectLatest
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
//
//class RateConsultantListActivity : AppCompatActivity() {
//
//    private lateinit var rvRequests: RecyclerView
//    private val items = mutableListOf<ServiceRequestDto>()
//    private val local by lazy { LocalRepos(this) }
//    private lateinit var adapter: RateConsultantAdapter
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_rate_consultant_list)
//
//        rvRequests = findViewById(R.id.recyclerViewRequests)
//        rvRequests.layoutManager = LinearLayoutManager(this)
//
//        adapter = RateConsultantAdapter(items) { consultantId, rating ->
//            saveConsultantRating(consultantId, rating)
//        }
//
//        rvRequests.adapter = adapter
//        loadStudentRequests()
//    }
//
//    private fun loadStudentRequests() {
//        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
//
//        lifecycleScope.launch {
//            local.requests.observeAll().collectLatest { entities ->
//                val studentEntities = entities
//                    .filter { it.userId == uid }
//                    .filter { !it.consultantId.isNullOrBlank() }
//
//                val list = studentEntities.map { entity ->
//                    val dto = entity.toServiceRequestDto()
//                    val consultantName = dto.consultantId?.let { fetchConsultantName(it) } ?: "Unassigned"
//                    dto.copy(studentName = consultantName)
//                }
//
//                items.clear()
//                items.addAll(list)
//                adapter.notifyDataSetChanged()
//            }
//        }
//    }
//
//    private fun saveConsultantRating(consultantId: String, rating: Float, position: Int) {
//        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
//        val ref = FirebaseDatabase.getInstance()
//            .getReference("users")
//            .child(consultantId)
//            .child("ratings")
//            .child(uid) // key by student ID
//
//        ref.setValue(rating)
//            .addOnSuccessListener {
//                Toast.makeText(this, "Rating saved!", Toast.LENGTH_SHORT).show()
//                // Remove from list so user cannot rate again
//                items.removeAt(position)
//                adapter.notifyItemRemoved(position)
//            }
//            .addOnFailureListener { e ->
//                Toast.makeText(this, "Failed to save rating: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//    }
//
//
//
//    private suspend fun fetchConsultantName(consultantId: String): String? {
//        val node = FirebaseDatabase.getInstance().reference.child("users").child(consultantId).get()
//            .await()
//        if (!node.exists()) return null
//        val first = node.child("firstName").getValue(String::class.java).orEmpty()
//        val sur = node.child("surname").getValue(String::class.java).orEmpty()
//        return if (first.isNotBlank() || sur.isNotBlank()) "$first $sur" else node.child("name")
//            .getValue(String::class.java).orEmpty()
//    }
//}
package com.example.citewise_mobile.reviews

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.toServiceRequestDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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
        adapter = RateConsultantAdapter(items, this)
        rvRequests.adapter = adapter

        loadStudentRequests()
    }

    private fun loadStudentRequests() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            local.requests.observeAll().collectLatest { entities ->
                val studentEntities = entities
                    .filter { it.userId == uid } // only this student
                    .filter { !it.consultantId.isNullOrBlank() } // only requests with consultant assigned

                val list = studentEntities.map { entity ->
                    val dto = entity.toServiceRequestDto()
                    // fetch RTDB consultant name
                    val consultantName = dto.consultantId?.let { fetchConsultantName(it) } ?: "Unassigned"
                    dto.copy(studentName = consultantName)
                }

                items.clear()
                items.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }

    suspend fun fetchConsultantName(consultantId: String): String? {
        val node = FirebaseDatabase.getInstance().reference.child("users").child(consultantId).get().await()
        if (!node.exists()) return null
        val first = node.child("firstName").getValue(String::class.java).orEmpty()
        val sur = node.child("surname").getValue(String::class.java).orEmpty()
        return if (first.isNotBlank() || sur.isNotBlank()) "$first $sur" else node.child("name").getValue(String::class.java).orEmpty()
    }

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
                // Remove from list
                items.removeAt(position)
                adapter.notifyItemRemoved(position)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save rating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
