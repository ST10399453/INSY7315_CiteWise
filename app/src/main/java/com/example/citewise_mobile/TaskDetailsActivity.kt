package com.example.citewise_mobile

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLConnection
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TaskDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST = "extra_request"
    }

    private val fileProviderAuthority: String by lazy {
        "${applicationContext.packageName}.fileprovider"
    }

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

    private val localRepos by lazy { LocalRepos(this) }
    private val docsRepo by lazy { DocumentsRepository(RetrofitInstance.documentsApi, applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_details)

        // Back button
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Top strip
        chipPriority = findViewById(R.id.chipPriority)
        tvProjectName = findViewById(R.id.tvProjectName)
        tvDeadline = findViewById(R.id.tvDeadline)
        tvStudentAndDeadline = findViewById(R.id.tvStudentAndDeadline)

        // Service section
        tvServiceName = findViewById(R.id.tvServiceName)

        // Files section
        tvFilesCount = findViewById(R.id.tvFilesCount)
        tvStudentFileName = findViewById(R.id.tvStudentFileName)
        tvFeedbackFileName = findViewById(R.id.tvFeedbackFileName)
        rowFeedbackFile = findViewById(R.id.rowFeedbackFile)
        btnDownloadStudentFile = findViewById(R.id.btnDownloadStudentFile)
        btnDownloadFeedbackFile = findViewById(R.id.btnDownloadFeedbackFile)
        btnViewStudentFile = findViewById(R.id.btnViewStudentFile)

        // Additional info
        tvDescription = findViewById(R.id.tvDescription)

        // Progress
        progressStage = findViewById(R.id.progressStage)

        // Get request from Intent
        (intent.getSerializableExtra(EXTRA_REQUEST) as? ServiceRequestDto)?.let { bindRequest(it) }
    }

    private fun bindRequest(req: ServiceRequestDto) {
        // Priority chip
        chipPriority.text = req.priority?.toPretty() ?: "—"

        // Title
        tvProjectName.text = when {
            //!req.title.isNullOrBlank() -> req.title
            !req.description.isNullOrBlank() -> req.description
            req.serviceType != null -> req.serviceType.toPretty()
            else -> "Untitled Project"
        }

        // Deadline (prefer remote, fallback to local Room)
        val deadlineFromRemote = req.deadline?.toUiDate()
        tvDeadline.text = deadlineFromRemote ?: "—"
        tvStudentAndDeadline.text = "Student     ${deadlineFromRemote ?: "—"}"

        lifecycleScope.launch(Dispatchers.IO) {
            val finalDeadline = deadlineFromRemote ?: isoToUiDate(tryFindLocalDeadlineIso(req)) ?: "—"
            val firstName = tryFindFirstName(req)

            withContext(Dispatchers.Main) {
                tvDeadline.text = finalDeadline
                tvStudentAndDeadline.text = "$firstName"
            }
        }

        // Service
        tvServiceName.text = req.serviceType?.toPretty() ?: "Other"

        // Files block
        val hasFeedback = !req.feedbackFileUrl.isNullOrEmpty()
        tvFilesCount.text = if (hasFeedback) "2" else "1"

        tvStudentFileName.text = req.originalFileName ?: "Student File"
        tvFeedbackFileName.text = req.feedbackFileName ?: "Feedback/Annotated File"
        rowFeedbackFile.visibility = if (hasFeedback) View.VISIBLE else View.GONE

        // Download (public Downloads via DownloadManager)
        btnDownloadStudentFile.setOnClickListener {
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = req.originalFileName ?: "$docId"
            lifecycleScope.launch(Dispatchers.IO) {
                when (val s = docsRepo.getSignedUrl(docId)) {
                    is NetResult.Ok -> withContext(Dispatchers.Main) {
                        enqueueSystemDownload(s.data, fileName)
                    }
                    is NetResult.Err -> withContext(Dispatchers.Main) {
                        // fallback: save privately if signed URL not available
                        when (val saved = docsRepo.downloadToDisk(docId, fileName)) {
                            is NetResult.Ok -> toast("Saved to ${saved.data.absolutePath}")
                            is NetResult.Err -> toast("Download failed: ${saved.message}")
                        }
                    }
                }
            }
        }

        // View (save to app Downloads then in-app viewer)
        btnViewStudentFile.setOnClickListener {
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = req.originalFileName ?: "$docId"
            downloadAndOpenInApp(docId, fileName)
        }

        // Download feedback (direct URL if provided)
        btnDownloadFeedbackFile.setOnClickListener {
            val url = req.feedbackFileUrl
            if (url.isNullOrBlank()) {
                toast("No feedback file yet.")
                return@setOnClickListener
            }
            openUrl(url)
        }

        // Description
        tvDescription.text = req.description?.takeIf { it.isNotBlank() } ?: "No additional information provided."

        // Progress
        progressStage.setProgressCompat(statusToProgress(req.status.orEmpty()), true)
    }

    /** Download privately then open in in-app viewer. */
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

    /** System Downloads via DownloadManager (public, visible, notification). */
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

    /** Look up first name from local DB → DTO fallback → "Student". */
    private suspend fun tryFindFirstName(req: ServiceRequestDto): String = withContext(Dispatchers.IO) {
        val uid = req.userId
        if (!uid.isNullOrBlank()) {
            val user = runCatching { localRepos.users.getAll().firstOrNull { it.uid == uid } }.getOrNull()
            val fromDb = user?.firstName?.takeIf { it.isNotBlank() }
            if (!fromDb.isNullOrBlank()) return@withContext fromDb
        }
        val fromDto = req.studentName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        return@withContext fromDto ?: "Student"
    }

    /** Try Room.deadlineIso by remoteId, else by documentId. */
    private suspend fun tryFindLocalDeadlineIso(req: ServiceRequestDto): String? = withContext(Dispatchers.IO) {
        val byRemote = req.id?.let { runCatching { localRepos.requests.findByRemoteId(it) }.getOrNull() }
        byRemote?.deadlineIso ?: run {
            val docId = req.documentId ?: return@run null
            val all = runCatching { localRepos.requests.getAll() }.getOrNull().orEmpty()
            all.firstOrNull { it.documentId == docId }?.deadlineIso
        }
    }

    /** Convert ISO-8601 Z to yyyy-MM-dd for UI. */
    private fun isoToUiDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dst = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val d: Date? = src.parse(iso)
            d?.let(dst::format)
        } catch (_: ParseException) {
            null
        }
    }

    /** (Optional legacy) open with external app by MIME. */
    private fun openFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, fileProviderAuthority, file)
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

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            toast("No app can handle this link.")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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

    private fun statusToProgress(status: String): Int = when (status.trim().lowercase(Locale.ROOT)) {
        "submitted", "pending" -> 25
        "assigned", "in_progress", "in progress" -> 50
        "feedback", "awaiting_feedback", "feedback ready" -> 75
        "complete", "completed", "done" -> 100
        else -> 25
    }
}
