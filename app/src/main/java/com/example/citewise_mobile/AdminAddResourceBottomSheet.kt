package com.example.citewise_mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.citewise_mobile.api.DocumentsApi
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.databinding.SheetAddResourceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AdminAddResourceBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetAddResourceBinding
    private var pickedUri: Uri? = null
    private val api: DocumentsApi by lazy { RetrofitInstance.documentsApi }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = SheetAddResourceBinding.inflate(inflater, container, false)

        binding.segment.check(binding.btnCatGuide.id)
        binding.btnPick.setOnClickListener { pickDoc() }
        binding.btnSubmit.setOnClickListener { doSubmit() }

        // TODO: populate faculties (array adapter) from a constant or backend
        return binding.root
    }

    private fun pickDoc() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/pdf")
        pick.launch(intent)
    }

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            pickedUri = it.data?.data
            binding.tvPicked.text = pickedUri?.lastPathSegment ?: "Selected"
        }
    }

    private fun doSubmit() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val faculty = binding.actFaculty.text?.toString()?.trim().orEmpty()
        val category = when (binding.segment.checkedButtonId) {
            binding.btnCatGuide.id -> "WRITING_GUIDE"
            binding.btnCatTemplate.id -> "TEMPLATE"
            else -> "AI_USAGE"
        }
        val uri = pickedUri
        if (name.isBlank() || faculty.isBlank() || uri == null) {
            Toast.makeText(requireContext(), "Fill all fields & pick a PDF", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val ctx = requireContext().applicationContext
            val tmp = File.createTempFile("upload", ".pdf", ctx.cacheDir)
            ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }

            val body = tmp.asRequestBody("application/pdf".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "$name.pdf", body)

            fun t(s: String): RequestBody = s.toRequestBody("text/plain".toMediaType())

            val resp = api.createResource(t(name), t(faculty), t(category), part)
            withContext(Dispatchers.Main) {
                if (resp.isSuccessful) {
                    Toast.makeText(ctx, "Uploaded", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(ctx, "Upload failed: ${resp.code()}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
