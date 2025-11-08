package com.example.citewise_mobile

import DocumentsAdapter
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.api.ResourcesViewModel
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.databinding.ActivityResourcesBinding
import com.example.citewise_mobile.offline.DocumentEntity
import com.example.citewise_mobile.offline.LocalRepos
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

class ResourcesActivity : BaseActivity() {

    private lateinit var binding: ActivityResourcesBinding
    private lateinit var vm: ResourcesViewModel
    private lateinit var adapter: DocumentsAdapter
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

        // Init adapter BEFORE collecting flows
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
            api = RetrofitInstance.documentsApi,
            localRepos = localRepos,
            docsRepo = docsRepo
        )
        vm = ViewModelProvider(this, factory)[ResourcesViewModel::class.java]

        lifecycleScope.launch {
            vm.items.collectLatest { list ->
                adapter.submitList(list)
                val isEmpty = list.isNullOrEmpty()
                binding.stateEmpty.isVisible = isEmpty
                if (!isEmpty) binding.stateNoInternet.isVisible = false
            }
        }
    }

    private fun setupRecycler() {
        adapter = DocumentsAdapter(
            onOverflow = ::showOverflow,
            onOpen = ::openDocument
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

        binding.actVisibility.onItemSelected { _, _ -> vm.refresh() }
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
            Snackbar.make(binding.root, "Add resource (admin)", Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.fabAdd)
                .show()
        }
    }

    // ─────────────────── Actions ───────────────────
    private fun showOverflow(doc: DocumentEntity, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.inflate(if (isAdminRole) R.menu.menu_document_admin else R.menu.menu_document_item)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_download -> { downloadExternal(doc); true }
                R.id.action_delete   -> { confirmDeleteAsAdmin(doc); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun openDocument(doc: DocumentEntity) = downloadExternal(doc)

    private fun downloadExternal(doc: DocumentEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = authHeaderOrNull()
                if (auth == null) {
                    withContext(Dispatchers.Main) { snack("Not signed in.") }
                    return@launch
                }

                // 1) Prefer a signed URL via repository (now requires auth)
                when (val urlRes = docsRepo.getResourceSignedUrl(
                    id = doc.id,
                    disposition = "attachment",
                    auth = auth
                )) {
                    is com.example.citewise_mobile.data.NetResult.Ok -> {
                        withContext(Dispatchers.Main) {
                            enqueueDownload(urlRes.data, doc.fileName)
                            snackShort("Downloading…")
                        }
                        return@launch
                    }
                    is com.example.citewise_mobile.data.NetResult.Err -> {
                        // fall through to streaming
                    }
                }

                // 2) Fallback: authorized streaming proxy
                val streamResp = RetrofitInstance.documentsApi.streamResource(
                    id = doc.id,
                    provider = null,
                    disposition = "attachment",
                    auth = auth
                )
                if (streamResp.isSuccessful && streamResp.body() != null) {
                    val file = saveToAppDownloads(streamResp.body()!!, doc.fileName)
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

    private fun confirmDeleteAsAdmin(doc: DocumentEntity) {
        if (!isAdminRole) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete resource?")
            .setMessage("“${doc.fileName}” will be removed for everyone. This cannot be undone.")
            .setPositiveButton("Delete") { d, _ ->
                d.dismiss()
                deleteDocumentAsAdmin(doc)
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun deleteDocumentAsAdmin(doc: DocumentEntity) {
        if (!isAdminRole) return
        lifecycleScope.launch(Dispatchers.IO) {
            val auth = authHeaderOrNull()
            if (auth == null) {
                withContext(Dispatchers.Main) { snack("Not signed in.") }
                return@launch
            }
            try {
                // Use repository if its deleteResource(resourceId, auth) is available:
                val res = docsRepo.deleteResource(doc.id)
                withContext(Dispatchers.Main) {
                    when (res) {
                        is com.example.citewise_mobile.data.NetResult.Ok -> {
                            snackShort("Deleted")
                            vm.refresh()
                        }
                        is com.example.citewise_mobile.data.NetResult.Err -> {
                            val code = res.code ?: -1
                            when (code) {
                                HttpURLConnection.HTTP_FORBIDDEN,
                                HttpURLConnection.HTTP_UNAUTHORIZED ->
                                    snack("You don’t have permission to delete this resource (code $code).")
                                HttpURLConnection.HTTP_NOT_FOUND ->
                                    snack("Resource not found (code $code).")
                                else ->
                                    snack("Delete failed${if (code > 0) " (code $code)" else ""}.")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { snack("Delete failed: ${t.message}") }
            }
        }
    }

    // ─────────────────── Helpers ───────────────────

    /** Build "Bearer <idToken)" or null if not signed in. */
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
