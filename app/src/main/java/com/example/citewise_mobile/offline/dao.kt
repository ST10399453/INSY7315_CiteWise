package com.example.citewise_mobile.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ============================================================
// Service Requests
// ============================================================

@Dao
interface ServiceRequestDao {

    // Use ABORT so conflicts surface during drafts; server merges go via upsertByRemoteId.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ServiceRequestEntity): Long

    @Update
    suspend fun update(entity: ServiceRequestEntity)

    @Query("SELECT * FROM service_requests ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ServiceRequestEntity>>

    @Query("SELECT * FROM service_requests ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ServiceRequestEntity>

    @Query("SELECT * FROM service_requests WHERE syncState = :state")
    suspend fun getBySyncState(state: SyncState): List<ServiceRequestEntity>

    @Query("SELECT * FROM service_requests WHERE syncState IN (:states) ORDER BY updatedAt ASC")
    suspend fun getBySyncStates(states: List<SyncState>): List<ServiceRequestEntity>

    @Query("SELECT * FROM service_requests WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): ServiceRequestEntity?

    @Query("SELECT * FROM service_requests WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): ServiceRequestEntity?

    @Query("SELECT * FROM service_requests WHERE documentId IS NOT NULL")
    suspend fun allWithDocumentId(): List<ServiceRequestEntity>

    /**
     * Reset FAILED rows older than a threshold back to PENDING_UPLOAD.
     * Useful for periodic automatic retries.
     */
    @Query("""
        UPDATE service_requests 
        SET syncState = :pending, updatedAt = :now
        WHERE syncState = :failed AND updatedAt <= :threshold
    """)
    suspend fun resetFailedOlderThan(
        threshold: Long,
        failed: SyncState = SyncState.FAILED,
        pending: SyncState = SyncState.PENDING_UPLOAD,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Bulk state flip (e.g., "Retry all failed").
     */
    @Query("""
        UPDATE service_requests 
        SET syncState = :toState, updatedAt = :now
        WHERE syncState = :fromState
    """)
    suspend fun bulkSetState(
        fromState: SyncState,
        toState: SyncState,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Upsert by remoteId while preserving important local fields.
     * Marks SYNCED and bumps updatedAt.
     */
    @Transaction
    suspend fun upsertByRemoteId(entity: ServiceRequestEntity) {
        val remoteId = entity.remoteId
        if (remoteId.isNullOrBlank()) {
            // Offline draft: no remote id yet
            insert(entity)
            return
        }

        val existing = findByRemoteId(remoteId)
        if (existing == null) {
            insert(entity.copy(syncState = SyncState.SYNCED, updatedAt = System.currentTimeMillis()))
        } else {
            update(
                existing.copy(
                    remoteId     = entity.remoteId,
                    userId       = entity.userId       ?: existing.userId,
                    consultantId = entity.consultantId ?: existing.consultantId,
                    status       = entity.status       ?: existing.status,
                    serviceType  = entity.serviceType,
                    quotationId  = entity.quotationId  ?: existing.quotationId,
                    description  = entity.description  ?: existing.description,
                    priority     = entity.priority     ?: existing.priority,
                    deadlineIso  = entity.deadlineIso  ?: existing.deadlineIso,
                    documentId   = entity.documentId   ?: existing.documentId,
                    filePath     = existing.filePath   ?: entity.filePath,
                    documentName = if (entity.documentName.isNotBlank()) entity.documentName else existing.documentName,
                    syncState    = SyncState.SYNCED,
                    updatedAt    = System.currentTimeMillis()
                )
            )
        }
    }
}

// ============================================================
// Users
// ============================================================

@Dao
interface UserDao {

    // Upsert avoids REPLACE side effects (requires Room 2.5+).
    @Upsert
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users ORDER BY firstName, surname")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getById(uid: String): UserEntity?
}

// ============================================================
// Chats
// ============================================================

@Dao
interface ChatDao {

    @Upsert
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>
}

// ============================================================
// Messages
// ============================================================

@Dao
interface MessageDao {

    @Upsert
    suspend fun upsertMessages(msgs: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timeSent ASC")
    fun observeChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE outbound = 1 AND synced = 0")
    suspend fun getPendingOutbound(): List<MessageEntity>

    @Query("SELECT MAX(timeSent) FROM messages WHERE recipientId = :recipientId")
    suspend fun maxInboundTs(recipientId: String): Long?
}

// ============================================================
// Documents
// ============================================================

@Dao
interface DocumentDao {

    @Upsert
    suspend fun upsertAll(docs: List<DocumentEntity>)

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("SELECT * FROM documents WHERE ownerUid = :uid ORDER BY updatedAt DESC")
    fun observeByOwner(uid: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE ownerUid = :uid")
    suspend fun getByOwner(uid: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY fileName COLLATE NOCASE ASC")
    suspend fun getAllAlpha(): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun getAllByDate(): List<DocumentEntity>

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ============================================================
// Admin Resources (separate table from Service Requests)
// ============================================================

@Dao
interface ResourceDao {

    // ABORT prevents silent data loss; server merges go via upsertByRemoteId.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ResourceEntity): Long

    @Update
    suspend fun update(entity: ResourceEntity)

    @Query("DELETE FROM resources WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    @Query("SELECT * FROM resources ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ResourceEntity>>

    @Query("SELECT * FROM resources ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE syncState = :state")
    suspend fun getBySyncState(state: SyncState): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE syncState IN (:states) ORDER BY updatedAt ASC")
    suspend fun getBySyncStates(states: List<SyncState>): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): ResourceEntity?

    @Query("SELECT * FROM resources WHERE documentId IS NOT NULL")
    suspend fun allWithDocumentId(): List<ResourceEntity>

    @Query("""
        UPDATE resources 
        SET syncState = :toState, updatedAt = :now
        WHERE syncState = :fromState
    """)
    suspend fun bulkSetState(
        fromState: SyncState,
        toState: SyncState,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE resources
        SET syncState = :pending, updatedAt = :now
        WHERE syncState = :failed AND updatedAt <= :threshold
    """)
    suspend fun resetFailedOlderThan(
        threshold: Long,
        failed: SyncState = SyncState.FAILED,
        pending: SyncState = SyncState.PENDING_UPLOAD,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Upsert by remoteId while preserving staged file info and marking SYNCED.
     * Also persists 'faculty'.
     */
    @Transaction
    suspend fun upsertByRemoteId(entity: ResourceEntity) {
        val remoteId = entity.remoteId
        if (remoteId.isNullOrBlank()) {
            // Offline draft without remote id yet
            insert(entity)
            return
        }

        val existing = findByRemoteId(remoteId)
        if (existing == null) {
            insert(
                entity.copy(
                    syncState = SyncState.SYNCED,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            update(
                existing.copy(
                    title       = entity.title,
                    description = entity.description ?: existing.description,
                    category    = entity.category    ?: existing.category,
                    faculty     = entity.faculty     ?: existing.faculty,
                    documentId  = entity.documentId  ?: existing.documentId,
                    fileName    = entity.fileName    ?: existing.fileName,
                    filePath    = existing.filePath  ?: entity.filePath, // keep staged path if present
                    remoteId    = entity.remoteId ?: existing.remoteId,
                    syncState   = SyncState.SYNCED,
                    updatedAt   = System.currentTimeMillis()
                )
            )
        }
    }
}
