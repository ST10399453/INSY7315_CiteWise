package com.example.citewise_mobile.offline

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Single access point to the local Room database. */
/** Single access point to the local Room database. */
class LocalRepos(ctx: Context) {
    // Use a shared singleton instance so we can close it centrally
    private val db = getDb(ctx)

    val requests = db.requests()
    val users = db.users()
    val chats = db.chats()
    val messages = db.messages()
    val documents = db.documents()

    companion object {
        @Volatile
        private var dbInstance: OfflineDb? = null

        private fun getDb(ctx: Context): OfflineDb {
            val cached = dbInstance
            if (cached != null) return cached
            return synchronized(this) {
                val again = dbInstance
                if (again != null) again
                else {
                    // Use your existing Room builder inside OfflineDb.get(ctx)
                    val created = OfflineDb.get(ctx)
                    dbInstance = created
                    created
                }
            }
        }

        /**
         * Closes the shared Room database instance and releases it.
         * Call this before deleting the DB file (e.g., in OfflineReset.resetLocalData).
         */
        fun closeAll() {
            synchronized(this) {
                dbInstance?.close()
                dbInstance = null
            }
        }
    }
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
