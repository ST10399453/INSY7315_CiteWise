// app/src/main/java/com/example/citewise_mobile/TaskDetailsActivity.kt
package com.example.citewise_mobile

import android.content.ActivityNotFoundException
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
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toUiDate
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.offline.LocalRepos
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TaskDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST = "extra_request"
        // Must match your <provider> authority in AndroidManifest.xml
        private const val FILE_PROVIDER_AUTH = "com.example.citewise_mobile.fileprovider"
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
    private lateinit var btnViewStudentFile: MaterialButton // NEW: view inline

    private lateinit var tvDescription: TextView
    private lateinit var progressStage: LinearProgressIndicator

    // Local DB + documents
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

        // If you added a “View PDF” button to the row layout, bind it here.
        // Otherwise you can reuse Download button’s click for view as well.
        btnViewStudentFile = findViewById(R.id.btnViewStudentFile)

        // Additional info
        tvDescription = findViewById(R.id.tvDescription)

        // Progress
        progressStage = findViewById(R.id.progressStage)

        // Retrieve the passed object
        val req = intent.getSerializableExtra(EXTRA_REQUEST) as? ServiceRequestDto
        if (req != null) bindRequest(req)
    }

    private fun bindRequest(req: ServiceRequestDto) {
        // Priority chip
        chipPriority.text = req.priority?.toPretty() ?: "—"

        // Project title: prefer Service Title → Description → Category → fallback
        tvProjectName.text = when {
            !req.title.isNullOrBlank() -> req.title
            !req.description.isNullOrBlank() -> req.description
            req.serviceType != null -> req.serviceType.toPretty()
            else -> "Untitled Project"
        }

        // Deadline — prefer Firestore (FlexTime) → fallback to local Room deadlineIso
        val deadlineFromFire = req.deadline?.toUiDate()
        tvDeadline.text = deadlineFromFire ?: "—"
        tvStudentAndDeadline.text = "Student     ${deadlineFromFire ?: "—"}"

        lifecycleScope.launch(Dispatchers.IO) {
            // 1) fallback to Room deadlineIso if Firestore missing
            val finalDeadline = if (deadlineFromFire == null) {
                val deadlineIso = tryFindLocalDeadlineIso(req)
                isoToUiDate(deadlineIso) ?: "—"
            } else deadlineFromFire

            // 2) Student first name
            val firstName = tryFindFirstName(req)

            withContext(Dispatchers.Main) {
                tvDeadline.text = finalDeadline
                tvStudentAndDeadline.text = "$firstName     $finalDeadline"
            }
        }

        // Service section
        tvServiceName.text = req.serviceType?.toPretty() ?: "Other"

        // Files
        val hasFeedback = !req.feedbackFileUrl.isNullOrEmpty()
        tvFilesCount.text = if (hasFeedback) "2" else "1"

        tvStudentFileName.text = req.originalFileName ?: "Student File"
        tvFeedbackFileName.text = req.feedbackFileName ?: "Feedback/Annotated File"
        rowFeedbackFile.visibility = if (hasFeedback) View.VISIBLE else View.GONE

        // DOWNLOAD original (streams through API → saves to app storage)
        btnDownloadStudentFile.setOnClickListener {
            val docId = req.documentId
            if (docId.isNullOrBlank()) {
                toast("Document not available yet.")
                return@setOnClickListener
            }
            val fileName = req.originalFileName ?: "$docId.pdf"
            downloadAndMaybeOpen(docId, fileName, openAfter = false)
        }

        // VIEW original (download silently then open with FileProvider)
        btnViewStudentFile.setOnClickListener {
            val docId = req.documentId
            if (docId.isNullOrBlank()) {
                toast("Document not available yet.")
                return@setOnClickListener
            }
            val fileName = req.originalFileName ?: "$docId.pdf"
            downloadAndMaybeOpen(docId, fileName, openAfter = true)
        }

        // Download feedback if you have a direct URL; otherwise you could mirror student logic if you also store a feedback documentId
        btnDownloadFeedbackFile.setOnClickListener {
            val url = req.feedbackFileUrl
            if (url.isNullOrBlank()) {
                toast("No feedback file yet.")
                return@setOnClickListener
            }
            openUrl(url)
        }

        // Description (additional info)
        tvDescription.text = req.description?.takeIf { it.isNotBlank() }
            ?: "No additional information provided."

        // Progress bar
        val progress = statusToProgress(req.status.orEmpty())
        progressStage.setProgressCompat(progress, true)
    }

    /** Download document (via DocumentsRepository → /documents/{id}/file) and optionally open it. */
    private fun downloadAndMaybeOpen(documentId: String, preferredName: String, openAfter: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = docsRepo.downloadToDisk(documentId, preferredName)) {
                is NetResult.Ok -> withContext(Dispatchers.Main) {
                    val file = result.data // <- use .data (fixes unresolved 'value')
                    if (openAfter) openPdf(file) else toast("Saved to ${file.absolutePath}")
                }
                is NetResult.Err -> withContext(Dispatchers.Main) {
                    toast("Download failed: ${result.message}")
                }
            }
        }
    }

    /** Try to find a user first name from local Room by userId, fallback to DTO.studentName, else "Student". */
    private suspend fun tryFindFirstName(req: ServiceRequestDto): String = withContext(Dispatchers.IO) {
        val uid = req.userId
        if (!uid.isNullOrBlank()) {
            val user = runCatching {
                // If you add a direct DAO: localRepos.users.getById(uid)
                localRepos.users.getAll().firstOrNull { it.uid == uid }
            }.getOrNull()
            val fromDb = user?.firstName?.takeIf { it.isNotBlank() }
            if (!fromDb.isNullOrBlank()) return@withContext fromDb
        }
        val fromDto = req.studentName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        return@withContext fromDto ?: "Student"
    }

    /** Try to get deadlineIso from Room ServiceRequestEntity via remoteId, else by documentId. */
    private suspend fun tryFindLocalDeadlineIso(req: ServiceRequestDto): String? = withContext(Dispatchers.IO) {
        val remoteId = req.id
        val byRemote = if (!remoteId.isNullOrBlank()) {
            runCatching { localRepos.requests.findByRemoteId(remoteId) }.getOrNull()
        } else null

        byRemote?.deadlineIso ?: run {
            val docId = req.documentId
            if (docId.isNullOrBlank()) return@run null
            val all = runCatching { localRepos.requests.getAll() }.getOrNull().orEmpty()
            all.firstOrNull { it.documentId == docId }?.deadlineIso
        }
    }

    /** Convert ISO-8601 Z (yyyy-MM-dd'T'HH:mm:ss'Z') → yyyy-MM-dd for UI. */
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

    /** Open a downloaded PDF using FileProvider (works with scoped storage). */
    private fun openPdf(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTH, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast("No PDF viewer installed.")
        } catch (t: Throwable) {
            toast("Unable to open file: ${t.message}")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Throwable) {
            toast("No app can handle this link")
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

    private fun statusToProgress(status: String): Int = when (status.trim().lowercase()) {
        "submitted", "pending" -> 25
        "assigned", "in_progress", "in progress" -> 50
        "feedback", "awaiting_feedback", "feedback ready" -> 75
        "complete", "completed", "done" -> 100
        else -> 25
    }
}
