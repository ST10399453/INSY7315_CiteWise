package com.example.citewise_mobile

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import java.io.FileOutputStream
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
 * - If service request status is completed, hide feedback upload and consultant preview buttons.
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
    private lateinit var tvDescription: TextView

    // ---- Quote UI ----
    private lateinit var tvQuoteSummary: TextView
    private lateinit var btnAcceptQuote: MaterialButton
    private lateinit var btnDeclineQuote: MaterialButton
//    private lateinit var btnDownloadPdf: Button

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

        // ✅ This alone will now control visibility, including Completed state
        bindRequest(req)

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
        tvDescription = findViewById(R.id.tvDescription)

        // Quote widgets
        tvQuoteSummary = findViewById(R.id.tvQuoteSummary)
        btnAcceptQuote = findViewById(R.id.btnAcceptQuote)
        btnDeclineQuote = findViewById(R.id.btnDeclineQuote)
//        btnDownloadPdf = findViewById(R.id.btnDownloadPdf)

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
        btnConsultantPreview = findViewById(R.id.btnConsultantPreview)
        btnConsultantDownload = findViewById(R.id.btnConsultantDownload)

        // Consultant: preview/download original + open bottom sheet
        cardQualitativeStudy.setOnClickListener { handleOpenFile() }

        // Consultant VIEW → open in DocumentViewerActivity (annotate enabled)
        btnConsultantPreview.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")

            val rawName = req.originalFileName ?: req.customName ?: "Student File"
            val fileName = ensurePdfExtension(rawName)

            downloadAndOpenInApp(docId, fileName)
        }

        // Consultant DOWNLOAD → system DownloadManager (no internal fallback)
        btnConsultantDownload.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")

            val rawName = req.originalFileName ?: req.customName ?: "Student File"
            val fileName = ensurePdfExtension(rawName)

            lifecycleScope.launch(Dispatchers.IO) {
                when (val s = docsRepo.getSignedUrl(docId)) {
                    is NetResult.Ok -> withContext(Dispatchers.Main) {
                        enqueueSystemDownload(s.data, fileName)
                    }
                    is NetResult.Err -> withContext(Dispatchers.Main) {
                        toast("Unable to download file: ${s.message}")
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

        // ---------------- Download feedback (system) ----------------
        btnDownloadFeedbackFile.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val requestId = req.id ?: return@setOnClickListener toast("Request ID missing.")

            lifecycleScope.launch(Dispatchers.IO) {
                val response = RetrofitInstance.api.getFeedbackDownloadUrl(requestId)

                if (response.isSuccessful && response.body()?.url != null) {
                    val signedUrl = response.body()!!.url
                    val filename = req.feedbackFileName ?: "Feedback File"

                    withContext(Dispatchers.Main) {
                        enqueueSystemDownload(signedUrl, filename)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        toast("Unable to download feedback file.")
                    }
                }
            }
        }

        // ---------------- Student file actions (system download only) ----------------
        btnDownloadStudentFile.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available.")

            val rawName = req.originalFileName ?: req.customName ?: "Student File"
            val fileName = ensurePdfExtension(rawName)

            lifecycleScope.launch(Dispatchers.IO) {
                when (val s = docsRepo.getSignedUrl(docId)) {
                    is NetResult.Ok -> withContext(Dispatchers.Main) {
                        enqueueSystemDownload(s.data, fileName)
                    }
                    is NetResult.Err -> withContext(Dispatchers.Main) {
                        toast("Unable to download file: ${s.message}")
                    }
                }
            }
        }

        // Student VIEW → open in DocumentViewerActivity (read-only)
        btnViewStudentFile.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available.")

            val rawName = req.originalFileName ?: req.customName ?: "Student File"
            val fileName = ensurePdfExtension(rawName)

            downloadAndOpenInApp(docId, fileName)
        }

        btnAcceptQuote.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val requestId = req.id ?: return@setOnClickListener toast("Request ID missing.")

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        setQuotationStatusForRequest(
                            requestId = requestId,
                            quotationId = req.quotationId, // if ServiceRequestDto has this
                            status = "approved"
                        )
                    }

                    // UI thread
                    btnAcceptQuote.isEnabled = false
                    btnDeclineQuote.isEnabled = false
                    toast("Quote approved.")
                } catch (t: Throwable) {
                    toast("Failed to approve quote: ${t.message}")
                }
            }
        }

        btnDeclineQuote.setOnClickListener {
            val req = currentReq ?: return@setOnClickListener toast("Task missing.")
            val requestId = req.id ?: return@setOnClickListener toast("Request ID missing.")

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        setQuotationStatusForRequest(
                            requestId = requestId,
                            quotationId = req.quotationId, // if available
                            status = "underReview"
                        )
                    }

                    btnAcceptQuote.isEnabled = false
                    btnDeclineQuote.isEnabled = false
                    toast("Quote marked as under review.")
                } catch (t: Throwable) {
                    toast("Failed to update quote: ${t.message}")
                }
            }
        }



        // Chat
        btnChatConsultant.setOnClickListener { startChatWithConsultant() }
    }

    private suspend fun setQuotationStatusForRequest(
        requestId: String,
        quotationId: String?,
        status: String
    ) {
        val fs = FirebaseFirestore.getInstance()

        // Prefer using quotationId if we have it, otherwise look it up by requestId
        val quoteRef = if (!quotationId.isNullOrBlank()) {
            fs.collection("Quotations").document(quotationId)
        } else {
            val snap = fs.collection("Quotations")
                .whereEqualTo("requestId", requestId)
                .limit(1)
                .get()
                .await()

            snap.documents.firstOrNull()?.reference
                ?: throw IllegalStateException("Quotation not found for request $requestId")
        }

        // Update status only
        quoteRef.update("status", status).await()
    }

    
    private fun bindRequest(req: ServiceRequestDto) {
        chipPriority.text = req.priority?.toPretty() ?: "—"

        tvProjectName.text = when {
            !req.customName.isNullOrBlank() -> req.customName
            !req.description.isNullOrBlank() -> req.description
            req.serviceType != null -> req.serviceType.toPretty()
            else -> "Untitled Project"
        }

        val deadlineFromRemote = req.deadline.toUiDate()
        tvDeadline.text = deadlineFromRemote
        val displayFileName = req.customName ?: req.originalFileName ?: "Student File"
        val student = req.studentName ?: "Student"
        tvStudentAndDeadline.text = student

        lifecycleScope.launch(Dispatchers.IO) {
            val localIso = tryFindLocalDeadlineIso(req)
            val localUi = isoToUiDate(localIso)
            val finalDeadline =
                if (deadlineFromRemote != "—") deadlineFromRemote else (localUi ?: "—")
            withContext(Dispatchers.Main) {
                tvDeadline.text = finalDeadline
                tvStudentAndDeadline.text = "$displayFileName     $finalDeadline"
            }
        }

        tvServiceName.text = req.serviceType?.toPretty() ?: "Other"

        tvDescription.text = if (!req.description.isNullOrBlank()) {
            req.description!!.trim()
        } else {
            "—"
        }

        // Annotated/feedback file visibility
        val hasFeedback = !req.feedbackFileUrl.isNullOrEmpty()
        tvFilesCount.text = if (hasFeedback) "2" else "1"
        tvStudentFileName.text = displayFileName
        tvFeedbackFileName.text = req.feedbackFileName ?: "Feedback/Annotated File"
        rowFeedbackFile.visibility = if (hasFeedback) View.VISIBLE else View.GONE

        // Quote summary
        val amount = req.quotationAmount
        val currency = (req.quotationCurrency ?: "").ifBlank { "USD" }

        val hasQuote = amount != null

        if (hasQuote) {
            tvQuoteSummary.visibility = View.VISIBLE
            tvQuoteSummary.text = buildString {
                append(currency)
                append(" ")
                append(String.format(Locale.US, "%.2f", amount))
            }
        } else {
            tvQuoteSummary.visibility = View.VISIBLE
            tvQuoteSummary.text = "No quotation available"
        }

        tvDocName.text = req.originalFileName ?: displayFileName

        // ✅ Robust Completed check (handles case & whitespace)
        val isCompleted = req.status
            ?.trim()
            ?.equals("Completed", ignoreCase = true) == true

        // Update button visibilities that depend on student/consultant, quote presence, and completion
        applyRoleVisibility(getCurrentUserRole(), hasQuote, isCompleted)
    }

    // ---------------- Role-driven visibility ----------------
    private fun applyRoleVisibility(
        role: UserRole,
        hasQuote: Boolean? = null,
        isCompleted: Boolean = false
    ) {
        val isStudent = role == UserRole.STUDENT
        val isConsultant = role == UserRole.CONSULTANT

        val showQuoteActions = hasQuote ?: true
        setVisible(btnAcceptQuote, isStudent && showQuoteActions)
        setVisible(btnDeclineQuote, isStudent && showQuoteActions)

        setVisible(consultantCard, isStudent)

        setVisible(cardQualitativeStudy, isConsultant)

        // Hide feedback upload when request is completed
        setVisible(btnUploadFeedback, isConsultant && !isCompleted)

        // Hide consultant preview when request is completed
        setVisible(btnConsultantPreview, isConsultant && !isCompleted)

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
        val sur = node.child("surname").getValue(String::class.java)?.trim().orEmpty()
        val name = when {
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
        val hrs = mins / 60
        val days = hrs / 24
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            hrs < 24 -> "${hrs}h ago"
            else -> "${days}d ago"
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
            val byRemote =
                req.id?.let { runCatching { localRepos.requests.findByRemoteId(it) }.getOrNull() }
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
        return try {
            src.parse(iso)?.let(dst::format)
        } catch (_: ParseException) {
            null
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, fileProviderAuthority, file)
            val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/pdf"
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
            // 1) Get signed URL from backend
            when (val signed = docsRepo.getSignedUrl(documentId)) {
                is NetResult.Ok -> {
                    val url = signed.data

                    // 2) Download the actual file using the URL
                    when (val saved = docsRepo.downloadFromUrlToDisk(url, preferredName)) {
                        is NetResult.Ok -> withContext(Dispatchers.Main) {
                            val file = saved.data
                            val intent =
                                Intent(this@TaskDetailsActivity, DocumentViewerActivity::class.java)
                                    .putExtra(
                                        DocumentViewerActivity.EXTRA_FILE_PATH,
                                        file.absolutePath
                                    )
                                    .putExtra(
                                        DocumentViewerActivity.EXTRA_REQUEST_ID,
                                        currentReq?.id
                                    )
                                    .putExtra(
                                        DocumentViewerActivity.EXTRA_DISPLAY_NAME,
                                        preferredName
                                    )
                                    .putExtra(
                                        DocumentViewerActivity.EXTRA_CAN_ANNOTATE,
                                        getCurrentUserRole() == UserRole.CONSULTANT
                                    )

                            startActivity(intent)
                        }

                        is NetResult.Err -> withContext(Dispatchers.Main) {
                            toast("Download failed: ${saved.message}")
                        }
                    }
                }

                is NetResult.Err -> {
                    withContext(Dispatchers.Main) {
                        toast("Unable to fetch file URL: ${signed.message}")
                    }
                }
            }
        }
    }

    private fun enqueueSystemDownload(url: String, fileName: String) {
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
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
        val rawName = req.originalFileName ?: req.customName ?: "${docId}.bin"
        val fileName = ensurePdfExtension(rawName)
        downloadAndOpenInApp(docId, fileName)
    }

    /** Generate a simple Quote PDF with CiteWise logo in the header and a table of fields. */
    private fun generateQuotePdf(req: ServiceRequestDto): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4-ish
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            color = Color.BLACK
        }

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            textSize = 12f
            color = Color.BLACK
        }

        // Header: logo + text
        var topY = 40f
        try {
            val logoRaw = BitmapFactory.decodeResource(resources, R.drawable.citewise_logo)
            if (logoRaw != null) {
                val desiredWidth = 80
                val scaledHeight = logoRaw.height * desiredWidth / logoRaw.width
                val logo =
                    Bitmap.createScaledBitmap(logoRaw, desiredWidth, scaledHeight, true)
                canvas.drawBitmap(logo, 40f, topY, null)
                logo.recycle()
                logoRaw.recycle()
            }
        } catch (_: Throwable) {
            // If logo fails, just ignore and draw text
        }

        canvas.drawText("CiteWise Quote", 150f, topY + 30f, titlePaint)

        // Table start
        topY = 120f
        val leftX = 40f
        val colDividerX = 220f
        val rightX = 555f
        val rowHeight = 32f

        // Header row background
        val headerPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.FILL
        }
        canvas.drawRect(leftX, topY, rightX, topY + rowHeight, headerPaint)

        // Header row text
        canvas.drawText(
            "Field",
            leftX + 10f,
            topY + 22f,
            bodyPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText("Value", colDividerX + 10f, topY + 22f, bodyPaint)

        // Reset body font
        bodyPaint.typeface = Typeface.DEFAULT

        // Rows
        val rows = mutableListOf<Pair<String, String>>()

        val serviceName = req.serviceType?.toPretty() ?: "N/A"
        val amountText = req.quotationAmount?.let {
            val currency = (req.quotationCurrency ?: "").ifBlank { "USD" }
            "$currency ${String.format(Locale.US, "%.2f", it)}"
        } ?: "N/A"

        val wordsText = req.quotationWords?.toString() ?: "N/A"
        val deadlineText = req.deadline.toUiDate().ifBlank { "N/A" }
        val requestIdText = req.id ?: "N/A"

        rows += "Service" to serviceName
        rows += "Amount" to amountText
        rows += "Words" to wordsText
        rows += "Deadline" to deadlineText
        rows += "Request ID" to requestIdText

        var currentY = topY + rowHeight

        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        for ((label, value) in rows) {
            // Row border
            canvas.drawRect(leftX, currentY, rightX, currentY + rowHeight, borderPaint)

            canvas.drawText(label, leftX + 10f, currentY + 22f, bodyPaint)
            canvas.drawText(value, colDividerX + 10f, currentY + 22f, bodyPaint)

            currentY += rowHeight
        }

        doc.finishPage(page)

        val outFile = File(cacheDir, "quote_${req.id ?: "temp"}.pdf")
        FileOutputStream(outFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()

        return outFile
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

private fun ensurePdfExtension(name: String): String {
    // If it already has an extension, keep it
    return if (name.contains('.')) name else "$name.pdf"
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 *
 * Android Developer. 2024. “Grant Partial Access to Photos and Videos”.
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Knowledge. 2023a. “Bottom Navigation Bar in Android Studio Using Java | Explanation”.
 * https://www.youtube.com/watch?v=0x5kmLY16qE
 * [accessed 20 September 2023].
 *
 * Android Knowledge. 2023b. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
 * https://www.youtube.com/watch?v=oGyQMBKPuNY
 * [accessed 21 September 2025].
 *
 * Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
 * https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494
 * [accessed 30 September 2025].
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2017. “How to Use Glide Image Loader Library in Android Apps?”
 * https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/
 * [accessed 30 September 2025].
 *
 * GeeksforGeeks. 2020. “SimpleAdapter in Android with Example”.
 * https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/
 * [accessed 19 August 2025].
 *
 * GeeksforGeeks. 2021. “State ProgressBar in Android”.
 * https://www.geeksforgeeks.org/android/state-progressbar-in-android/
 * [accessed 22 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”.
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */

