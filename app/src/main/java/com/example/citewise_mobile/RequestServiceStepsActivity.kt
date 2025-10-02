package com.example.citewise_mobile

import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.citewise_mobile.data.ServicePriority
import com.example.citewise_mobile.data.ServiceReview
import com.example.citewise_mobile.data.ServiceType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RequestServiceStepsActivity : AppCompatActivity() {

    // ---- UI / Flow ----
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHeader: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var sections: List<View>
    private var currentStep = 0
    private val totalSteps = 5 // 4 steps + success

    // Step 1
    private lateinit var serviceSpinner: Spinner
    private lateinit var services: List<String>

    // Step 2
    private lateinit var etAdditionalInfo: TextInputEditText

    // Step 3
    private lateinit var etDocName: TextInputEditText
    private lateinit var btnAttachFile: MaterialButton
    private lateinit var tvFileName: TextView
    private lateinit var progressUpload: ProgressBar
    private var pickedFileUri: Uri? = null

    // Step 4
    private lateinit var urgencySpinner: Spinner
    private lateinit var etDeadline: TextInputEditText

    // Cached state
    private var selectedService: String? = null
    private var additionalInfo: String? = null
    private var docName: String? = null
    private var urgencyLevel: String? = null
    private var deadlineText: String? = null

    // SAF OpenDocument (documents only)
    private lateinit var pickDocLauncher: ActivityResultLauncher<Array<String>>

    // Firebase
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // keep doc size under Firestore ~1MiB limit (allow headroom)
    private val MAX_FIRESTORE_BLOB_BYTES = 900 * 1024 // 900 KB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_request_services)

        // edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // documents-only picker with persistable permission
        pickDocLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {}
                pickedFileUri = uri
                tvFileName.text = displayNameFromUri(uri) ?: getString(R.string.file_selected)
            } else {
                pickedFileUri = null
                tvFileName.text = getString(R.string.no_file_selected)
            }
        }

        bindViews()
        setupSpinners()
        setupListeners()

        showStep(0, forward = true)
        animateProgress(0, stepToPercent(0))
    }

    private fun bindViews() {
        progressBar   = findViewById(R.id.progressService)
        tvHeader      = findViewById(R.id.tvHeader)
        btnNext       = findViewById(R.id.btnNext)

        sections = listOf(
            findViewById(R.id.sectionStep1),
            findViewById(R.id.sectionStep2),
            findViewById(R.id.sectionStep3),
            findViewById(R.id.sectionStep4),
            findViewById(R.id.sectionStep5Success)
        )

        // Step 1
        serviceSpinner = findViewById(R.id.serviceSpinner)

        // Step 2
        etAdditionalInfo = findViewById(R.id.etAdditionalInfo)

        // Step 3
        etDocName       = findViewById(R.id.etDocName)
        btnAttachFile   = findViewById(R.id.btnAttachFile)
        tvFileName      = findViewById(R.id.tvFileName)
        progressUpload  = findViewById(R.id.progressUpload)

        // Step 4
        urgencySpinner  = findViewById(R.id.urgencySpinner)
        etDeadline      = findViewById(R.id.etDeadline)
    }

    private fun setupSpinners() {
        services = resources.getStringArray(R.array.services_array).toList()
        val serviceAdapter = ArrayAdapter(
            this, R.layout.item_service_selected, android.R.id.text1, services
        ).apply { setDropDownViewResource(R.layout.item_service_dropdown) }
        serviceSpinner.adapter = serviceAdapter

        val urgencyItems = resources.getStringArray(R.array.urgency_array).toList()
        val urgencyAdapter = ArrayAdapter(
            this, R.layout.item_service_selected, android.R.id.text1, urgencyItems
        ).apply { setDropDownViewResource(R.layout.item_service_dropdown) }
        urgencySpinner.adapter = urgencyAdapter
    }

    private fun setupListeners() {
        btnNext.setOnClickListener {
            when (currentStep) {
                0 -> { // Step 1
                    if (serviceSpinner.selectedItem == null) {
                        toast(getString(R.string.select_service_first)); return@setOnClickListener
                    }
                    selectedService = services[serviceSpinner.selectedItemPosition]
                }
                1 -> { // Step 2
                    additionalInfo = etAdditionalInfo.text?.toString()?.trim()
                }
                2 -> { // Step 3 (validate only)
                    docName = etDocName.text?.toString()?.trim()
                    if (pickedFileUri == null) {
                        toast(getString(R.string.choose_file_first)); return@setOnClickListener
                    }
                    if (docName.isNullOrEmpty()) {
                        toast(getString(R.string.enter_document_name)); return@setOnClickListener
                    }
                }
                3 -> { // Step 4 -> SAVE EVERYTHING
                    urgencyLevel = urgencySpinner.selectedItem?.toString()
                    deadlineText = etDeadline.text?.toString()
                    if (urgencyLevel.isNullOrEmpty()) {
                        toast(getString(R.string.select_urgency)); return@setOnClickListener
                    }
                    val deadlineTs = parseDeadlineOrNull(deadlineText)
                    setLoading(true)
                    saveAllAtEnd(deadlineTs,
                        onSuccess = {
                            setLoading(false)
                            goNextStep() // success screen
                        },
                        onError = { msg ->
                            setLoading(false)
                            toast(msg)
                        }
                    )
                    return@setOnClickListener
                }
            }
            goNextStep()
        }

        btnAttachFile.setOnClickListener { pickDocLauncher.launch(allowedMimeTypes()) }
        etDeadline.setOnClickListener { showDatePicker() }
        findViewById<MaterialButton>(R.id.btnDone)?.setOnClickListener { finish() }
    }

    private fun goNextStep() {
        if (currentStep < totalSteps - 1) {
            val next = currentStep + 1
            animateProgress(stepToPercent(currentStep), stepToPercent(next))
            currentStep = next
            showStep(currentStep, forward = true)
            if (currentStep == totalSteps - 1) btnNext.text = getString(R.string.done)
        } else finish()
    }

    private fun showStep(stepIndex: Int, forward: Boolean) {
        tvHeader.text = when (stepIndex) {
            0 -> getString(R.string.step_1)
            1 -> getString(R.string.step_2)
            2 -> getString(R.string.step_3)
            3 -> getString(R.string.step_4)
            else -> getString(R.string.step_done)
        }

        sections.forEachIndexed { index, section ->
            if (index == stepIndex) {
                val animIn = if (forward) R.anim.slide_in_right else R.anim.slide_in_left
                section.startAnimation(AnimationUtils.loadAnimation(this, animIn))
                section.visibility = View.VISIBLE
            } else if (section.visibility == View.VISIBLE) {
                val animOut = if (forward) R.anim.slide_out_left else R.anim.slide_out_right
                section.startAnimation(AnimationUtils.loadAnimation(this, animOut))
                section.visibility = View.GONE
            } else section.visibility = View.GONE
        }
    }

    private fun animateProgress(from: Int, to: Int) {
        ObjectAnimator.ofInt(progressBar, "progress", from, to).apply {
            duration = 350
            start()
        }
    }

    private fun stepToPercent(step: Int): Int =
        ((step.coerceIn(0, totalSteps - 1).toFloat() / (totalSteps - 1)) * 100f).toInt()

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d -> etDeadline.setText(String.format("%02d/%02d/%04d", d, m + 1, y)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onBackPressed() {
        if (currentStep in 1..(totalSteps - 2)) {
            val prev = currentStep - 1
            animateProgress(stepToPercent(currentStep), stepToPercent(prev))
            currentStep = prev
            showStep(currentStep, forward = false)
        } else super.onBackPressed()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun setLoading(isLoading: Boolean) {
        btnNext.isEnabled = !isLoading
        btnAttachFile.isEnabled = !isLoading
        progressUpload.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnNext.text = when {
            isLoading -> getString(R.string.uploading)
            currentStep == totalSteps - 1 -> getString(R.string.done)
            else -> getString(R.string.next)
        }
    }

    // ===== Final save (Documents + serviceReviews) =====

    private fun saveAllAtEnd(
        deadlineTs: Timestamp?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uri = pickedFileUri ?: return onError(getString(R.string.choose_file_first))
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return onError(getString(R.string.not_signed_in))

        // read bytes & sanity check size
        val bytes = try { readAllBytes(uri, MAX_FIRESTORE_BLOB_BYTES + 1) }
        catch (e: Exception) { return onError("Failed to read file: ${e.message}") }
        if (bytes.size > MAX_FIRESTORE_BLOB_BYTES) {
            return onError("File is too large for Firestore (>${MAX_FIRESTORE_BLOB_BYTES / 1024} KB).")
        }

        val name = displayNameFromUri(uri) ?: (docName ?: "document")
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"

        // 1) Create the document (file + all step fields)
        val docData = hashMapOf(
            "uid" to uid,
            "service" to (selectedService ?: ""),
            "additionalInfo" to (additionalInfo ?: ""),
            "docName" to name,
            "mimeType" to mime,
            "content" to Blob.fromBytes(bytes),
            "urgency" to (urgencyLevel ?: ""),
            "deadline" to deadlineTs, // Timestamp? -> null if not provided
            "uploadedAt" to FieldValue.serverTimestamp(),
            "status" to "submitted"
        )

        firestore.collection("Documents")
            .add(docData)
            .addOnSuccessListener { docRef ->
                // 2) Create the service review linked to that document
                val review = ServiceReview(
                    userId = uid,
                    documentId = docRef.id,
                    consultantId = null,
                    serviceType = mapServiceType(selectedService),
                    description = additionalInfo.orEmpty(),
                    priority = mapPriority(urgencyLevel),
                    deadline = deadlineTs,
                    createdAt = null // set via serverTimestamp() below
                )
                firestore.collection("ServiceReviews")
                    .add(review.toMapWithServerTimestamp())
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e.message ?: "Failed to create review") }
            }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to create document") }
    }

    // ===== Helpers =====

    private fun allowedMimeTypes() = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain"
    )

    private fun displayNameFromUri(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val idx = c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    private fun readAllBytes(uri: Uri, hardLimit: Int): ByteArray {
        contentResolver.openInputStream(uri)?.use { input ->
            return readAll(input, hardLimit)
        }
        throw IllegalStateException("Cannot open input stream for URI")
    }

    private fun readAll(input: InputStream, hardLimit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val temp = ByteArray(16 * 1024)
        var read: Int
        var total = 0
        while (input.read(temp).also { read = it } != -1) {
            total += read
            if (total > hardLimit) break
            buffer.write(temp, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun parseDeadlineOrNull(text: String?): Timestamp? {
        if (text.isNullOrBlank()) return null
        // expects dd/MM/yyyy
        return try {
            val parts = text.trim().split("/")
            val d = parts[0].toInt()
            val m = parts[1].toInt() - 1
            val y = parts[2].toInt()
            val cal = Calendar.getInstance(Locale.getDefault()).apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            Timestamp(Date(cal.timeInMillis))
        } catch (_: Exception) {
            null
        }
    }

    private fun mapPriority(uiValue: String?): ServicePriority =
        when (uiValue?.trim()?.lowercase(Locale.getDefault())) {
            "high" -> ServicePriority.HIGH
            "medium" -> ServicePriority.MEDIUM
            "low" -> ServicePriority.LOW
            else -> ServicePriority.MEDIUM
        }

    private fun mapServiceType(uiValue: String?): ServiceType =
        when (uiValue?.trim()?.lowercase(Locale.getDefault())) {
            "proofreading & editing", "proofreading", "editing" -> ServiceType.PROOFREADING_EDITING
            "formatting & referencing", "formatting", "referencing" -> ServiceType.FORMATTING_REFERENCING
            "data analysis support", "data analysis" -> ServiceType.DATA_ANALYSIS_SUPPORT
            "research/methodology coaching", "research", "methodology" -> ServiceType.RESEARCH_METHODOLOGY_COACHING
            "translation" -> ServiceType.TRANSLATION
            else -> ServiceType.OTHER
        }
}