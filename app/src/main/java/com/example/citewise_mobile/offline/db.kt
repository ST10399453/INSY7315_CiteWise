package com.example.citewise_mobile.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

//https://piashcse.medium.com/room-database-in-jetpack-compose-a-step-by-step-guide-for-android-development-6c7ae419105a

@Database(
    entities = [
        ServiceRequestEntity::class,
        UserEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        DocumentEntity::class,
        ResourceEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class OfflineDb : RoomDatabase() {

    abstract fun requests(): ServiceRequestDao
    abstract fun users(): UserDao
    abstract fun chats(): ChatDao
    abstract fun messages(): MessageDao
    abstract fun documents(): DocumentDao
    abstract fun resources(): ResourceDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDb? = null
        private const val DB_NAME = "offline.db"

        fun get(context: Context): OfflineDb {
            // Double-checked locking for a single process-wide instance
            val cached = INSTANCE
            if (cached != null) return cached

            return synchronized(this) {
                val again = INSTANCE
                if (again != null) again
                else Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDb::class.java,
                    DB_NAME
                )
                    // NOTE: Use proper Migration objects for production to avoid data loss.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Close the DB and drop the cached instance.
         * Call this before deleting the DB file (e.g., during a full local reset).
         */
        fun closeAndClear() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
