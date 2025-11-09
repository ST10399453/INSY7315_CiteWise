package com.example.citewise_mobile

import com.example.citewise_mobile.adapters.ResourcesAdapter
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection

class ResourcesActivity : BaseActivity(), NewResourceBottomSheet.Callback {

    private lateinit var binding: ActivityResourcesBinding
    private lateinit var vm: ResourcesViewModel
    private lateinit var adapter: ResourcesAdapter
    private lateinit var docsRepo: DocumentsRepository

    private val isAdminRole get() = getCurrentUserRole() == UserRole.ADMIN

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

        setupRecycler()
        setupViewModel()
        setupSearch()
        setupSpinners()
        setupRoleSpecificUi()

        vm.refresh()
    }

    // ─────────────────── Setup ───────────────────
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
                    val empty = list.isEmpty()
                    binding.stateEmpty.isVisible = empty
                    // If we have any items, we definitely have network at least recently
                    if (!empty) binding.stateNoInternet.isVisible = false
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.error.collectLatest { err ->
                    binding.stateNoInternet.isVisible = err != null
                }
            }
        }
    }

    private fun setupRecycler() {
        adapter = ResourcesAdapter(
            onOverflow = ::showOverflow,
            onOpen = ::openResource
        )
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        binding.rvDocuments.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvDocuments.adapter = adapter

        binding.rvDocuments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!isAdminRole) return
                when {
                    dy > 6  -> binding.fabAdd.shrink()
                    dy < -6 -> binding.fabAdd.extend()
                }
            }
        })
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            vm.setQuery(editable?.toString().orEmpty())
        }
        binding.etSearch.setOnEditorActionListener { v, _, _ ->
            vm.setQuery(v.text?.toString().orEmpty()); true
        }
        binding.btnRetry.setOnClickListener { vm.refresh() }
    }

    private fun setupSpinners() {
        val visibilityOptions = listOf("All", "Public", "Private")
        val facultyOptions = listOf("All", "Engineering", "Science", "Humanities", "Business")
        val sortOptions = listOf("Newest first", "Oldest first", "A–Z", "Z–A")

        fun spinnerAdapter(items: List<String>) =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

        binding.actVisibility.adapter = spinnerAdapter(visibilityOptions)
        binding.actFaculty.adapter = spinnerAdapter(facultyOptions)
        binding.actSort.adapter = spinnerAdapter(sortOptions)

        var sortReady = false
        binding.actSort.post { sortReady = true }

        binding.actVisibility.onItemSelected { parent, pos ->
            val label = parent.getItemAtPosition(pos)?.toString()
            vm.setVisibility(
                when (label?.lowercase()) {
                    "public"  -> "students"
                    "private" -> "admins"
                    else      -> "all"
                }
            )
        }
        binding.actFaculty.onItemSelected { parent, pos ->
            val label = parent.getItemAtPosition(pos)?.toString()
            vm.setFaculty(label)
        }
        binding.actSort.onItemSelected { _, pos ->
            if (!sortReady) return@onItemSelected
            when (pos) {
                0 -> vm.setSort("date")
                1 -> vm.setSort("date_asc")
                2 -> vm.setSort("alpha")
                3 -> vm.setSort("alpha_desc")
            }
        }

        binding.actVisibility.setSelection(0)
        binding.actFaculty.setSelection(0)
        binding.actSort.setSelection(0)
    }

    private fun setupRoleSpecificUi() {
        binding.fabAdd.isVisible = isAdminRole
        binding.fabAdd.setOnClickListener {
            if (!isAdminRole) return@setOnClickListener
            NewResourceBottomSheet.show(supportFragmentManager)
        }
    }

    // ─────────────────── NewResourceBottomSheet.Callback ───────────────────
    override fun onResourceCreated() {
        vm.refresh()
        snackShort("Resource queued for upload")
    }

    // ─────────────────── Actions ───────────────────
    private fun showOverflow(res: ResourceEntity, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        val menuId = if (isAdminRole) R.menu.menu_document_admin else R.menu.menu_document_item
        popup.menuInflater.inflate(menuId, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_download -> { openResource(res); true }
                R.id.action_delete -> { confirmDelete(res); true }
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
                    if (ok) snackShort("Deleted")
                    else snack("Delete failed")
                }
            }
            .show()
    }


    private fun openResource(res: ResourceEntity) {
        val docId = res.documentId
        val displayName = res.title.ifBlank { res.fileName ?: "file" }
        if (docId.isNullOrBlank()) {
            snack("No document is linked to this resource yet.")
            return
        }
        downloadByDocumentId(docId, displayName)
    }

    /**
     * Download using Documents API only:
     * 1) Try signed URL (/documents/{id}/download) -> DownloadManager
     * 2) Fallback to streaming (/documents/{id}/file) -> save to app downloads
     */
    private fun downloadByDocumentId(documentId: String, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = authHeaderOrNull()
                if (auth == null) {
                    withContext(Dispatchers.Main) { snack("Not signed in.") }
                    return@launch
                }

                // 1) Prefer a signed URL
                when (val urlRes = docsRepo.getSignedUrl(
                    documentId = documentId,
                    auth = auth
                )) {
                    is com.example.citewise_mobile.data.NetResult.Ok -> {
                        withContext(Dispatchers.Main) {
                            enqueueDownload(urlRes.data, fileName)
                            snackShort("Downloading…")
                        }
                        return@launch
                    }
                    is com.example.citewise_mobile.data.NetResult.Err -> {
                        // fall back to streaming
                    }
                }

                // 2) Fallback: authorized streaming proxy
                val streamResp = RetrofitInstance.documentsApi.streamFile(
                    documentId = documentId,
                    provider = null,
                    disposition = "attachment",
                    auth = auth
                )
                if (streamResp.isSuccessful && streamResp.body() != null) {
                    val file = saveToAppDownloads(streamResp.body()!!, fileName)
                    withContext(Dispatchers.Main) { snackShort("Saved to ${file.absolutePath}") }
                    return@launch
                }

                val code = streamResp.code()
                withContext(Dispatchers.Main) {
                    when (code) {
                        HttpURLConnection.HTTP_FORBIDDEN,
                        HttpURLConnection.HTTP_UNAUTHORIZED ->
                            snack("You don’t have permission to download this file (code $code).")
                        HttpURLConnection.HTTP_NOT_FOUND ->
                            snack("File not found (code $code).")
                        else -> snack("Download failed${if (code > 0) " (code $code)" else ""}.")
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { snack("Download failed: ${t.message}") }
            }
        }
    }

    // ─────────────────── Helpers ───────────────────

    /** Build "Bearer <idToken>" or null if not signed in. */
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

/** Spinner helper */
private inline fun android.widget.Spinner.onItemSelected(
    crossinline block: (parent: AdapterView<*>, position: Int) -> Unit
) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>, view: View?, position: Int, id: Long
        ) = block(parent, position)
        override fun onNothingSelected(parent: AdapterView<*>) = Unit
    }
}
