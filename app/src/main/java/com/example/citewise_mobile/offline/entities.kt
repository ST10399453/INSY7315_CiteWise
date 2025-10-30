package com.example.citewise_mobile.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncState { PENDING_UPLOAD, SYNCED, FAILED }

@Entity(
    tableName = "service_requests",
    indices = [
        Index("updatedAt"),
        Index("syncState"),
        Index("remoteId") // fast lookup when merging pulls
    ]
)
data class ServiceRequestEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val consultantId: String? = null,
    val serviceType: String,
    //val title: String? = null,
    val quotationId: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val status: String? = null,
    val documentId: String? = null,
    val documentName: String = "",
    val customName: String = "",
    val filePath: String? = null,
    val deadlineIso: String? = null,
    val feedback: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users", indices = [Index("updatedAt")])
data class UserEntity(
    @PrimaryKey val uid: String,
    val firstName: String,
    val surname: String,
    val email: String,
    val role: String,
    val updatedAt: Long
)

@Entity(tableName = "chats", indices = [Index("updatedAt")])
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val senderId: String,
    val recipientId: String,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    indices = [Index("chatId"), Index("senderId"), Index("recipientId"), Index("timeSent"), Index("synced")]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val timeSent: Long,
    val outbound: Boolean,
    val inbound: Boolean,
    val synced: Boolean = false
)

@Entity(
    tableName = "documents",
    indices = [Index("ownerUid"), Index("updatedAt")]
)
data class DocumentEntity(
    @PrimaryKey val id: String,         // server doc id
    val ownerUid: String,               // firebase uid if available
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val updatedAt: Long,
    val etag: String?,
    val remoteUrlHint: String?,
    val localPath: String?,
    val downloadedAt: Long?
)
