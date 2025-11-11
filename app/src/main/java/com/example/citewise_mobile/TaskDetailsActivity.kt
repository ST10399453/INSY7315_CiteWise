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
import java.util.*

class TaskDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST = "extra_request"
    }

    private val fileProviderAuthority by lazy {
        "${applicationContext.packageName}.fileprovider"
    }

    private val localRepos by lazy { LocalRepos(this) }
    private val docsRepo by lazy {
        DocumentsRepository(
            RetrofitInstance.documentsApi,
            applicationContext
        )
    }

    // Views
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_details)
        initViews()
        (intent.getSerializableExtra(EXTRA_REQUEST) as? ServiceRequestDto)?.let { bindRequest(it) }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

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
    }

    private fun bindRequest(req: ServiceRequestDto) {
        // Priority chip
        chipPriority.text = req.priority?.toPretty() ?: "—"

        // Project name (prefer customName → description → serviceType)
        tvProjectName.text = when {
            !req.customName.isNullOrBlank() -> req.customName
            !req.description.isNullOrBlank() -> req.description
            req.serviceType != null -> req.serviceType.toPretty()
            else -> "Untitled Project"
        }

        // Use FlexTime as-is for remote deadline
        val deadlineFromRemote = req.deadline.toUiDate() // returns "—" when null

        tvDeadline.text = deadlineFromRemote

        // Show filename and deadline together
        val displayFileName = req.customName ?: req.originalFileName ?: "Student File"
        tvStudentAndDeadline.text = "$displayFileName     $deadlineFromRemote"

        // If remote is missing ("—"), try local ISO fallback
        lifecycleScope.launch(Dispatchers.IO) {
            val localIso = tryFindLocalDeadlineIso(req)
            val localUi = isoToUiDate(localIso)
            val finalDeadline = if (deadlineFromRemote != "—") deadlineFromRemote else (localUi ?: "—")

            withContext(Dispatchers.Main) {
                tvDeadline.text = finalDeadline
                tvStudentAndDeadline.text = "$displayFileName     $finalDeadline"
            }
        }

        // Service name
        tvServiceName.text = req.serviceType?.toPretty() ?: "Other"

        // Files section
        val hasFeedback = !req.feedbackFileUrl.isNullOrEmpty()
        tvFilesCount.text = if (hasFeedback) "2" else "1"

        tvStudentFileName.text = displayFileName
        tvFeedbackFileName.text = req.feedbackFileName ?: "Feedback/Annotated File"
        rowFeedbackFile.visibility = if (hasFeedback) View.VISIBLE else View.GONE

        // Download student file
        btnDownloadStudentFile.setOnClickListener {
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = displayFileName
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

        // View student file
        btnViewStudentFile.setOnClickListener {
            val docId = req.documentId ?: return@setOnClickListener toast("Document not available yet.")
            val fileName = displayFileName
            downloadAndOpenInApp(docId, fileName)
        }

        // Download feedback
        btnDownloadFeedbackFile.setOnClickListener {
            val url = req.feedbackFileUrl
            if (url.isNullOrBlank()) return@setOnClickListener toast("No feedback file yet.")
            openUrl(url)
        }

        // Description
        tvDescription.text =
            req.description?.takeIf { it.isNotBlank() } ?: "No additional information provided."

        // Progress bar
        progressStage.setProgressCompat(statusToProgress(req.status.orEmpty()), true)
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

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            toast("No app can handle this link.")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
}
