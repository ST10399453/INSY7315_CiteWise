package com.example.citewise_mobile

import android.app.DownloadManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.citewise_mobile.adapters.ResourcesAdapter
import com.example.citewise_mobile.api.ResourcesViewModel
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.databinding.ActivityResourcesBinding
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.ResourceEntity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.InputStream

class ResourcesActivity : BaseActivity(), AddResourceBottom.Callback {

    private lateinit var binding: ActivityResourcesBinding
    private lateinit var vm: ResourcesViewModel
    private lateinit var adapter: ResourcesAdapter
    private lateinit var docsRepo: DocumentsRepository

    private val isAdminRole get() = getCurrentUserRole() == UserRole.ADMIN

    private var lastItemsCount: Int = 0
    private var hasError: Boolean = false
    private var isLoading: Boolean = false
    private var hasAttemptedFirstLoad: Boolean = false

    // Search debounce
    private var searchJob: Job? = null
    private val searchDelayMs = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_resources, baseContent, false)
        binding = ActivityResourcesBinding.bind(content)
        baseContent.addView(content)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val selectedId = if (isAdminRole) R.id.nav_resource_mgmt else R.id.nav_resources
        setupBottomNav(bottomNav, selectedId)

        setupViewModel()
        setupRecycler()
        setupSearch()
        setupDropdowns()
        setupRoleUi()

        vm.refresh()
    }

    // ───────── Recycler ─────────
    private fun setupRecycler() {
        adapter = ResourcesAdapter(
            onOverflow = ::showOverflow,
            onOpen = ::openResource
        )
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        binding.rvDocuments.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvDocuments.adapter = adapter
    }

    // ───────── ViewModel / collectors ─────────
    private fun setupViewModel() {
        val localRepos = LocalRepos(applicationContext)
        docsRepo = DocumentsRepository(RetrofitInstance.documentsApi, applicationContext)

        val factory = ResourcesViewModel.Factory(
            api = RetrofitInstance.resourcesApi,
            localRepos = localRepos,
            docsRepo = docsRepo
        )
        vm = ViewModelProvider(this, factory)[ResourcesViewModel::class.java]

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.items.collectLatest { list: List<ResourceEntity> ->
                    adapter.submitList(list)
                    lastItemsCount = list.size
                    if (list.isNotEmpty()) hasError = false
                    renderState()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.error.collectLatest { err ->
                    hasError = (err != null)
                    if (lastItemsCount > 0) hasError = false
                    renderState()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.loading.collectLatest { loading ->
                    isLoading = loading
                    if (!loading) hasAttemptedFirstLoad = true
                    renderState()
                }
            }
        }
    }

    private fun renderState() {
        val showNoInternet = hasAttemptedFirstLoad && !isLoading && hasError && lastItemsCount == 0
        val showEmpty = hasAttemptedFirstLoad && !isLoading && !hasError && lastItemsCount == 0
        val showList = lastItemsCount > 0 || isLoading || !hasAttemptedFirstLoad

        binding.stateNoInternet.isVisible = showNoInternet
        binding.stateEmpty.isVisible = showEmpty
        binding.rvDocuments.isVisible = showList
    }

    // ───────── Search (debounced) ─────────
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            val text = editable?.toString()?.trim().orEmpty()
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(searchDelayMs)
                vm.setQuery(text)
                vm.refresh()
            }
        }
        binding.btnRetry.setOnClickListener { vm.refresh() }
    }

    // ───────── Filters (exposed dropdowns, collapsible) ─────────
    private fun setupDropdowns() {
        val visibilityOptions = listOf("All", "Public", "Private")
        val facultyOptions = listOf("All", "Engineering", "Science", "Business", "Law", "Education", "Health", "Arts")
        val sortOptions = listOf("Newest first", "Oldest first", "A–Z", "Z–A")
        val itemLayout = R.layout.item_dropdown_text

        // Visibility
        (binding.actVisibility as? AutoCompleteTextView)?.apply {
            setAdapter(ArrayAdapter(context, itemLayout, visibilityOptions))
            setOnItemClickListener { parent, _, pos, _ ->
                val label = parent.getItemAtPosition(pos)?.toString()
                vm.setVisibility(label)
                vm.refresh()
                clearFocus()      // collapse dropdown
            }
            setText(visibilityOptions.first(), false)
            vm.setVisibility("All")

            // tap again to toggle dropdown
            setOnClickListener { showDropDown() }
        }

        // Faculty
        (binding.actFaculty as? AutoCompleteTextView)?.apply {
            setAdapter(ArrayAdapter(context, itemLayout, facultyOptions))
            setOnItemClickListener { parent, _, pos, _ ->
                val v = parent.getItemAtPosition(pos)?.toString()
                vm.setFaculty(v?.takeUnless { it.equals("All", true) })
                vm.refresh()
                clearFocus()
            }
            setText(facultyOptions.first(), false)
            vm.setFaculty(null)
            setOnClickListener { showDropDown() }
        }

        // Sort
        (binding.actSort as? AutoCompleteTextView)?.apply {
            setAdapter(ArrayAdapter(context, itemLayout, sortOptions))
            setOnItemClickListener { _, _, pos, _ ->
                when (pos) {
                    0 -> vm.setSort("date")
                    1 -> vm.setSort("date_asc")
                    2 -> vm.setSort("alpha")
                    3 -> vm.setSort("alpha_desc")
                }
                vm.refresh()
                clearFocus()
            }
            setText(sortOptions.first(), false)
            vm.setSort("date")
            setOnClickListener { showDropDown() }
        }
    }

    // ───────── Role-specific UI ─────────
    private fun setupRoleUi() {
        binding.fabAdd.apply {
            isVisible = isAdminRole
            if (isAdminRole) {
                isExtended = true
                setOnClickListener { AddResourceBottom.show(supportFragmentManager) }
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.gradient_end))
                setIconTintResource(android.R.color.white)
            }
        }
    }

    // ───────── AddResourceBottom.Callback ─────────
    override fun onResourceCreated() {
        vm.refresh()
        snackShort("Resource queued for upload")
    }

    // ───────── Overflow / actions ─────────
    private fun showOverflow(res: ResourceEntity, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        val menuId = if (isAdminRole) R.menu.menu_document_admin else R.menu.menu_document_item
        popup.menuInflater.inflate(menuId, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_download -> { openResource(res); true }
                R.id.action_delete   -> { confirmDelete(res); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDelete(res: ResourceEntity) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete resource?")
            .setMessage("This will remove “${res.title.ifBlank { res.fileName ?: "resource" }}”.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val ok = vm.deleteResource(res)
                    if (ok) snackShort("Deleted") else snack("Delete failed")
                }
            }
            .show()
    }

    private fun openResource(res: ResourceEntity) {
        val resourceId = res.remoteId
        val fileName = res.fileName ?: res.title

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitInstance.resourcesApi
                val urlDto = api.getResourceDownloadUrl(
                    id = resourceId,
                    disposition = "attachment"
                )

                withContext(Dispatchers.Main) {
                    enqueueDownload(urlDto.url, fileName)
                    snackShort("Downloading…")
                }

            } catch (e: Exception) {
                val resp = RetrofitInstance.resourcesApi.streamResourceFile(resourceId)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body != null) {
                        val saved = saveToAppDownloads(body, fileName)
                        withContext(Dispatchers.Main) {
                            snackShort("Saved to ${saved.absolutePath}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        snack("Download failed (${resp.code()})")
                    }
                }
            }
        }
    }

    private suspend fun authHeaderOrNull(): String? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        val token = runCatching { user.getIdToken(true).await().token }.getOrNull()
        token?.let { "Bearer $it" }
    }

    private fun enqueueDownload(url: String, fileName: String) {
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("Downloading…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CiteWise/$safeName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = getSystemService(DownloadManager::class.java)
        dm.enqueue(req)
    }

    private fun saveToAppDownloads(body: ResponseBody, fileName: String): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val dir = File(base, "CiteWise").apply { if (!exists()) mkdirs() }
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val target = File(dir, safeName)
        body.byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun saveToAppDownloads(input: InputStream, fileName: String): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val dir = File(base, "CiteWise").apply { if (!exists()) mkdirs() }
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val target = File(dir, safeName)
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        return target
    }

    private fun snack(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
            .setAnchorView(if (binding.fabAdd.isVisible) binding.fabAdd else null)
            .show()
    }

    private fun snackShort(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
            .setAnchorView(if (binding.fabAdd.isVisible) binding.fabAdd else null)
            .show()
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

