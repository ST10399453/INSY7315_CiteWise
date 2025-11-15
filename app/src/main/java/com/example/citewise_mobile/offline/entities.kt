package com.example.citewise_mobile.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//https://piashcse.medium.com/room-database-in-jetpack-compose-a-step-by-step-guide-for-android-development-6c7ae419105a

/**
 * Indicates how far a record has synced with the backend.
 */
enum class SyncState {
    PENDING_UPLOAD,
    SYNCED,
    FAILED
}

// ============================================================
// Service Requests (Students & Consultants)
// ============================================================

@Entity(
    tableName = "service_requests",
    indices = [
        Index("updatedAt"),
        Index("syncState"),
        Index("remoteId")
    ]
)
data class ServiceRequestEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val consultantId: String? = null,

    val serviceType: String,

    // quotation linkage + NEW details
    val quotationId: String? = null,
    val quotationWords: Int? = null,
    val quotationAmount: Double? = null,
    val quotationCurrency: String? = null,

    val description: String? = null,
    val priority: String? = null,
    val status: String? = null,

    val documentId: String? = null,
    val documentName: String = "",
    val customName: String = "",
    val filePath: String? = null,
    val deadlineIso: String? = null,

    val feedback: String? = null,

    // also store feedback file meta for quick UI binding
    val feedbackFileName: String? = null,
    val feedbackFileUrl: String? = null,

    val syncState: SyncState = SyncState.PENDING_UPLOAD,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
// ============================================================
// Users (All Roles)
// ============================================================

@Entity(
    tableName = "users",
    indices = [Index("updatedAt")]
)
data class UserEntity(
    @PrimaryKey val uid: String,
    val firstName: String,
    val surname: String,
    val email: String,
    val role: String,           // "student", "consultant", "admin"
    val updatedAt: Long
)

// ============================================================
// Chats (1:1 Chat Sessions)
// ============================================================

@Entity(
    tableName = "chats",
    indices = [Index("updatedAt")]
)
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val senderId: String,
    val recipientId: String,
    val updatedAt: Long
)

// ============================================================
// Messages (Individual Chat Messages)
// ============================================================

@Entity(
    tableName = "messages",
    indices = [
        Index("chatId"),
        Index("senderId"),
        Index("recipientId"),
        Index("timeSent"),
        Index("synced")
    ]
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

// ============================================================
// Documents (Any Uploaded File Stored on Server)
// ============================================================

@Entity(
    tableName = "documents",
    indices = [Index("ownerUid"), Index("updatedAt")]
)
data class DocumentEntity(
    @PrimaryKey val id: String,             // server doc id
    val ownerUid: String,                   // Firebase UID of the owner
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val updatedAt: Long,
    val etag: String?,                      // for sync comparisons
    val remoteUrlHint: String?,
    val localPath: String?,
    val downloadedAt: Long?
)

// ============================================================
// Resources (Admin-Uploaded Shared Materials)
//  - Added `faculty` so workers/UI can send & display it.
// ============================================================

@Entity(
    tableName = "resources",
    indices = [
        Index("updatedAt"),
        Index("remoteId"),
        Index("faculty")
    ]
)
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,           // server resource id
    val adminUid: String? = null,           // uploader
    val title: String,
    val description: String? = null,
    val category: String? = null,           // "TEMPLATE", "WRITING_GUIDE", etc.
    val faculty: String? = null,
    val documentId: String? = null,
    val fileName: String? = null,
    val filePath: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)