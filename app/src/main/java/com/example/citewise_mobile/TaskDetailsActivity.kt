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
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toUiDate
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.example.citewise_mobile.offline.LocalRepos
import com.google.android.material.button.MaterialButton
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
 * Unified Task Details screen with student/consultant modes.
 * - Shows quote summary (amount + word count) when available.
 * - Student can view/download annotated file once uploaded.
 * - Bottom sheet opens expanded reliably. Chat wired to ConversationActivity.
 */
class TaskDetailsActivity :
    BaseActivity(),
    FeedbackUploadBottomSheet.Callback {

    companion object {
        const val EXTRA_REQUEST = "extra_request"
        private const val TAG = "TaskDetailsActivity"
        private const val SHEET_TAG = "feedback_upload"
    }

    // ---- Lazy deps ----
    private val fileProviderAuthority by lazy { "${applicationContext.packageName}.fileprovider" }
    private val localRepos by lazy { LocalRepos(this) }
    private val docsRepo by lazy { DocumentsRepository(RetrofitInstance.documentsApi, applicationContext) }
    @Suppress("unused")
    private val serviceRepo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    // ---- Common views ----
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
    private lateinit var btnViewFeedbackFile: MaterialButton
    private lateinit var tvDescription: TextView

    // ---- Quote UI ----
    private lateinit var tvQuoteSummary: TextView
    private lateinit var btnAcceptQuote: MaterialButton
    private lateinit var btnDeclineQuote: MaterialButton
    private lateinit var btnRequestReview: MaterialButton

    // ---- Student: consultant card ----
    private lateinit var consultantCard: View
    private lateinit var imgConsultantAvatar: ImageView
    private lateinit var tvConsultantName: TextView
    private lateinit var tvConsultantAvailability: TextView
    private lateinit var btnChatConsultant: ImageButton

    // ---- Consultant extras ----
    private lateinit var cardQualitativeStudy: View
    private lateinit var tvDocName: TextView
    private lateinit var btnUploadFeedback: View
    private lateinit var btnRevise: Button
    private lateinit var btnSendToStudent: Button
    private lateinit var btnDownloadPdf: Button
    private lateinit var btnConsultantPreview: MaterialButton
    private lateinit var btnConsultantDownload: MaterialButton

    private var currentReq: ServiceRequestDto? = null

    // ---- Chat peer cache (for ConversationActivity) ----
    private var consultantUid: String? = null
    private var consultantDisplayName: String = "Consultant"

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
            lifecycleScope.launch(Dispatchers.IO) { populateConsultantForStudent(req) }
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
        btnViewFeedbackFile = findViewById(R.id.btnViewFeedbackFile)
        tvDescription = findViewById(R.id.tvDescription)

        // Quote widgets
        tvQuoteSummary = findViewById(R.id.tvQuoteSummary)
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
        btnRevise = findViewById(R.id.btnRevise)
        btnSendToStudent = findViewById(R.id.btnSendToStudent)
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf)
        btnConsultantPreview = findViewById(R.id.btnConsultantPreview)
        btnConsultantDownload = findViewById(R.id.btnConsultantDownload)

        // Consultant: preview/download original + open bottom sheet
        cardQualitativeStudy.setOnClickListener { handleOpenFile() }

        btnConsultantPreview.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = req.originalFileName ?: req.customName ?: "${docId}.bin"
            downloadAndOpenInApp(docId, fileName)
        }

        btnConsultantDownload.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = req.originalFileName ?: req.customName ?: "Student File"
            lifecycleScope.launch(Dispatchers.IO) {
                when (val s = docsRepo.getSignedUrl(docId)) {
                    is NetResult.Ok -> withContext(Dispatchers.Main) { enqueueSystemDownload(s.data, fileName) }
                    is NetResult.Err -> withContext(Dispatchers.Main) {
                        when (val saved = docsRepo.downloadToDisk(docId, fileName)) {
                            is NetResult.Ok -> toast("Saved to ${saved.data.absolutePath}")
                            is NetResult.Err -> toast("Download failed: ${saved.message}")
                        }
                    }
                }
            }
        }

        btnUploadFeedback.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val id = req.id ?: return@setOnClickListener toast("Request ID missing.")
            // Server will do: count words, compute quote, set status to Completed, push to student.
            openFeedbackBottomSheet(id, nextStatus = "review_submitted")
        }

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
                    is NetResult.Ok -> withContext(Dispatchers.Main) { enqueueSystemDownload(s.data, fileName) }
                    is NetResult.Err -> withContext(Dispatchers.Main) {
                        when (val saved = docsRepo.downloadToDisk(docId, fileName)) {
                            is NetResult.Ok -> toast("Saved to ${saved.data.absolutePath}")
                            is NetResult.Err -> toast("Download failed: ${saved.message}")
                        }
                    }
                }
            }
        }

        // Feedback file actions (student)
        btnViewFeedbackFile.setOnClickListener {
            val url = currentReq?.feedbackFileUrl
            if (url.isNullOrBlank()) toast("No feedback file yet.") else openUrl(url)
        }
        btnDownloadFeedbackFile.setOnClickListener {
            val url = currentReq?.feedbackFileUrl
            val name = currentReq?.feedbackFileName ?: "Feedback"
            if (url.isNullOrBlank()) toast("No feedback file yet.") else enqueueSystemDownload(url, name)
        }

        // Quote actions (student) — endpoints TBD server-side
        btnAcceptQuote.setOnClickListener {
            toast("Accept tapped. (Hook to /quotes/{id}/accept when available.)")
        }
        btnDeclineQuote.setOnClickListener {
            toast("Decline tapped. (Hook to /quotes/{id}/decline when available.)")
        }
        btnRequestReview.setOnClickListener {
            toast("Query tapped. (Open chat or feedback dialog.)")
        }

        // Chat
        btnChatConsultant.setOnClickListener { startChatWithConsultant() }
    }

    private fun bindRequest(req: ServiceRequestDto) {
        chipPriority.text = req.priority?.toPretty() ?: "—"

        tvProjectName.text = when {
            !req.customName.isNullOrBlank()   -> req.customName
            !req.description.isNullOrBlank()  -> req.description
            req.serviceType != null           -> req.serviceType.toPretty()
            else                              -> "Untitled Project"
        }

        val deadlineFromRemote = req.deadline.toUiDate()
        tvDeadline.text = deadlineFromRemote
        val displayFileName = req.customName ?: req.originalFileName ?: "Student File"
        tvStudentAndDeadline.text = "$displayFileName     $deadlineFromRemote"

        lifecycleScope.launch(Dispatchers.IO) {
            val localIso = tryFindLocalDeadlineIso(req)
            val localUi = isoToUiDate(localIso)
            val finalDeadline = if (deadlineFromRemote != "—") deadlineFromRemote else (localUi ?: "—")
            withContext(Dispatchers.Main) {
                tvDeadline.text = finalDeadline
                tvStudentAndDeadline.text = "$displayFileName     $finalDeadline"
            }
        }

        tvServiceName.text = req.serviceType?.toPretty() ?: "Other"

        // Annotated/feedback file visibility
        val hasFeedback = !req.feedbackFileUrl.isNullOrEmpty()
        tvFilesCount.text = if (hasFeedback) "2" else "1"
        tvStudentFileName.text = displayFileName
        tvFeedbackFileName.text = req.feedbackFileName ?: "Feedback/Annotated File"
        rowFeedbackFile.visibility = if (hasFeedback) View.VISIBLE else View.GONE

        // Quote summary (only if server provided a quote)
        val amount = req.quotationAmount
        val words = req.quotationWords
        val currency = (req.quotationCurrency ?: "").ifBlank { "USD" }
        val hasQuote = req.quotationId != null && amount != null && words != null

        if (hasQuote) {
            tvQuoteSummary.visibility = View.VISIBLE
            tvQuoteSummary.text = buildString {
                append(currency)
                append(" ")
                append(String.format(Locale.US, "%.2f", amount))
                append(" • ")
                append(words)
                append(" words")
            }
        } else {
            tvQuoteSummary.visibility = View.GONE
        }

        tvDocName.text = req.originalFileName ?: displayFileName

        // Update button visibilities that depend on student/consultant and quote presence
        applyRoleVisibility(getCurrentUserRole(), hasQuote)
    }

    // ---------------- Role-driven visibility ----------------
    private fun applyRoleVisibility(role: UserRole, hasQuote: Boolean? = null) {
        val isStudent = role == UserRole.STUDENT
        val isConsultant = role == UserRole.CONSULTANT

        // If hasQuote is provided, only show the quote actions when a quote exists
        val showQuoteActions = hasQuote ?: true
        setVisible(btnAcceptQuote, isStudent && showQuoteActions)
        setVisible(btnDeclineQuote, isStudent && showQuoteActions)
        setVisible(btnRequestReview, isStudent && showQuoteActions)

        setVisible(consultantCard, isStudent)

        setVisible(cardQualitativeStudy, isConsultant)
        setVisible(btnUploadFeedback, isConsultant)

        setVisible(btnRevise, isConsultant)
        setVisible(btnSendToStudent, isConsultant)
        setVisible(btnDownloadPdf, isConsultant)
        setVisible(btnConsultantPreview, isConsultant)
        setVisible(btnConsultantDownload, isConsultant)

        setVisible(btnViewStudentFile, isStudent)
        setVisible(btnDownloadStudentFile, isStudent)

        if (!isStudent) rowFeedbackFile.visibility = View.GONE
    }

    private fun setVisible(v: View, show: Boolean) {
        v.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Opens the feedback bottom sheet reliably with proper expansion. */
    private fun openFeedbackBottomSheet(requestId: String, nextStatus: String? = "review_submitted") {
        FeedbackUploadBottomSheet
            .newInstance(requestId = requestId, newStatus = nextStatus)
            .show(supportFragmentManager, SHEET_TAG)
    }

    // Bottom sheet callback
    override fun onFeedbackUploaded(updated: ServiceRequestDto) {
        currentReq = updated
        bindRequest(updated)
        toast("Feedback uploaded.")
    }

    // ---------------- Consultant resolution & population ----------------
    private suspend fun populateConsultantForStudent(req: ServiceRequestDto) {
        val uid = resolveConsultantUid(req)
        if (uid.isNullOrBlank()) {
            consultantUid = null
            consultantDisplayName = "Unassigned"
            withContext(Dispatchers.Main) {
                tvConsultantName.text = "Unassigned"
                tvConsultantAvailability.text = ""
            }
            return
        }
        consultantUid = uid
        val profile = fetchConsultantFromRtdb(uid)
        withContext(Dispatchers.Main) {
            if (profile == null) {
                consultantDisplayName = "Consultant"
                tvConsultantName.text = "Consultant"
                tvConsultantAvailability.text = ""
            } else {
                val nameFinal = profile.name.ifBlank { "Consultant" }
                consultantDisplayName = nameFinal
                tvConsultantName.text = nameFinal
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

    private suspend fun resolveConsultantUid(req: ServiceRequestDto): String? {
        req.consultantId?.takeIf { it.isNotBlank() }?.let { return it }

        val requestId = req.id ?: return null
        val fs = FirebaseFirestore.getInstance()
        val snap = fs.collection("ServiceReviews")
            .whereEqualTo("requestId", requestId)
            .limit(1)
            .get()
            .await()

        val d = snap.documents.firstOrNull() ?: return null
        d.getString("consultantUid")?.takeIf { it.isNotBlank() }?.let { return it }
        val ref = d.get("consultant") as? DocumentReference
        return ref?.id
    }

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

    // ---------------- Chat wiring ----------------
    private fun startChatWithConsultant() {
        val role = getCurrentUserRole()
        if (role == UserRole.STUDENT) {
            lifecycleScope.launch(Dispatchers.IO) {
                var peerUid = consultantUid
                if (peerUid.isNullOrBlank()) {
                    currentReq?.let { peerUid = resolveConsultantUid(it) }
                }
                val name = consultantDisplayName.ifBlank { "Consultant" }
                withContext(Dispatchers.Main) {
                    if (peerUid.isNullOrBlank()) {
                        toast("No consultant assigned yet.")
                    } else {
                        openConversation(peerUid!!, name)
                    }
                }
            }
        } else {
            toast("Chat is available from the student view for the assigned consultant.")
        }
    }

    private fun openConversation(peerUid: String, displayName: String) {
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: run {
            toast("You must be signed in to chat.")
            return
        }
        val chatId = buildChatId(myUid, peerUid)
        val intent = Intent(this@TaskDetailsActivity, ConversationActivity::class.java).apply {
            putExtra(ConversationActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ConversationActivity.EXTRA_CHAT_TITLE, displayName)
            putExtra(ConversationActivity.EXTRA_PEER_UID, peerUid)
        }
        startActivity(intent)
    }

    private fun buildChatId(a: String, b: String) = if (a <= b) "${a}_$b" else "${b}_$a"

    // ---------------- Helpers ----------------
    private suspend fun tryFindLocalDeadlineIso(req: ServiceRequestDto): String? =
        withContext(Dispatchers.IO) {
            val byRemote = req.id?.let { runCatching { localRepos.requests.findByRemoteId(it) }.getOrNull() }
            byRemote?.deadlineIso ?: run {
                val docId = req.documentId ?: return@run null
                val all = runCatching { localRepos.requests.getAll() }.getOrNull().orEmpty()
                all.firstOrNull { it.documentId == docId }?.deadlineIso
            }
        }

    private fun isoToUiDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dst = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try { src.parse(iso)?.let(dst::format) } catch (_: ParseException) { null }
    }

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

    private fun downloadAndOpenInApp(documentId: String, preferredName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = docsRepo.downloadToDisk(documentId, preferredName)) {
                is NetResult.Ok -> withContext(Dispatchers.Main) {
                    val file = result.data
                    val intent = Intent(this@TaskDetailsActivity, DocumentViewerActivity::class.java)
                        .putExtra(DocumentViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                        .putExtra(DocumentViewerActivity.EXTRA_REQUEST_ID, currentReq?.id)
                        .putExtra(DocumentViewerActivity.EXTRA_DISPLAY_NAME, preferredName)
                    startActivity(intent)
                }
                is NetResult.Err -> withContext(Dispatchers.Main) {
                    toast("Download failed: ${result.message}")
                }
            }
        }
    }

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
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Throwable) { toast("No app can handle this link.") }
    }

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

    private fun handleOpenFile() {
        val req = currentReq ?: return
        val docId = req.documentId ?: return toast("Document not available yet.")
        val fileName = req.originalFileName ?: req.customName ?: "${docId}.bin"
        downloadAndOpenInApp(docId, fileName)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
