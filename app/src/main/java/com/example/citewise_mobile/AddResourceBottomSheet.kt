package com.example.citewise_mobile.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.api.DocumentsApi
import com.example.citewise_mobile.databinding.SheetAddResourceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class NewResourceBottomSheet : BottomSheetDialogFragment() {

    interface Callback { fun onResourceCreated() }

    private var _binding: SheetAddResourceBinding? = null
    private val binding get() = _binding!!
    private val api: DocumentsApi by lazy { RetrofitInstance.documentsApi }

    private var pickedUri: Uri? = null
    private var pickedDisplayName: String? = null
    private var pickedMime: String? = null

    companion object {
        private const val REQ_FILE = 42
        fun show(fm: androidx.fragment.app.FragmentManager) =
            NewResourceBottomSheet().also { it.show(fm, "new_resource") }
    }

    private val faculties = listOf("Engineering", "Science", "Humanities", "Business")
    private val categoriesUi = listOf("Writing guide", "Template", "Tools")
    private fun mapCategory(ui: String) = when (ui) {
        "Writing guide" -> "WRITING_GUIDE"
        "Template"      -> "TEMPLATE"
        else            -> "AI_USAGE"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAddResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (binding.actCategory as MaterialAutoCompleteTextView)
            .setSimpleItems(categoriesUi.toTypedArray())
        (binding.actFaculty as MaterialAutoCompleteTextView)
            .setSimpleItems(faculties.toTypedArray())

        binding.cardPicker.setOnClickListener { pickFile() }
        binding.btnUpload.setOnClickListener { upload() }
        binding.btnBack.setOnClickListener { dismiss() }
        binding.tvFileName.text = "Select a file"

        // KTX text change listeners
        binding.actCategory.doOnTextChanged { _, _, _, _ -> updateButtonEnabled() }
        binding.actFaculty.doOnTextChanged  { _, _, _, _ -> updateButtonEnabled() }
        binding.etTitle.doOnTextChanged     { _, _, _, _ -> updateButtonEnabled() }

        updateButtonEnabled()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, REQ_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            requireContext().takePersistable(uri)
            pickedUri = uri
            val (name, _) = queryMeta(uri)
            pickedDisplayName = name
            pickedMime = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
            binding.tvFileName.text = name ?: "Selected file"
            updateButtonEnabled()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateButtonEnabled() {
        val ok = binding.actCategory.text?.isNotBlank() == true &&
                binding.actFaculty.text?.isNotBlank() == true &&
                binding.etTitle.text?.isNotBlank() == true &&
                pickedUri != null
        binding.btnUpload.isEnabled = ok && !binding.progress.isVisible
    }

    private fun upload() {
        val uiCategory = binding.actCategory.text?.toString()?.trim().orEmpty()
        val category = mapCategory(uiCategory)
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val faculty = binding.actFaculty.text?.toString()?.trim().orEmpty()
        val uri = pickedUri ?: run { snack("Pick a file"); return }

        if (uiCategory.isBlank()) { snack("Pick a category"); return }
        if (title.isBlank()) { binding.tilTitle.error = "Required"; return } else binding.tilTitle.error = null
        if (faculty.isBlank()) { snack("Pick a faculty"); return }

        setBusy(true)

        runBlocking {
            try {
                val tmp = copyToCache(requireContext(), uri, pickedDisplayName)
                val mime = pickedMime ?: "application/octet-stream"
                val filePart = MultipartBody.Part.createFormData(
                    name = "file",
                    filename = tmp.name,
                    body = tmp.asRequestBody(mime.toMediaTypeOrNull())
                )
                val namePart: RequestBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val facultyPart: RequestBody = faculty.toRequestBody("text/plain".toMediaTypeOrNull())
                val categoryPart: RequestBody = category.toRequestBody("text/plain".toMediaTypeOrNull())

                val auth = getAuthHeaderOrNull()
                if (auth == null) { setBusy(false); snack("Not signed in."); return@runBlocking }

                val resp = withContext(Dispatchers.IO) {
                    api.createResource(
                        name = namePart,
                        faculty = facultyPart,
                        category = categoryPart,
                        file = filePart,
                        auth = auth
                    )
                }

                setBusy(false)
                if (resp.isSuccessful) {
                    snack("Uploaded")
                    (parentFragment as? Callback ?: activity as? Callback)?.onResourceCreated()
                    dismiss()
                } else {
                    val msg = resp.errorBody()?.string().orEmpty()
                    snack("Upload failed (${resp.code()}) ${msg.ifBlank { "" }}")
                }
            } catch (t: Throwable) {
                setBusy(false); snack(t.message ?: "Upload failed")
            }
        }
    }

    private fun setBusy(b: Boolean) {
        binding.progress.isVisible = b
        binding.btnUpload.isVisible = !b
        binding.btnUpload.isEnabled = !b
        binding.cardPicker.isEnabled = !b
        binding.etTitle.isEnabled = !b
        binding.actCategory.isEnabled = !b
        binding.actFaculty.isEnabled = !b
        binding.etDescription.isEnabled = !b
    }

    private fun getAuthHeaderOrNull(): String? = runBlocking {
        val user = FirebaseAuth.getInstance().currentUser ?: return@runBlocking null
        val token = user.getIdToken(true).await().token ?: return@runBlocking null
        "Bearer $token"
    }

    private fun Context.takePersistable(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) {}
        }
    }

    private fun queryMeta(uri: Uri): Pair<String?, Long?> {
        var name: String? = null; var size: Long? = null
        val c = requireContext().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )
        c?.use { if (it.moveToFirst()) { name = it.getString(0); size = it.getLong(1) } }
        return name to size
    }

    private fun copyToCache(ctx: Context, uri: Uri, displayName: String?): File {
        val safe = (displayName ?: "upload.bin").replace(Regex("""[\\/:*?"<>|]"""), "_")
        val out = File(ctx.cacheDir, "nr_$safe")
        ctx.contentResolver.openInputStream(uri).use { inS ->
            FileOutputStream(out).use { outS -> requireNotNull(inS); inS.copyTo(outS) }
        }
        return out
    }

    private fun snack(msg: String) =
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
