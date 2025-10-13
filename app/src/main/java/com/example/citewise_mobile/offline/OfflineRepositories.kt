// app/src/main/java/com/example/citewise_mobile/offline/OfflineRepositories.kt
package com.example.citewise_mobile.offline

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Single access point to the local Room database. */
class LocalRepos(ctx: Context) {
    private val db = OfflineDb.get(ctx)
    val requests = db.requests()
    val users = db.users()
    val chats = db.chats()
    val messages = db.messages()
    val documents = db.documents()
}

/**
 * Bridges to cloud services that are NOT covered by Retrofit.
 * - Users: Firebase Realtime Database `/users`
 * - (Messages are handled via REST API workers; not here.)
 * - (Requests & documents are handled by Retrofit workers.)
 */
class CloudDataSources(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    fun myUid(): String? = auth.currentUser?.uid

    // -------- Users (Realtime DB) --------
    suspend fun fetchUsers(): List<UserEntity> = withContext(Dispatchers.IO) {
        val snap = rtdb.reference.child("users").get().await()
        snap.children.mapNotNull { c ->
            val uid = c.child("uid").getValue(String::class.java) ?: return@mapNotNull null
            val first = c.child("firstName").getValue(String::class.java) ?: ""
            val sur = c.child("surname").getValue(String::class.java) ?: ""
            val email = c.child("email").getValue(String::class.java) ?: ""
            val role = c.child("role").getValue(String::class.java) ?: ""
            val updatedAt = (
                    c.child("updatedAt").getValue(Long::class.java)
                        ?: c.child("createdAt").getValue(Long::class.java)
                        ?: 0L
                    )
            UserEntity(uid, first, sur, email, role, updatedAt)
        }
    }
}
