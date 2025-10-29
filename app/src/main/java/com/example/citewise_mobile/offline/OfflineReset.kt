package com.example.citewise_mobile.offline

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fully clears local offline data:
 * - Cancels all WorkManager background jobs
 * - Clears Firestore local cache (if used)
 * - Closes & deletes Room database file: offline.db
 */
object OfflineReset {

    /**
     * Call this when you want to nuke all local data (e.g., logout, debug button, after wiping Firebase).
     * Must be called before Firestore or Room are re-initialized in this process.
     */
    fun resetLocalData(context: Context) {
        // Cancel all background work
        WorkManager.getInstance(context).cancelAllWork()

        // Clear persistence (if enabled in your app)
        runCatching {
            FirebaseFirestore.getInstance().clearPersistence()
        }.onFailure { it.printStackTrace() }

        // Close & drop Room DB
        runCatching { LocalRepos.closeAll() }
        context.deleteDatabase("offline.db")
    }
}
