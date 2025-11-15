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
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.RequestsSyncWorker
import com.example.citewise_mobile.offline.ServiceRequestEntity
import com.example.citewise_mobile.offline.SyncState
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RequestServiceStepsActivity : AppCompatActivity() {

    // ---- UI / Flow ----
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHeader: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: View
    private lateinit var sections: List<View>
    private var currentStep = 0
    private val totalSteps = 5 // 4 steps + success

    // Step 1
    private lateinit var serviceRadioGroup: RadioGroup

    // Step 2
    private lateinit var etAdditionalInfo: TextInputEditText

    // Step 3 (Doc Name == customName)
    private lateinit var etDocName: TextInputEditText
    private lateinit var btnAttachFile: LinearLayout
    private lateinit var tvFileName: TextView
    private lateinit var progressUpload: ProgressBar
    private var pickedFileUri: Uri? = null

    // Step 4
    private lateinit var dropdownUrgency: AutoCompleteTextView
    private lateinit var etDeadline: TextInputEditText

    // Cached state
    private var selectedService: String? = null
    private var additionalInfo: String? = null
    private var customName: String? = null       // <--- docName is our customName
    private var urgencyLevel: String? = null
    private var deadlineText: String? = null

    // SAF OpenDocument (documents only)
    private lateinit var pickDocLauncher: ActivityResultLauncher<Array<String>>

    // Local Room access
    private val localRepos by lazy { LocalRepos(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_request_services)

        // Edge-to-edge insets
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
                } catch (_: SecurityException) { /* ignore */ }
                pickedFileUri = uri
                tvFileName.text = displayNameFromUri(uri) ?: getString(R.string.file_selected)
            } else {
                pickedFileUri = null
                tvFileName.text = getString(R.string.no_file_selected)
            }
        }

        bindViews()
        setupUrgencyDropdown()
        setupListeners()

        // Back arrow: go to previous step if possible
        btnBack.setOnClickListener {
            if (currentStep in 1..(totalSteps - 2)) {
                val prev = currentStep - 1
                animateProgress(stepToPercent(currentStep), stepToPercent(prev))
                currentStep = prev
                showStep(currentStep, forward = false)
                // ensure CTA label is correct when going back
                btnNext.text = if (currentStep == totalSteps - 1) {
                    getString(R.string.done)
                } else {
                    getString(R.string.next)
                }
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        showStep(0, forward = true)
        animateProgress(0, stepToPercent(0))
    }

    private fun bindViews() {
        progressBar   = findViewById(R.id.progressService)
        tvHeader      = findViewById(R.id.tvHeader)
        btnNext       = findViewById(R.id.btnNext)
        btnBack       = findViewById(R.id.btnBack)

        sections = listOf(
            findViewById(R.id.sectionStep1),
            findViewById(R.id.sectionStep2),
            findViewById(R.id.sectionStep3),
            findViewById(R.id.sectionStep4),
            findViewById(R.id.sectionStep5Success)
        )

        // Step 1
        serviceRadioGroup = findViewById(R.id.serviceRadioGroup)

        // Step 2
        etAdditionalInfo = findViewById(R.id.etAdditionalInfo)

        // Step 3
        etDocName       = findViewById(R.id.etDocName)
        btnAttachFile   = findViewById(R.id.btnAttachFile)
        tvFileName      = findViewById(R.id.tvFileName)
        progressUpload  = findViewById(R.id.progressUpload)

        // Step 4
        dropdownUrgency = findViewById(R.id.dropdownUrgency)
        etDeadline      = findViewById(R.id.etDeadline)
    }

    private fun setupUrgencyDropdown() {
        val urgencyItems = resources.getStringArray(R.array.urgency_array).toList()
        val adapter = ArrayAdapter(this, R.layout.item_service_selected, urgencyItems)
        dropdownUrgency.setAdapter(adapter)
    }

    private fun setupListeners() {
        btnNext.setOnClickListener {
            when (currentStep) {
                0 -> { // Step 1
                    val selectedRadioId = serviceRadioGroup.checkedRadioButtonId
                    if (selectedRadioId == -1) {
                        toast(getString(R.string.select_service_first))
                        return@setOnClickListener
                    }
                    val selectedRadio = findViewById<RadioButton>(selectedRadioId)
                    selectedService = selectedRadio.text.toString()
                }
                1 -> { // Step 2 – capture DESCRIPTION
                    additionalInfo = etAdditionalInfo.text?.toString()?.trim()
                }
                2 -> { // Step 3 — file + customName required
                    customName = etDocName.text?.toString()?.trim()
                    if (pickedFileUri == null) {
                        toast(getString(R.string.choose_file_first)); return@setOnClickListener
                    }
                    if (customName.isNullOrEmpty()) {
                        toast(getString(R.string.enter_document_name)); return@setOnClickListener
                    }
                }
                3 -> { // Step 4 -> SAVE LOCALLY & SYNC VIA WORKER
                    urgencyLevel = dropdownUrgency.text?.toString()?.trim()
                    deadlineText = etDeadline.text?.toString()
                    if (urgencyLevel.isNullOrEmpty()) {
                        toast(getString(R.string.select_urgency)); return@setOnClickListener
                    }
                    setLoading(true)
                    sendToLocalDb(
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
    }

    private fun goNextStep() {
        if (currentStep < totalSteps - 1) {
            val next = currentStep + 1
            animateProgress(stepToPercent(currentStep), stepToPercent(next))
            currentStep = next
            showStep(currentStep, forward = true)
            btnNext.text = if (currentStep == totalSteps - 1) {
                getString(R.string.done)
            } else {
                getString(R.string.next)
            }
        } else {
            finish()
        }
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
            } else {
                section.visibility = View.GONE
            }
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
        val dialog = CustomDatePickerDialog(
            context = this,
            onPicked = { date ->
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                etDeadline.setText(fmt.format(date))
            },
            minDate = java.util.Date() // today; you can pass a different min if needed
        )
        dialog.show()
    }

    override fun onBackPressed() {
        if (currentStep in 1..(totalSteps - 2)) {
            val prev = currentStep - 1
            animateProgress(stepToPercent(currentStep), stepToPercent(prev))
            currentStep = prev
            showStep(currentStep, forward = false)
            btnNext.text = getString(R.string.next)
        } else {
            super.onBackPressed()
        }
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

    // ======= OFFLINE-FIRST SAVE (Room) + SYNC TRIGGER =======
    private fun sendToLocalDb(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uri = pickedFileUri ?: return onError(getString(R.string.choose_file_first))

        val originalName = displayNameFromUri(uri) ?: "upload.bin"
        val customNameVal = customName?.takeIf { it.isNotBlank() } // Doc Name -> customName

        val description = (additionalInfo ?: etAdditionalInfo.text?.toString()?.trim()).orEmpty()
        val serviceTypeEnum = mapServiceType(selectedService)
        val priorityEnum = mapPriority(urgencyLevel)
        val deadlineIso = parseDeadlineIsoOrNull(deadlineText)

        val staged = try { copyUriToTempFile(uri, originalName) }
        catch (e: Exception) { return onError("Failed to stage file: ${e.message}") }

        val myUid = FirebaseAuth.getInstance().currentUser?.uid

        val appScope = (application as CiteWiseApp).appScope
        appScope.launch {
            try {
                val entity = ServiceRequestEntity(
                    // IMPORTANT: documentName holds the file's original name
                    //            customName holds the user-provided Doc Name
                    documentName = originalName,
                    customName   = customNameVal ?: "",     // persist as non-null string
                    serviceType  = serviceTypeEnum.name,
                    description  = description,
                    priority     = priorityEnum.name,
                    deadlineIso  = deadlineIso,
                    filePath     = staged.absolutePath,
                    status       = "pending",
                    syncState    = SyncState.PENDING_UPLOAD,
                    userId       = myUid
                )

                LocalRepos(this@RequestServiceStepsActivity).requests.insert(entity)
                RequestsSyncWorker.oneShot(this@RequestServiceStepsActivity)

                runOnUiThread { onSuccess() }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                runOnUiThread { onError("Save was cancelled, please try again") }
            } catch (t: Throwable) {
                runOnUiThread { onError(t.message ?: "Failed to save locally") }
            }
        }
    }

    // ===== Helpers (UI parsing, file staging) =====

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

    private fun copyUriToTempFile(uri: Uri, displayName: String): File {
        val safeName = if (displayName.isBlank()) "upload.bin" else displayName
        val outFile = File(cacheDir, safeName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { out -> input.copyTo(out) }
        } ?: throw IllegalStateException("Cannot open stream for URI")
        return outFile
    }

    // expects dd/MM/yyyy in UI; returns ISO-8601 Z (UTC) at 00:00:00
    private fun parseDeadlineIsoOrNull(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return try {
            val parts = text.trim().split("/")
            val d = parts[0].toInt()
            val m = parts[1].toInt() - 1
            val y = parts[2].toInt()
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = Date(cal.timeInMillis)
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.format(date)
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

