package com.example.citewise_mobile.offline

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Central access point to Room DAOs.
 * Holds a single process-wide DB instance and exposes typed DAO handles.
 */
class LocalRepos(ctx: Context) {

    // Backed by the process-wide singleton instance.
    private val db: OfflineDb = getDb(ctx)

    val requests  = db.requests()
    val users     = db.users()
    val chats     = db.chats()
    val messages  = db.messages()
    val documents = db.documents()
    val resources = db.resources()

    companion object {
        @Volatile
        private var dbInstance: OfflineDb? = null

        /**
         * Obtain (or create) the process-wide OfflineDb instance.
         */
        private fun getDb(ctx: Context): OfflineDb {
            // Fast-path read
            dbInstance?.let { return it }

            // Double-checked locking
            return synchronized(this) {
                dbInstance ?: OfflineDb.get(ctx).also { created ->
                    dbInstance = created
                }
            }
        }

        /**
         * Closes and clears the cached DB instance.
         * Call this BEFORE deleting the DB file (e.g., during a full local reset).
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
 * Cloud data sources that are not covered by your Retrofit layer.
 * - Users are stored in Firestore (/users) and optionally mirrored in Realtime DB.
 * - Messages/Requests/Documents are handled elsewhere (e.g., workers & repositories).
 */
class CloudDataSources(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val fs: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun myUid(): String? = auth.currentUser?.uid

    /**
     * Preferred source of truth: Firestore `/users` collection.
     * - Falls back to document id for uid if the "uid" field is missing.
     * - Normalizes role to lowercase.
     * - Derives updatedAt from supported fields (Timestamp/Number/Date/ISO/numeric String).
     */
    suspend fun fetchUsers(): List<UserEntity> = withContext(Dispatchers.IO) {
        val snap = fs.collection("users").get().await()
        snap.documents.mapNotNull { d ->
            val uid = (d.getString("uid") ?: d.id).takeIf { it.isNotBlank() } ?: return@mapNotNull null

            // Prefer explicit first/surname; fall back to a single "name"
            val first   = (d.getString("firstName") ?: d.getString("firstname") ?: "").trim()
            val sur     = (d.getString("surname")   ?: d.getString("lastName")  ?: "").trim()
            val nameRaw = (d.getString("name") ?: "").trim()
            val (firstName, surname) =
                if (first.isNotEmpty() || sur.isNotEmpty()) {
                    first to sur
                } else if (nameRaw.contains(" ")) {
                    val parts = nameRaw.split(Regex("\\s+"), limit = 2)
                    (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
                } else {
                    nameRaw to ""
                }

            val email = d.getString("email") ?: ""
            val role  = (d.getString("role") ?: "").trim().lowercase()

            // Robust updatedAt (accept Timestamp/Number/Date/ISO string/numeric string)
            val updatedAt = d.readMillis("updatedAt").takeIf { it > 0 }
                ?: d.readMillis("createdAt")

            UserEntity(
                uid        = uid,
                firstName  = firstName,
                surname    = surname,
                email      = email,
                role       = role,
                updatedAt  = updatedAt
            )
        }
    }

    /**
     * Legacy/backup: Realtime Database `/users` node.
     * - Still normalizes role.
     * - Supports missing "uid" by using the node key.
     */
    suspend fun fetchUsersFromRtdb(): List<UserEntity> = withContext(Dispatchers.IO) {
        val snap = rtdb.reference.child("users").get().await()
        snap.children.mapNotNull { c ->
            val uid = c.child("uid").getValue(String::class.java) ?: c.key ?: return@mapNotNull null
            val first = c.child("firstName").getValue(String::class.java) ?: ""
            val sur   = c.child("surname").getValue(String::class.java)   ?: ""
            val email = c.child("email").getValue(String::class.java)     ?: ""
            val role  = (c.child("role").getValue(String::class.java) ?: "").trim().lowercase()
            val updatedAt =
                c.child("updatedAt").getValue(Long::class.java)
                    ?: c.child("createdAt").getValue(Long::class.java)
                    ?: 0L
            UserEntity(uid, first, sur, email, role, updatedAt)
        }
    }

    /**
     * Optional: merge Firestore + RTDB users and keep the newest per uid.
     */
    suspend fun fetchAllUsersMerged(): List<UserEntity> = withContext(Dispatchers.IO) {
        val fsUsers   = fetchUsers()
        val rtdbUsers = fetchUsersFromRtdb()
        (fsUsers + rtdbUsers)
            .groupBy { it.uid }
            .map { (_, list) -> list.maxByOrNull { it.updatedAt }!! }
    }

    /**
     * Example helper if you need consultants referenced by ServiceReviews (Firestore).
     */
    suspend fun fetchConsultantsAssignedInServiceReviews(): List<UserEntity> = withContext(Dispatchers.IO) {
        val reviews = fs.collection("ServiceReviews")
            .whereNotEqualTo("consultantUid", null)
            .get()
            .await()

        val uids = buildSet {
            for (d in reviews.documents) {
                d.getString("consultantUid")?.takeIf { it.isNotBlank() }?.let(::add)
                (d.get("consultant") as? DocumentReference)?.id?.let(::add)
            }
        }
        if (uids.isEmpty()) return@withContext emptyList()
        fetchUsers().filter { it.uid in uids }
    }
}

/* -------------------- helpers: robust timestamp parsing -------------------- */

private fun DocumentSnapshot.readMillis(field: String): Long {
    val v = get(field) ?: return 0L
    return when (v) {
        is Number     -> v.toLong()
        is Timestamp  -> v.toDate().time
        is Date       -> v.time
        is String     -> parseIsoToMillis(v) ?: v.toLongOrNull() ?: 0L
        else          -> 0L
    }
}

private fun parseIsoToMillis(s: String): Long? = try {
    java.time.Instant.parse(s).toEpochMilli()
} catch (_: Throwable) {
    // Add more patterns here if you store "2025-11-10 12:34:56" etc.
    null
}
