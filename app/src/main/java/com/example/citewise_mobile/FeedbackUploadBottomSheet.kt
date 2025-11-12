package com.example.citewise_mobile

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class FeedbackUploadBottomSheet : BottomSheetDialogFragment() {

    interface Callback { fun onFeedbackUploaded(updated: ServiceRequestDto) }

    private var callback: Callback? = null
    private lateinit var repo: ServiceReviewsRepository

    private var reqId: String = ""
    private var newStatus: String? = null

    private var pickedUri: Uri? = null
    private var pickedDisplayName: String? = null
    private var pickedExt: String? = null
    private var pickedMime: String = "application/octet-stream"

    // activity result launcher
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val root = view ?: return@registerForActivityResult
        val tvPickedName = root.findViewById<TextView>(R.id.tvPickedName)
        val etFileName = root.findViewById<TextInputEditText>(R.id.etFileName)

        if (uri != null) {
            pickedUri = uri
            pickedMime = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
            pickedDisplayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "annotated.pdf"
            pickedExt = guessExtension(pickedDisplayName, pickedMime)
            tvPickedName.text = pickedDisplayName
            if (etFileName.text.isNullOrBlank()) {
                etFileName.setText(pickedDisplayName)
                etFileName.setSelection(etFileName.text?.length ?: 0)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = parentFragment as? Callback ?: activity as? Callback
        repo = ServiceReviewsRepository(RetrofitInstance.api)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reqId = requireArguments().getString(ARG_REQUEST_ID).orEmpty()
        newStatus = requireArguments().getString(ARG_NEW_STATUS)
        if (reqId.isBlank()) {
            requireContext().toast("Request ID missing.")
            dismissAllowingStateLoss()
        }
        isCancelable = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_feedback_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val cardPicker = view.findViewById<LinearLayout>(R.id.cardPicker)
        val tvPickedName = view.findViewById<TextView>(R.id.tvPickedName)
        val etFileName = view.findViewById<TextInputEditText>(R.id.etFileName)
        val btnUpload = view.findViewById<MaterialButton>(R.id.btnUpload)
        val progress = view.findViewById<ProgressBar>(R.id.progress)

        cardPicker.setOnClickListener { picker.launch("*/*") }

        btnUpload.setOnClickListener {
            if (pickedUri == null) {
                requireContext().toast("Please select a file")
                return@setOnClickListener
            }
            val desiredNameRaw = etFileName.text?.toString()?.trim().orEmpty()
            val desiredName = sanitizedDesiredName(desiredNameRaw, pickedDisplayName, pickedExt)
            if (desiredName.isBlank()) {
                requireContext().toast("Enter a valid file name")
                return@setOnClickListener
            }

            btnUpload.isEnabled = false
            cardPicker.isEnabled = false
            progress.visibility = View.VISIBLE

            MainScope().launch(Dispatchers.IO) {
                try {
                    val tmp = copyToCache(desiredName, pickedUri!!)
                    val result = repo.uploadAnnotatedFile(
                        requestId = reqId,
                        file = tmp,
                        mime = pickedMime,
                        newStatus = newStatus
                    )
                    withContext(Dispatchers.Main) {
                        when (result) {
                            is NetResult.Ok -> { callback?.onFeedbackUploaded(result.data); dismiss() }
                            is NetResult.Err -> {
                                requireContext().toast("Upload failed: ${result.message}")
                                btnUpload.isEnabled = true
                                cardPicker.isEnabled = true
                                progress.visibility = View.GONE
                            }
                        }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        requireContext().toast("Upload failed: ${t.message}")
                        btnUpload.isEnabled = true
                        cardPicker.isEnabled = true
                        progress.visibility = View.GONE
                        Log.e(TAG, "Upload error", t)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { d ->
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                // Set generous peek height for consistent display
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                behavior.isDraggable = true
            }
        }
    }

    // --- helpers ---

    private fun copyToCache(filename: String, uri: Uri): File {
        val out = File(requireContext().cacheDir, filename)
        requireContext().contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(out).use { output -> input?.copyTo(output) }
        }
        return out
    }

    private fun Context.toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun sanitizedDesiredName(inputName: String, fallbackPicked: String?, extFromPicked: String?): String {
        val base = (inputName.ifBlank { fallbackPicked ?: "annotated" })
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
        return if (base.contains('.')) base else "${base}.${(extFromPicked ?: "pdf").lowercase(Locale.ROOT)}"
    }

    private fun guessExtension(displayName: String?, mime: String?): String? {
        displayName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        return when (mime?.lowercase(Locale.ROOT)) {
            "application/pdf" -> "pdf"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "text/plain" -> "txt"
            "application/zip" -> "zip"
            else -> null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && it.moveToFirst()) name = it.getString(idx)
        }
        return name
    }

    companion object {
        private const val TAG = "FeedbackUploadSheet"
        private const val ARG_REQUEST_ID = "arg_request_id"
        private const val ARG_NEW_STATUS = "arg_new_status"

        fun newInstance(requestId: String, newStatus: String? = null) =
            FeedbackUploadBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST_ID, requestId)
                    putString(ARG_NEW_STATUS, newStatus)
                }
            }
    }
}
