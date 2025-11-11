package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.toUiDate
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.offline.LocalRepos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsultantTaskDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST = "extra_request"
    }

    private lateinit var currentRequest: ServiceRequestDto

    // UI Elements
    private lateinit var tvServiceName: TextView
    private lateinit var chipPriority: TextView
    private lateinit var tvDeadlineInfo: TextView
    private lateinit var tvStudentInfo: TextView
    private lateinit var tvQualitativeStudyFileName: TextView
    private lateinit var btnUploadFeedback: Button

    // Quote table
    private lateinit var tvWordCount: TextView
    private lateinit var tvCostPerWord: TextView
    private lateinit var tvUrgencyIncrease: TextView
    private lateinit var tvTotalQuote: TextView

    // Repos
    private val localRepos by lazy { LocalRepos(this) }
    private val docsRepo by lazy { DocumentsRepository(getDocumentsApi(), applicationContext) }
    private fun getDocumentsApi() = com.example.citewise_mobile.api.RetrofitInstance.documentsApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_consultant_task_details)

        // Retrieve data
        val requestDto = intent.getSerializableExtra(EXTRA_REQUEST) as? ServiceRequestDto
        if (requestDto == null) {
            Toast.makeText(this, "Task data missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentRequest = requestDto

        // Back button (ImageView in XML)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // --- Task Info Card ---
        tvServiceName  = findViewById(R.id.tvServiceName)
        chipPriority   = findViewById(R.id.chipPriority)
        tvDeadlineInfo = findViewById(R.id.tvDeadline)
        tvStudentInfo  = findViewById(R.id.tvStudent)

        // --- Files section ---
        val cardFileLayout: View = findViewById(R.id.cardQualitativeStudy)
        // tvDocName MUST exist in the XML inside cardQualitativeStudy
        tvQualitativeStudyFileName = cardFileLayout.findViewById(R.id.tvDocName)

        btnUploadFeedback = findViewById(R.id.btnUploadFeedback)
        cardFileLayout.setOnClickListener { handleOpenFile() }
        btnUploadFeedback.setOnClickListener { handleUploadFeedback() }

        // --- Quote table ---
        tvWordCount       = findViewById(R.id.tvWordCount)
        tvCostPerWord     = findViewById(R.id.tvCostPerWord)
        tvUrgencyIncrease = findViewById(R.id.tvUrgencyIncrease)
        tvTotalQuote      = findViewById(R.id.tvTotalQuote)

        // --- Footer actions ---
        findViewById<Button>(R.id.btnRevise).setOnClickListener { handleRevise() }
        findViewById<Button>(R.id.btnSendToStudent).setOnClickListener { handleSendToStudent() }
        findViewById<Button>(R.id.btnDownloadPdf).setOnClickListener { handleDownloadPdf() }

        // Bind data
        bindRequest(currentRequest)
    }

    private fun bindRequest(req: ServiceRequestDto) {
        val priority = req.priority ?: ServicePriority.LOW

        // Task info
        tvServiceName.text = req.serviceType?.toPretty() ?: "Untitled Task"
        chipPriority.text  = priority.toPrettyTag()

        // Async fields: deadline & student name
        lifecycleScope.launch(Dispatchers.IO) {
            val deadlineText = req.deadline.toUiDate()
            val studentName  = tryFindStudentName(req)
            withContext(Dispatchers.Main) {
                tvDeadlineInfo.text = "Deadline: $deadlineText"
                tvStudentInfo.text  = "Student: $studentName"
            }
        }

        // Files
        tvQualitativeStudyFileName.text = req.originalFileName ?: "Student Document"

        // Quote (placeholder demo values)
        tvWordCount.text       = "8 000"
        tvCostPerWord.text     = "15c"
        tvUrgencyIncrease.text = "n/a"
        tvTotalQuote.text      = "R 1 200"
    }

    // --- Actions ---

    private fun handleOpenFile() {
        val docId = currentRequest.documentId ?: return toast("Document not available yet.")
        val fileName = currentRequest.originalFileName ?: "$docId.bin"
        downloadAndOpenInApp(docId, fileName)
    }

    private fun handleUploadFeedback() {
        toast("Opening file picker for feedback upload...")
    }

    private fun handleRevise() {
        toast("Opening revision tools...")
    }

    private fun handleSendToStudent() {
        toast("Status updated and sent to student.")
    }

    private fun handleDownloadPdf() {
        toast("Downloading Quote PDF...")
    }

    // --- Helpers ---

    private fun downloadAndOpenInApp(documentId: String, preferredName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = docsRepo.downloadToDisk(documentId, preferredName)) {
                is NetResult.Ok -> withContext(Dispatchers.Main) {
                    val file = result.data
                    val intent = Intent(
                        this@ConsultantTaskDetailsActivity,
                        DocumentViewerActivity::class.java
                    ).putExtra(DocumentViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                    startActivity(intent)
                }
                is NetResult.Err -> withContext(Dispatchers.Main) {
                    toast("Download failed: ${result.message}")
                }
            }
        }
    }

    private suspend fun tryFindStudentName(req: ServiceRequestDto): String =
        withContext(Dispatchers.IO) {
            val uid = req.userId
            if (!uid.isNullOrBlank()) {
                val user = runCatching {
                    localRepos.users.getAll().firstOrNull { it.uid == uid }
                }.getOrNull()
                if (user != null) return@withContext "${user.firstName} ${user.surname}"
            }
            return@withContext req.studentName?.takeIf { it.isNotBlank() } ?: "Student"
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
