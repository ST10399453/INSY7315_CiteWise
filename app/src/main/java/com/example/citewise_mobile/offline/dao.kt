package com.example.citewise_mobile.offline

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ----- Service Requests -----

@Dao
interface ServiceRequestDao {
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

    @Query("SELECT * FROM service_requests WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): ServiceRequestEntity?

    @Query("SELECT * FROM service_requests WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): ServiceRequestEntity?

    @Query("SELECT * FROM service_requests WHERE documentId IS NOT NULL")
    suspend fun allWithDocumentId(): List<ServiceRequestEntity>

    @Transaction
    suspend fun upsertByRemoteId(entity: ServiceRequestEntity) {
        if (entity.remoteId.isNullOrBlank()) {
            insert(entity)
        } else {
            val existing = findByRemoteId(entity.remoteId)
            if (existing == null) {
                insert(entity)
            } else {
                val merged = existing.copy(
                    remoteId     = entity.remoteId,
                    userId       = entity.userId ?: existing.userId,
                    consultantId = entity.consultantId ?: existing.consultantId,
                    status       = entity.status,
                    serviceType  = entity.serviceType,
                    //title        = entity.title ?: existing.title,
                    description  = entity.description,
                    priority     = entity.priority,
                    deadlineIso  = entity.deadlineIso,
                    documentId   = entity.documentId ?: existing.documentId,
                    filePath     = existing.filePath ?: entity.filePath,
                    syncState    = SyncState.SYNCED,
                    updatedAt    = System.currentTimeMillis(),
                    documentName = if (entity.documentName.isNotBlank()) entity.documentName else existing.documentName
                )
                update(merged)
            }
        }
    }
}

// ----- Users -----

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users ORDER BY firstName, surname")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getById(uid: String): UserEntity?
}

// ----- Chats -----

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>
}

// ----- Messages -----

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(msgs: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timeSent ASC")
    fun observeChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE outbound = 1 AND synced = 0")
    suspend fun getPendingOutbound(): List<MessageEntity>

    @Query("SELECT MAX(timeSent) FROM messages WHERE recipientId = :recipientId")
    suspend fun maxInboundTs(recipientId: String): Long?
}

// ----- Documents -----

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
}
