package com.example.citewise_mobile

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
import androidx.lifecycle.lifecycleScope
import com.example.citewise_mobile.databinding.SheetAddResourceBinding
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.ResourceEntity
import com.example.citewise_mobile.offline.ResourcesSyncWorker
import com.example.citewise_mobile.offline.SyncState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NewResourceBottomSheet : BottomSheetDialogFragment() {

    interface Callback { fun onResourceCreated() }

    private var _binding: SheetAddResourceBinding? = null
    private val binding get() = _binding!!

    private var pickedUri: Uri? = null
    private var pickedDisplayName: String? = null
    private var pickedMime: String? = null

    companion object {
        private const val REQ_FILE = 42
        fun show(fm: androidx.fragment.app.FragmentManager) =
            NewResourceBottomSheet().also { it.show(fm, "new_resource") }
    }

    // Keep these in sync with your filters
    private val faculties = listOf("Engineering", "Science", "Humanities", "Business")
    private val categoriesUi = listOf("Writing guide", "Template", "Tools")

    // Map UI -> server category code
    private fun mapCategory(ui: String) = when (ui) {
        "Writing guide" -> "WRITING_GUIDE"
        "Template"      -> "TEMPLATE"
        else            -> "AI_USAGE"
    }

    // Normalize faculty to canonical values your backend expects
    private fun normalizeFaculty(ui: String): String = when (ui.trim().lowercase()) {
        "engineering" -> "Engineering"
        "science"     -> "Science"
        "humanities"  -> "Humanities"
        "business"    -> "Business"
        else          -> "General"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAddResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (binding.actCategory as MaterialAutoCompleteTextView)
            .setSimpleItems(categoriesUi.toTypedArray())
        (binding.actFaculty as MaterialAutoCompleteTextView)
            .setSimpleItems(faculties.toTypedArray())

        binding.cardPicker.setOnClickListener { pickFile() }
        binding.btnUpload.setOnClickListener { uploadToLocalAndQueueSync() }
//        binding.btnBack.setOnClickListener { dismiss() }
        binding.tvFileName.text = "Select a file"

        // reactive validation
        binding.actCategory.addTextChangedListener { updateButtonEnabled() }
        binding.actFaculty.addTextChangedListener  { updateButtonEnabled() }
        binding.tilTitle.addTextChangedListener     { updateButtonEnabled() }

        updateButtonEnabled()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, REQ_FILE)
    }

    @Deprecated("Deprecated in Java")
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
                binding.tilTitle.text?.isNotBlank() == true &&
                pickedUri != null
        binding.btnUpload.isEnabled = ok && !binding.progress.isVisible
    }

    /**
     * Offline-first:
     *  - Validate
     *  - Copy the file into app cache
     *  - Insert ResourceEntity with PENDING_UPLOAD (INCLUDING faculty!)
     *  - Trigger ResourcesSyncWorker (which will POST to /resources)
     */
    private fun uploadToLocalAndQueueSync() {
        val uiCategory = binding.actCategory.text?.toString()?.trim().orEmpty()
        val category = mapCategory(uiCategory)
        val title = binding.tilTitle.text?.toString()?.trim().orEmpty()
        val facultyUi = binding.actFaculty.text?.toString()?.trim().orEmpty()
        val faculty = normalizeFaculty(facultyUi) // <- normalization applied here
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val uri = pickedUri ?: run { snack("Pick a file"); return }

        if (uiCategory.isBlank()) { snack("Pick a category"); return }
        if (title.isBlank()) { binding.tilTitle.error = "Required"; return } else binding.tilTitle.error = null
        if (facultyUi.isBlank()) { snack("Pick a faculty"); return }

        setBusy(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Stage into cache on IO thread
                val staged: File = withContext(Dispatchers.IO) {
                    copyToCache(requireContext(), uri, pickedDisplayName)
                }

                val adminUid = FirebaseAuth.getInstance().currentUser?.uid
                val entity = ResourceEntity(
                    adminUid    = adminUid,
                    title       = title,
                    description = description.ifBlank { null },
                    category    = category,
                    faculty     = faculty, // store normalized faculty
                    fileName    = pickedDisplayName ?: "upload.bin",
                    filePath    = staged.absolutePath,
                    syncState   = SyncState.PENDING_UPLOAD
                )

                // Insert into Room and trigger background sync
                withContext(Dispatchers.IO) {
                    LocalRepos(requireContext()).resources.insert(entity)
                    ResourcesSyncWorker.oneShot(requireContext())
                }

                setBusy(false)
                snack("Queued for upload")
                (parentFragment as? Callback ?: activity as? Callback)?.onResourceCreated()
                dismiss()
            } catch (t: Throwable) {
                setBusy(false)
                snack(t.message ?: "Failed to stage resource")
            }
        }
    }

    private fun setBusy(b: Boolean) {
        binding.progress.isVisible = b
        binding.btnUpload.isVisible = !b
        binding.btnUpload.isEnabled = !b
        binding.cardPicker.isEnabled = !b
        binding.tilTitle.isEnabled = !b
        binding.actCategory.isEnabled = !b
        binding.actFaculty.isEnabled = !b
        binding.etDescription.isEnabled = !b
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
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )
        c?.use { if (it.moveToFirst()) { name = it.getString(0); size = it.getLong(1) } }
        return name to size
    }

    private fun copyToCache(ctx: Context, uri: Uri, displayName: String?): File {
        val safe = (displayName ?: "upload.bin").replace(Regex("""[\\/:*?"<>|]"""), "_")
        val out = File(ctx.cacheDir, "nr_$safe")
        ctx.contentResolver.openInputStream(uri).use { inS ->
            FileOutputStream(out).use { outS ->
                requireNotNull(inS) { "Failed to open selected file" }
                inS.copyTo(outS)
            }
        }
        return out
    }

    private fun snack(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
