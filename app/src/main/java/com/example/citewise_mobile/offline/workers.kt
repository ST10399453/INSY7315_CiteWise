package com.example.citewise_mobile.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.data.MessagesRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ResourcesRepository
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue.serverTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// ============================================================
// Shared helpers
// ============================================================

private object Net {
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

internal object WorkerCfg {
    val connectedConstraints: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val backoffPolicy = BackoffPolicy.EXPONENTIAL
    val backoffDelaySeconds = 3L
}

private object WorkNames {
    const val USERS_SYNC      = "users_sync"
    const val MESSAGES_SYNC   = "messages_sync"
    const val REQUESTS_SYNC   = "requests_sync"
    const val REQUESTS_PULL   = "requests_pull"
    const val RESOURCES_SYNC  = "resources_sync"
    const val DOCUMENTS_SYNC  = "documents_sync"
}

// ============================================================
// UsersSyncWorker  (pull from Firestore /users; normalize roles)
// ============================================================

class UsersSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!Net.isOnline(applicationContext)) return@withContext Result.retry()

        val local = LocalRepos(applicationContext)
        val cloud = CloudDataSources()

        return@withContext try {
            // CloudDataSources.fetchUsers() now tolerates Timestamp/Number/String for updatedAt
            val cloudUsers = cloud.fetchUsers()
            val localUsersByUid = local.users.getAll().associateBy { it.uid }

            val normalized = cloudUsers.map { u -> u.copy(role = u.role.trim().lowercase()) }
            val toUpsert = normalized.filter { cu ->
                val existing = localUsersByUid[cu.uid]
                existing == null || cu.updatedAt > existing.updatedAt
            }

            if (toUpsert.isNotEmpty()) {
                local.users.upsertAll(toUpsert)
            }

            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (t: Throwable) {
            Log.e("UsersSyncWorker", "Failure: ${t.message}", t)
            Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UsersSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkNames.USERS_SYNC, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun oneShot(context: Context) {
            val once = OneTimeWorkRequestBuilder<UsersSyncWorker>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(once)
        }
    }
}

// ============================================================
// MessagesSyncWorker  (push pending + pull delta)
// ============================================================

class MessagesSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val msgsRepo by lazy { MessagesRepository(RetrofitInstance.messagesApi) }
    private val local by lazy { LocalRepos(applicationContext) }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!Net.isOnline(applicationContext)) return@withContext Result.retry()

        val myUid = auth.currentUser?.uid.orEmpty()

        // 1) Push pending outbound
        val pending = local.messages.getPendingOutbound()
        for (m in pending) {
            when (val sent = msgsRepo.send(toUid = m.recipientId, body = m.body, clientId = null)) {
                is NetResult.Ok -> {
                    val dto = sent.data
                    val entity = MessageEntity(
                        messageId   = dto.id ?: m.messageId,
                        chatId      = buildChatId(dto.fromUid, dto.toUid),
                        senderId    = dto.fromUid,
                        recipientId = dto.toUid,
                        body        = dto.body,
                        timeSent    = dto.createdAt ?: m.timeSent,
                        outbound    = dto.fromUid == myUid,
                        inbound     = dto.toUid == myUid,
                        synced      = true
                    )
                    local.messages.upsertMessages(listOf(entity))
                }
                is NetResult.Err -> {
                    return@withContext if ((sent.code ?: 0) in 500..599) Result.retry() else Result.failure()
                }
            }
        }

        // 2) Pull delta for my inbound (others → me)
        val since = local.messages.maxInboundTs(myUid) ?: 0L
        return@withContext when (val res = msgsRepo.since(since)) {
            is NetResult.Ok -> {
                if (res.data.isNotEmpty()) {
                    val hydrated = res.data.map { d ->
                        MessageEntity(
                            messageId   = d.id ?: "${d.fromUid}_${d.toUid}_${d.createdAt ?: 0}",
                            chatId      = buildChatId(d.fromUid, d.toUid),
                            senderId    = d.fromUid,
                            recipientId = d.toUid,
                            body        = d.body,
                            timeSent    = d.createdAt ?: System.currentTimeMillis(),
                            outbound    = d.fromUid == myUid,
                            inbound     = d.toUid == myUid,
                            synced      = true
                        )
                    }
                    local.messages.upsertMessages(hydrated)
                }
                Result.success()
            }
            is NetResult.Err -> if ((res.code ?: 0) in 500..599) Result.retry() else Result.failure()
        }
    }

    private fun buildChatId(a: String, b: String): String =
        if (a <= b) "${a}_$b" else "${b}_$a"

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<MessagesSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkNames.MESSAGES_SYNC, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun oneShot(context: Context) {
            val once = OneTimeWorkRequestBuilder<MessagesSyncWorker>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(once)
        }
    }
}

// ============================================================
// RequestsSyncWorker  (Room -> REST API POST /requests multipart)
// Retries PENDING_UPLOAD and FAILED
// ============================================================

class RequestsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "RequestsSyncWorker"
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }
    private val local by lazy { LocalRepos(applicationContext) }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val fs  by lazy { FirebaseFirestore.getInstance() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!Net.isOnline(applicationContext)) return@withContext Result.retry()

        // Optional: auto-revive older failures
        runCatching {
            local.requests.resetFailedOlderThan(System.currentTimeMillis() - 10 * 60 * 1000)
        }

        val pending = try {
            local.requests.getBySyncStates(listOf(SyncState.PENDING_UPLOAD, SyncState.FAILED))
        } catch (_: Throwable) {
            local.requests.getBySyncState(SyncState.PENDING_UPLOAD)
        }

        if (pending.isEmpty()) return@withContext Result.success()

        pending.forEach { sr ->
            try {
                val stagedFile = sr.filePath?.let { File(it) }
                if (stagedFile == null || !stagedFile.exists()) {
                    Log.w(TAG, "Missing staged file for localId=${sr.localId}")
                    local.requests.update(
                        sr.copy(syncState = SyncState.FAILED, updatedAt = System.currentTimeMillis())
                    )
                    return@forEach
                }

                when (val result = repo.createRequestMultipart(
                    file = stagedFile,
                    mime = stagedFile.guessMimeOrDefault(),
                    documentName = sr.documentName,
                    customName = sr.customName.takeIf { it.isNotBlank() },
                    serviceType = sr.serviceType.orEmpty(),
                    description = sr.description.orEmpty(),
                    priority = sr.priority.orEmpty(),
                    deadlineIso = sr.deadlineIso
                )) {
                    is NetResult.Ok -> {
                        val dto = result.data

                        // Delete staged after server accepts
                        runCatching { if (stagedFile.exists()) stagedFile.delete() }

                        val newUserId = dto.userId ?: sr.userId ?: auth.currentUser?.uid
                        val newConsultantId = dto.consultantId ?: sr.consultantId

                        local.requests.update(
                            sr.copy(
                                remoteId     = dto.id,
                                documentId   = dto.documentId,
                                status       = dto.status ?: "submitted",
                                userId       = newUserId,
                                consultantId = newConsultantId,
                                syncState    = SyncState.SYNCED,
                                updatedAt    = System.currentTimeMillis()
                            )
                        )

                        val fsId = dto.id ?: sr.remoteId ?: sr.localId.toString()
                        val fsData = hashMapOf(
                            "id"               to fsId,
                            "userId"           to (newUserId ?: ""),
                            "consultantId"     to (newConsultantId ?: ""),
                            "serviceType"      to ((dto.serviceType?.name) ?: sr.serviceType),
                            "description"      to (dto.description ?: sr.description ?: ""),
                            "priority"         to ((dto.priority?.name) ?: sr.priority ?: "MEDIUM"),
                            "status"           to (dto.status ?: "submitted"),
                            "documentId"       to (dto.documentId ?: sr.documentId),
                            "originalFileName" to (dto.originalFileName ?: sr.documentName),
                            "customName"       to (dto.customName ?: sr.customName),
                            "deadline"         to (sr.deadlineIso ?: dto.deadline ?: sr.deadlineIso),
                            "createdAt"        to (dto.createdAt?.epochMillis ?: sr.createdAt),
                            "updatedAt"        to serverTimestamp()
                        )
                        runCatching {
                            fs.collection("ServiceReviews")
                                .document(fsId)
                                .set(fsData, SetOptions.merge())
                                .addOnSuccessListener { Log.d(TAG, "Firestore upsert ok id=$fsId") }
                                .addOnFailureListener { e -> Log.w(TAG, "Firestore upsert failed id=$fsId: ${e.message}") }
                        }

                        Log.d(TAG, "Uploaded localId=${sr.localId} -> remoteId=${dto.id}")
                    }
                    is NetResult.Err -> {
                        Log.w(TAG, "Upload failed localId=${sr.localId}: ${result.message} [code=${result.code}]")
                        if ((result.code ?: 0) in 500..599) {
                            return@withContext Result.retry()
                        } else {
                            local.requests.update(
                                sr.copy(syncState = SyncState.FAILED, updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                }
            } catch (io: IOException) {
                Log.e(TAG, "Network error: ${io.message}")
                return@withContext Result.retry()
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error: ${t.message}", t)
                local.requests.update(
                    sr.copy(syncState = SyncState.FAILED, updatedAt = System.currentTimeMillis())
                )
            }
        }

        Result.success()
    }

    private fun File.guessMimeOrDefault(): String =
        when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }

    companion object {
        fun oneShot(context: Context) {
            val req = OneTimeWorkRequestBuilder<RequestsSyncWorker>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<RequestsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkNames.REQUESTS_SYNC, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }
    }
}

// ============================================================
// ResourcesSyncWorker  (Room -> REST API POST /resources multipart)
// Retries PENDING_UPLOAD and FAILED
// ============================================================

class ResourcesSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "ResourcesSyncWorker"
    private val local by lazy { LocalRepos(applicationContext) }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val fs   by lazy { FirebaseFirestore.getInstance() }
    private val repo by lazy { ResourcesRepository(RetrofitInstance.resourcesApi) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!Net.isOnline(applicationContext)) return@withContext Result.retry()

        // Optional: auto-revive older failures
        runCatching {
            local.resources.resetFailedOlderThan(System.currentTimeMillis() - 10 * 60 * 1000)
        }

        val toSync = try {
            local.resources.getBySyncStates(listOf(SyncState.PENDING_UPLOAD, SyncState.FAILED))
        } catch (_: Throwable) {
            local.resources.getBySyncState(SyncState.PENDING_UPLOAD)
        }

        if (toSync.isEmpty()) return@withContext Result.success()

        // Build bearer once
        val authHeader = buildAuthHeader()

        for (res in toSync) {
            try {
                val stagedFile = res.filePath?.let { File(it) }
                if (stagedFile == null || !stagedFile.exists()) {
                    Log.w(TAG, "Missing staged file for localId=${res.localId}")
                    local.resources.update(
                        res.copy(syncState = SyncState.FAILED, updatedAt = System.currentTimeMillis())
                    )
                    continue
                }

                val name     = res.title.ifBlank { res.fileName ?: "Untitled" }
                val faculty  = normalizeFaculty(res.faculty)
                val category = normalizeCategory(res.category)
                val mime     = stagedFile.guessMimeOrDefault()
                val desc     = res.description

                when (val result = repo.createResourceMultipart(
                    file = stagedFile,
                    mime = mime,
                    name = name,
                    faculty = faculty,
                    category = category,
                    description = desc,
                    auth = authHeader
                )) {
                    is NetResult.Ok -> {
                        val dto = result.data

                        runCatching { if (stagedFile.exists()) stagedFile.delete() }

                        local.resources.update(
                            res.copy(
                                remoteId  = dto.id,
                                syncState = SyncState.SYNCED,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        // Mirror to Firestore (optional)
                        val fsId = dto.id ?: res.remoteId ?: res.localId.toString()
                        val payload = hashMapOf(
                            "id"          to fsId,
                            "title"       to name,
                            "description" to (desc ?: ""),
                            "category"    to (category ?: "AI_USAGE"),
                            "faculty"     to faculty,
                            "documentId"  to (res.documentId ?: ""),
                            "fileName"    to (res.fileName ?: stagedFile.name),
                            "adminUid"    to (res.adminUid ?: auth.currentUser?.uid.orEmpty()),
                            "createdAt"   to res.createdAt,
                            "updatedAt"   to serverTimestamp()
                        )
                        runCatching {
                            fs.collection("resources")
                                .document(fsId)
                                .set(payload, SetOptions.merge())
                        }

                        if (!res.documentId.isNullOrBlank()) {
                            DocumentsSyncWorker.oneShot(applicationContext)
                        }

                        Log.d(TAG, "Uploaded resource localId=${res.localId} -> remoteId=${dto.id}")
                    }
                    is NetResult.Err -> {
                        Log.w(TAG, "Resource upload failed localId=${res.localId}: ${result.message} [code=${result.code}]")
                        if ((result.code ?: 0) in 500..599) {
                            return@withContext Result.retry()
                        } else {
                            local.resources.update(
                                res.copy(syncState = SyncState.FAILED, updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                }
            } catch (io: IOException) {
                Log.e(TAG, "Network error: ${io.message}")
                return@withContext Result.retry()
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error: ${t.message}", t)
                local.resources.update(
                    res.copy(syncState = SyncState.FAILED, updatedAt = System.currentTimeMillis())
                )
            }
        }

        Result.success()
    }

    /** Map UI / free text to server enum values. */
    private fun normalizeFaculty(input: String?): String =
        when (input?.trim()?.lowercase()) {
            "engineering" -> "ENGINEERING"
            "science"     -> "SCIENCE"
            "humanities"  -> "HUMANITIES"
            "business"    -> "BUSINESS"
            "general", "", null -> "GENERAL"
            else -> "GENERAL"
        }

    /** Ensure only valid server categories are sent. */
    private fun normalizeCategory(input: String?): String? =
        when (input?.trim()?.uppercase()) {
            "WRITING_GUIDE", "TEMPLATE", "AI_USAGE" -> input.trim().uppercase()
            null, "" -> "AI_USAGE"
            else -> "AI_USAGE"
        }

    private fun File.guessMimeOrDefault(): String =
        when (extension.lowercase()) {
            "pdf"  -> "application/pdf"
            "doc"  -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt"  -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls"  -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt"  -> "text/plain"
            else   -> "application/octet-stream"
        }

    private suspend fun buildAuthHeader(): String? = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext null
        val token = try {
            com.google.android.gms.tasks.Tasks.await(user.getIdToken(true)).token
        } catch (_: Throwable) { null }
        token?.let { "Bearer $it" }
    }

    companion object {
        fun oneShot(context: Context) {
            val req = OneTimeWorkRequestBuilder<ResourcesSyncWorker>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ResourcesSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkNames.RESOURCES_SYNC, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }
    }
}

// ============================================================
// RequestsPullWorker  (fetch user’s requests -> Room)
// ============================================================

class RequestsPullWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }
    private val local by lazy { LocalRepos(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!Net.isOnline(applicationContext)) return@withContext Result.retry()
        val uid = auth.currentUser?.uid ?: return@withContext Result.success()

        when (val res = repo.listMyRequests(status = null, userId = uid)) {
            is NetResult.Ok -> {
                res.data.forEach { dto ->
                    val existing: ServiceRequestEntity? = dto.id?.let { local.requests.findByRemoteId(it) }
                    val mapped = dto.toEntityPreservingLocalFallback(existing)
                    local.requests.upsertByRemoteId(mapped)
                }
                DocumentsSyncWorker.oneShot(applicationContext)
                Result.success()
            }
            is NetResult.Err -> if ((res.code ?: 0) in 500..599) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RequestsPullWorker>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkNames.REQUESTS_PULL, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun oneShot(context: Context) {
            val once = OneTimeWorkRequestBuilder<RequestsPullWorker>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(once)
        }
    }
}

// ============================================================
// DocumentsSyncWorker
// - Downloads by documentId and caches to disk
// - Supports BOTH request docs and resource docs
// ============================================================

class DocumentsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "DocumentsSyncWorker"
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val local by lazy { LocalRepos(applicationContext) }
    private val docsRepo by lazy { DocumentsRepository(RetrofitInstance.documentsApi, applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!Net.isOnline(applicationContext)) return@withContext Result.retry()
        val uid = auth.currentUser?.uid ?: return@withContext Result.success()

        val requestDocs  = local.requests.allWithDocumentId()
        val resourceDocs = local.resources.allWithDocumentId()
        val docIdToPreferredName = linkedMapOf<String, String>()

        requestDocs.forEach { sr ->
            sr.documentId?.let { docId ->
                val name = (sr.documentName).ifBlank { "document_$docId" }
                docIdToPreferredName.putIfAbsent(docId, name)
            }
        }
        resourceDocs.forEach { res ->
            res.documentId?.let { docId ->
                val name = (res.fileName ?: "").ifBlank { "resource_$docId" }
                docIdToPreferredName.putIfAbsent(docId, name)
            }
        }

        if (docIdToPreferredName.isEmpty()) return@withContext Result.success()

        for ((docId, preferredName) in docIdToPreferredName) {
            val existingDoc = local.documents.getById(docId)
            if (existingDoc?.localPath?.let { File(it).exists() } == true) continue

            when (val dl = docsRepo.downloadToDisk(docId, preferredName)) {
                is NetResult.Ok -> {
                    val file = dl.data
                    val upsert = DocumentEntity(
                        id = docId,
                        ownerUid = uid,
                        fileName = preferredName,
                        mimeType = guessMime(preferredName),
                        sizeBytes = file.length(),
                        updatedAt = System.currentTimeMillis(),
                        etag = null,
                        remoteUrlHint = null,
                        localPath = file.absolutePath,
                        downloadedAt = System.currentTimeMillis()
                    )
                    local.documents.upsertAll(listOf(upsert))
                }
                is NetResult.Err -> {
                    Log.w(TAG, "Download failed for docId=$docId: ${dl.message}")
                    if ((dl.code ?: 0) in 500..599) return@withContext Result.retry()
                }
            }
        }

        Result.success()
    }

    private fun guessMime(name: String?): String {
        val n = name?.lowercase().orEmpty()
        return when {
            n.endsWith(".pdf")  -> "application/pdf"
            n.endsWith(".doc")  -> "application/msword"
            n.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            n.endsWith(".ppt")  -> "application/vnd.ms-powerpoint"
            n.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            n.endsWith(".xls")  -> "application/vnd.ms-excel"
            n.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            n.endsWith(".txt")  -> "text/plain"
            else                -> "application/octet-stream"
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DocumentsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WorkNames.DOCUMENTS_SYNC, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun oneShot(context: Context) {
            val once = OneTimeWorkRequestBuilder<DocumentsSyncWorker>()
                .setConstraints(WorkerCfg.connectedConstraints)
                .setBackoffCriteria(WorkerCfg.backoffPolicy, WorkerCfg.backoffDelaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(once)
        }
    }
}
