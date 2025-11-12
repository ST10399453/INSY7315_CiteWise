package com.example.citewise_mobile

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toUiDate
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.offline.LocalRepos
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLConnection
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Unified Task Details screen.
 * - Student sees quote + consultant card (populated via ServiceReviews → RTDB).
 * - Consultant sees file row, upload, collapsibles, and footer actions.
 * Visibility handled by applyRoleVisibility(getCurrentUserRole()).
 */
class TaskDetailsActivity : BaseActivity() {

    companion object {
        const val EXTRA_REQUEST = "extra_request"
    }

    // ---- Lazy deps ----
    private val fileProviderAuthority by lazy {
        "${applicationContext.packageName}.fileprovider"
    }
    private val localRepos by lazy { LocalRepos(this) }
    private val docsRepo by lazy {
        DocumentsRepository(RetrofitInstance.documentsApi, applicationContext)
    }

    // ---- Common views (existing) ----
    private lateinit var btnBack: ImageButton
    private lateinit var chipPriority: TextView
    private lateinit var tvProjectName: TextView
    private lateinit var tvDeadline: TextView
    private lateinit var tvStudentAndDeadline: TextView
    private lateinit var tvServiceName: TextView
    private lateinit var tvFilesCount: TextView
    private lateinit var tvStudentFileName: TextView
    private lateinit var tvFeedbackFileName: TextView
    private lateinit var rowFeedbackFile: View
    private lateinit var btnDownloadStudentFile: MaterialButton
    private lateinit var btnDownloadFeedbackFile: MaterialButton
    private lateinit var btnViewStudentFile: MaterialButton
    private lateinit var tvDescription: TextView
    private lateinit var progressStage: LinearProgressIndicator

    // ---- Student: quote actions ----
    private lateinit var btnAcceptQuote: MaterialButton
    private lateinit var btnDeclineQuote: MaterialButton
    private lateinit var btnRequestReview: MaterialButton

    // ---- Student: consultant card ----
    private lateinit var consultantCard: View
    private lateinit var imgConsultantAvatar: ImageView
    private lateinit var tvConsultantName: TextView
    private lateinit var tvConsultantAvailability: TextView
    private lateinit var btnChatConsultant: ImageButton

    // ---- Consultant extras (ported) ----
    private lateinit var cardQualitativeStudy: View
    private lateinit var tvDocName: TextView
    private lateinit var btnUploadFeedback: View
    private lateinit var versionHistoryPanel: View
    private lateinit var versionHistoryContent: View
    private lateinit var progressStatusPanel: View
    private lateinit var progressHistoryContent: View
    private lateinit var btnRevise: Button
    private lateinit var btnSendToStudent: Button
    private lateinit var btnDownloadPdf: Button

    private var currentReq: ServiceRequestDto? = null

    // ---------------- Lifecycle ----------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_details)
        applyInsets(android.R.id.content)
        initViews()

        val req = intent.getSerializableExtra(EXTRA_REQUEST) as? ServiceRequestDto
        if (req == null) {
            toast("Task data missing.")
            finish()
            return
        }
        currentReq = req

        bindRequest(req)
        applyRoleVisibility(getCurrentUserRole())

        // Populate consultant card only for Student role
        if (getCurrentUserRole() == UserRole.STUDENT) {
            lifecycleScope.launch(Dispatchers.IO) {
                populateConsultantForStudent(req)
            }
        }
    }

    // ---------------- View binding ----------------
    private fun initViews() {
        // Header
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Core
        chipPriority = findViewById(R.id.chipPriority)
        tvProjectName = findViewById(R.id.tvProjectName)
        tvDeadline = findViewById(R.id.tvDeadline)
        tvStudentAndDeadline = findViewById(R.id.tvStudentAndDeadline)
        tvServiceName = findViewById(R.id.tvServiceName)
        tvFilesCount = findViewById(R.id.tvFilesCount)
        tvStudentFileName = findViewById(R.id.tvStudentFileName)
        tvFeedbackFileName = findViewById(R.id.tvFeedbackFileName)
        rowFeedbackFile = findViewById(R.id.rowFeedbackFile)
        btnDownloadStudentFile = findViewById(R.id.btnDownloadStudentFile)
        btnDownloadFeedbackFile = findViewById(R.id.btnDownloadFeedbackFile)
        btnViewStudentFile = findViewById(R.id.btnViewStudentFile)
        tvDescription = findViewById(R.id.tvDescription)
        progressStage = findViewById(R.id.progressStage)

        // Student quote actions
        btnAcceptQuote = findViewById(R.id.btnAcceptQuote)
        btnDeclineQuote = findViewById(R.id.btnDeclineQuote)
        btnRequestReview = findViewById(R.id.btnRequestReview)

        // Consultant card (student-facing)
        consultantCard = findViewById(R.id.consultantCardRoot)
        imgConsultantAvatar = findViewById(R.id.imgConsultantAvatar)
        tvConsultantName = findViewById(R.id.tvConsultantName)
        tvConsultantAvailability = findViewById(R.id.tvConsultantAvailability)
        btnChatConsultant = findViewById(R.id.btnChatConsultant)

        // Consultant-only controls
        cardQualitativeStudy = findViewById(R.id.cardQualitativeStudy)
        tvDocName = findViewById(R.id.tvDocName)
        btnUploadFeedback = findViewById(R.id.btnUploadFeedback)
        versionHistoryPanel = findViewById(R.id.versionHistoryPanel)
        versionHistoryContent = findViewById(R.id.versionHistoryContent)
        progressStatusPanel = findViewById(R.id.progressStatusPanel)
        progressHistoryContent = findViewById(R.id.progressHistoryContent)
        btnRevise = findViewById(R.id.btnRevise)
        btnSendToStudent = findViewById(R.id.btnSendToStudent)
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf)

        // Expand/collapse
        versionHistoryPanel.findViewById<View>(R.id.tvVersionHistoryHeader)?.setOnClickListener {
            versionHistoryContent.visibility =
                if (versionHistoryContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        progressStatusPanel.findViewById<View>(R.id.tvProgressStatusHeader)?.setOnClickListener {
            progressHistoryContent.visibility =
                if (progressHistoryContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Consultant actions (safe to wire regardless of visibility)
        cardQualitativeStudy.setOnClickListener { handleOpenFile() }
        btnUploadFeedback.setOnClickListener { toast("Opening file picker for feedback upload…") }
        btnRevise.setOnClickListener { toast("Opening revision tools…") }
        btnSendToStudent.setOnClickListener { toast("Status updated and sent to student.") }
        btnDownloadPdf.setOnClickListener { toast("Downloading Quote PDF…") }

        // Student file actions
        btnViewStudentFile.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = req.customName ?: req.originalFileName ?: "Student File"
            downloadAndOpenInApp(docId, fileName)
        }
        btnDownloadStudentFile.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = req.customName ?: req.originalFileName ?: "Student File"
            lifecycleScope.launch(Dispatchers.IO) {
                when (val s = docsRepo.getSignedUrl(docId)) {
                    is NetResult.Ok -> withContext(Dispatchers.Main) {
                        enqueueSystemDownload(s.data, fileName)
                    }
                    is NetResult.Err -> withContext(Dispatchers.Main) {
                        when (val saved = docsRepo.downloadToDisk(docId, fileName)) {
                            is NetResult.Ok -> toast("Saved to ${saved.data.absolutePath}")
                            is NetResult.Err -> toast("Download failed: ${saved.message}")
                        }
                    }
                }
            }
        }
        btnDownloadFeedbackFile.setOnClickListener {
            val url = currentReq?.feedbackFileUrl
            if (url.isNullOrBlank()) return@setOnClickListener toast("No feedback file yet.")
            openUrl(url)
        }

        // Chat tap (you can navigate to your chat screen here)
        btnChatConsultant.setOnClickListener {
            toast("Opening chat with consultant…")
        }
    }

    private fun bindRequest(req: ServiceRequestDto) {
        // Priority
        chipPriority.text = req.priority?.toPretty() ?: "—"

        // Project name (prefer customName → description → serviceType)
        tvProjectName.text = when {
            !req.customName.isNullOrBlank()   -> req.customName
            !req.description.isNullOrBlank()  -> req.description
            req.serviceType != null           -> req.serviceType.toPretty()
            else                              -> "Untitled Project"
        }

        // Deadline (remote first) and combined line
        val deadlineFromRemote = req.deadline.toUiDate() // "—" when null
        tvDeadline.text = deadlineFromRemote
        val displayFileName = req.customName ?: req.originalFileName ?: "Student File"
        tvStudentAndDeadline.text = "$displayFileName     $deadlineFromRemote"

        // Local ISO fallback if remote missing
        lifecycleScope.launch(Dispatchers.IO) {
            val localIso = tryFindLocalDeadlineIso(req)
            val localUi = isoToUiDate(localIso)
            val finalDeadline = if (deadlineFromRemote != "—") deadlineFromRemote else (localUi ?: "—")
            withContext(Dispatchers.Main) {
                tvDeadline.text = finalDeadline
                tvStudentAndDeadline.text = "$displayFileName     $finalDeadline"
            }
        }

        // Service
        tvServiceName.text = req.serviceType?.toPretty() ?: "Other"

        // Files
        val hasFeedback = !req.feedbackFileUrl.isNullOrEmpty()
        tvFilesCount.text = if (hasFeedback) "2" else "1"
        tvStudentFileName.text = displayFileName
        tvFeedbackFileName.text = req.feedbackFileName ?: "Feedback/Annotated File"
        rowFeedbackFile.visibility = if (hasFeedback) View.VISIBLE else View.GONE

        // Consultant copy of filename for their file row
        tvDocName.text = req.originalFileName ?: displayFileName

        // Progress
        progressStage.setProgressCompat(statusToProgress(req.status.orEmpty()), true)
    }

    // ---------------- Role-driven visibility ----------------
    private fun applyRoleVisibility(role: UserRole) {
        val isStudent = role == UserRole.STUDENT
        val isConsultant = role == UserRole.CONSULTANT

        // Student-only actions
        setVisible(btnAcceptQuote, isStudent)
        setVisible(btnDeclineQuote, isStudent)
        setVisible(btnRequestReview, isStudent)

        // Student-facing consultant card
        setVisible(consultantCard, isStudent)

        // Consultant-only extras
        setVisible(cardQualitativeStudy, isConsultant)
        setVisible(btnUploadFeedback, isConsultant)
        setVisible(versionHistoryPanel, isConsultant)
        setVisible(progressStatusPanel, isConsultant)
        setVisible(btnRevise, isConsultant)
        setVisible(btnSendToStudent, isConsultant)
        setVisible(btnDownloadPdf, isConsultant)

        // Students can view/download their file; consultants generally open via the card
        setVisible(btnViewStudentFile, isStudent)
        setVisible(btnDownloadStudentFile, isStudent)

        // Feedback row (download) mostly for students
        if (!isStudent) rowFeedbackFile.visibility = View.GONE
    }

    private fun setVisible(v: View, show: Boolean) {
        v.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---------------- Consultant resolution & population ----------------
    /** Master flow for student: resolve consultant uid then fill card from RTDB. */
    private suspend fun populateConsultantForStudent(req: ServiceRequestDto) {
        val uid = resolveConsultantUid(req)
        if (uid.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                tvConsultantName.text = "Unassigned"
                tvConsultantAvailability.text = ""
            }
            return
        }
        val profile = fetchConsultantFromRtdb(uid)
        withContext(Dispatchers.Main) {
            if (profile == null) {
                tvConsultantName.text = "Consultant"
                tvConsultantAvailability.text = ""
            } else {
                tvConsultantName.text = profile.name.ifBlank { "Consultant" }
                val availability = buildString {
                    if (profile.isOnline) append("Online") else if (profile.lastSeen > 0L) {
                        append("Last seen ${timeAgoShort(profile.lastSeen)}")
                    }
                    if (profile.specialty.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(profile.specialty)
                    }
                }
                tvConsultantAvailability.text = availability
            }
        }
    }

    /** Prefer dto.consultantId; else look up from Firestore ServiceReviews by requestId. */
    private suspend fun resolveConsultantUid(req: ServiceRequestDto): String? {
        // 1) From DTO
        req.consultantId?.takeIf { it.isNotBlank() }?.let { return it }

        // 2) From Firestore ServiceReviews (requestId == req.id)
        val requestId = req.id ?: return null
        val fs = FirebaseFirestore.getInstance()
        val snap = fs.collection("ServiceReviews")
            .whereEqualTo("requestId", requestId)
            .limit(1)
            .get()
            .await()

        val d = snap.documents.firstOrNull() ?: return null

        // Preferred explicit field
        d.getString("consultantUid")?.takeIf { it.isNotBlank() }?.let { return it }

        // Fallback if you store a reference in "consultant"
        val ref = d.get("consultant") as? DocumentReference
        return ref?.id
    }

    /** Read /users/{uid} from Realtime DB. */
    private suspend fun fetchConsultantFromRtdb(uid: String): ConsultantRtdb? {
        val node = FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .get()
            .await()
        if (!node.exists()) return null

        val first = node.child("firstName").getValue(String::class.java)?.trim().orEmpty()
        val sur   = node.child("surname").getValue(String::class.java)?.trim().orEmpty()
        val name  = when {
            first.isNotEmpty() || sur.isNotEmpty() -> "$first $sur".trim()
            else -> node.child("name").getValue(String::class.java)?.trim().orEmpty()
        }.ifBlank { "Consultant" }

        return ConsultantRtdb(
            uid = uid,
            name = name,
            email = node.child("email").getValue(String::class.java).orEmpty(),
            specialty = node.child("specialty").getValue(String::class.java).orEmpty(),
            isOnline = node.child("online").getValue(Boolean::class.java) ?: false,
            lastSeen = node.child("lastSeen").getValue(Long::class.java) ?: 0L
        )
    }

    private data class ConsultantRtdb(
        val uid: String,
        val name: String,
        val email: String,
        val specialty: String,
        val isOnline: Boolean,
        val lastSeen: Long
    )

    private fun timeAgoShort(lastSeenMillis: Long): String {
        val diff = System.currentTimeMillis() - lastSeenMillis
        if (diff < 0) return "just now"
        val mins = diff / 60000
        val hrs  = mins / 60
        val days = hrs / 24
        return when {
            mins < 1  -> "just now"
            mins < 60 -> "${mins}m ago"
            hrs < 24  -> "${hrs}h ago"
            else      -> "${days}d ago"
        }
    }

    // ---------------- Existing helpers ----------------
    /** Find local deadline if missing remotely */
    private suspend fun tryFindLocalDeadlineIso(req: ServiceRequestDto): String? =
        withContext(Dispatchers.IO) {
            val byRemote = req.id?.let { runCatching { localRepos.requests.findByRemoteId(it) }.getOrNull() }
            byRemote?.deadlineIso ?: run {
                val docId = req.documentId ?: return@run null
                val all = runCatching { localRepos.requests.getAll() }.getOrNull().orEmpty()
                all.firstOrNull { it.documentId == docId }?.deadlineIso
            }
        }

    /** Convert ISO-8601 string to yyyy-MM-dd (for Room fallback only) */
    private fun isoToUiDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dst = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val d = src.parse(iso)
            d?.let(dst::format)
        } catch (_: ParseException) {
            null
        }
    }

    /** Open file using system MIME type */
    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, fileProviderAuthority, file)
            val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast("No app installed to open ${file.extension.uppercase(Locale.ROOT)} files.")
        } catch (t: Throwable) {
            toast("Unable to open file: ${t.message}")
        }
    }

    /** Download privately then open in in-app viewer */
    private fun downloadAndOpenInApp(documentId: String, preferredName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = docsRepo.downloadToDisk(documentId, preferredName)) {
                is NetResult.Ok -> withContext(Dispatchers.Main) {
                    val file = result.data
                    val intent = Intent(this@TaskDetailsActivity, DocumentViewerActivity::class.java)
                        .putExtra(DocumentViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                    startActivity(intent)
                }
                is NetResult.Err -> withContext(Dispatchers.Main) {
                    toast("Download failed: ${result.message}")
                }
            }
        }
    }

    /** System Downloads via DownloadManager */
    private fun enqueueSystemDownload(url: String, fileName: String) {
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(req)
        toast("Downloading to system Downloads…")
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            toast("No app can handle this link.")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- pretty printers ----
    private fun ServicePriority.toPretty(): String = when (this) {
        ServicePriority.LOW -> "Low"
        ServicePriority.MEDIUM -> "Medium"
        ServicePriority.HIGH -> "High"
    }

    private fun ServiceType.toPretty(): String = when (this) {
        ServiceType.PROOFREADING_EDITING -> "Proofreading & Editing"
        ServiceType.FORMATTING_REFERENCING -> "Formatting & Referencing"
        ServiceType.DATA_ANALYSIS_SUPPORT -> "Data Analysis Support"
        ServiceType.RESEARCH_METHODOLOGY_COACHING -> "Research/Methodology Coaching"
        ServiceType.TRANSLATION -> "Translation"
        ServiceType.OTHER -> "Other"
    }

    private fun statusToProgress(status: String): Int =
        when (status.trim().lowercase(Locale.ROOT)) {
            "submitted", "pending" -> 25
            "assigned", "in_progress", "in progress" -> 50
            "feedback", "awaiting_feedback", "feedback ready" -> 75
            "complete", "completed", "done" -> 100
            else -> 25
        }

    // Consultant tap on file card
    private fun handleOpenFile() {
        val req = currentReq ?: return
        val docId = req.documentId ?: return toast("Document not available yet.")
        val fileName = req.originalFileName ?: req.customName ?: "${docId}.bin"
        downloadAndOpenInApp(docId, fileName)
    }
}
