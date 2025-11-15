package com.example.citewise_mobile

import android.R
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
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
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

class AddResourceBottom : BottomSheetDialogFragment() {

    interface Callback { fun onResourceCreated() }

    private var _binding: SheetAddResourceBinding? = null
    private val binding get() = _binding!!

    private var pickedUri: Uri? = null
    private var pickedDisplayName: String? = null
    private var pickedMime: String? = null

    companion object {
        private const val REQ_FILE = 42
        private const val UNIQUE_WORK = "resources_sync_once"

        fun show(fm: FragmentManager) =
            AddResourceBottom().also { it.show(fm, "add_resource_bottom") }
    }

    // Keep these in sync with your filters
    private val faculties = listOf("Engineering", "Science", "Humanities", "Business")
    private val categoriesUi = listOf("Writing guide", "Template", "Tools")

    private fun mapCategory(ui: String) = when (ui) {
        "Writing guide" -> "WRITING_GUIDE"
        "Template"      -> "TEMPLATE"
        else            -> "AI_USAGE"
    }

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
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            R.layout.simple_dropdown_item_1line,
            categoriesUi
        )
        (binding.actCategory as MaterialAutoCompleteTextView).setAdapter(categoryAdapter)

        val facultyAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            faculties
        )
        (binding.actFaculty as MaterialAutoCompleteTextView).setAdapter(facultyAdapter)

        binding.cardPicker.setOnClickListener { pickFile() }
        binding.btnUpload.setOnClickListener { uploadToLocalAndQueueSync() }
        binding.tvFileName.text = ""

        binding.actCategory.addTextChangedListener { updateButtonEnabled() }
        binding.actFaculty.addTextChangedListener  { updateButtonEnabled() }
        binding.tilTitle.addTextChangedListener    { updateButtonEnabled() }

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
     *  - Copy file to app cache
     *  - Insert ResourceEntity with PENDING_UPLOAD
     *  - Trigger ResourcesSyncWorker immediately (REPLACE + expedited)
     */
    private fun uploadToLocalAndQueueSync() {
        val uiCategory = binding.actCategory.text?.toString()?.trim().orEmpty()
        val category = mapCategory(uiCategory)
        val title = binding.tilTitle.text?.toString()?.trim().orEmpty()
        val facultyUi = binding.actFaculty.text?.toString()?.trim().orEmpty()
        val faculty = normalizeFaculty(facultyUi)
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val uri = pickedUri ?: run { snack("Pick a file"); return }

        if (uiCategory.isBlank()) { snack("Pick a category"); return }
        if (title.isBlank()) { binding.tilTitle.error = "Required"; return } else binding.tilTitle.error = null
        if (facultyUi.isBlank()) { snack("Pick a faculty"); return }

        setBusy(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val staged: File = withContext(Dispatchers.IO) {
                    copyToCache(requireContext(), uri, pickedDisplayName)
                }

                val adminUid = FirebaseAuth.getInstance().currentUser?.uid
                val entity = ResourceEntity(
                    adminUid    = adminUid,
                    title       = title,
                    description = description.ifBlank { null },
                    category    = category,
                    faculty     = faculty,
                    fileName    = pickedDisplayName ?: "upload.bin",
                    filePath    = staged.absolutePath,
                    syncState   = SyncState.PENDING_UPLOAD
                )

                withContext(Dispatchers.IO) {
                    LocalRepos(requireContext()).resources.insert(entity)
                }

                enqueueResourceSyncNow()

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

    /** Force-run the sync worker immediately, even if a previous instance exists. */
    private fun enqueueResourceSyncNow() {
        val ctx = requireContext().applicationContext
        val req = OneTimeWorkRequestBuilder<ResourcesSyncWorker>()
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            req
        )
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
