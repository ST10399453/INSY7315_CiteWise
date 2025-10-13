// app/src/main/java/com/example/citewise_mobile/offline/db.kt
package com.example.citewise_mobile.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServiceRequestEntity::class,
        UserEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        DocumentEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OfflineDb : RoomDatabase() {
    abstract fun requests(): ServiceRequestDao
    abstract fun users(): UserDao
    abstract fun chats(): ChatDao
    abstract fun messages(): MessageDao
    abstract fun documents(): DocumentDao

    companion object {
        @Volatile private var INSTANCE: OfflineDb? = null

        fun get(ctx: Context): OfflineDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    OfflineDb::class.java,
                    "offline.db"
                )
                    .fallbackToDestructiveMigration() // Change to proper Migrations for prod
                    .build().also { INSTANCE = it }
            }
    }
}
