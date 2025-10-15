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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.citewise_mobile.api.ResourcesViewModel
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.databinding.ActivityResourcesBinding
import com.example.citewise_mobile.offline.DocumentEntity
import com.example.citewise_mobile.offline.LocalRepos
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ResourcesActivity : BaseActivity() {

    private lateinit var binding: ActivityResourcesBinding
    private lateinit var vm: ResourcesViewModel
    private lateinit var adapter: DocumentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Use shared base layout (bottom nav, etc.)
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // 2) Inflate resources screen into baseContent
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val content = layoutInflater.inflate(R.layout.activity_resources, baseContent, false)
        binding = ActivityResourcesBinding.bind(content)
        baseContent.addView(content)

        // 3) Bottom nav like ServiceRequestActivity
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_resources)

        // 4) Set up UI
        setupViewModel()
        setupRecycler()
        setupSearch()
        setupSpinners()   // NEW: replaces toggle sort + ACT fields
        setupAdminFab()

        vm.refresh()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────
    private fun setupViewModel() {
        val localRepos = LocalRepos(applicationContext)
        val docsRepo = DocumentsRepository(RetrofitInstance.documentsApi, applicationContext)
        val factory = ResourcesViewModel.Factory(
            api = RetrofitInstance.documentsApi,
            localRepos = localRepos,
            docsRepo = docsRepo
        )
        vm = ViewModelProvider(this, factory)[ResourcesViewModel::class.java]
    }

    private fun setupRecycler() {
        adapter = DocumentsAdapter(
            onOverflow = ::showOverflow,
            onOpen = ::openDocument   // tap = download
        )
        binding.rvDocuments.layoutManager = GridLayoutManager(this, 2)
        binding.rvDocuments.adapter = adapter

        lifecycleScope.launch {
            vm.items.collect { list -> adapter.submitList(list) }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(SimpleTextWatcher { text ->
            vm.setQuery(text)
        })
    }

    private fun setupSpinners() {
        // You can swap these for string-array resources if you have them.
        val visibilityOptions = listOf("All", "Public", "Private")
        val facultyOptions = listOf("All", "Engineering", "Science", "Humanities", "Business")
        val sortOptions = listOf("Newest first", "Oldest first", "A–Z", "Z–A")

        fun spinnerAdapter(items: List<String>) =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

        binding.actVisibility.adapter = spinnerAdapter(visibilityOptions)
        binding.actFaculty.adapter = spinnerAdapter(facultyOptions)
        binding.actSort.adapter = spinnerAdapter(sortOptions)

        // Avoid firing on initial selection
        var ready = false
        binding.actSort.post { ready = true }

        binding.actVisibility.onItemSelected { _, pos ->
            if (pos >= 0) vm.refresh() // hook to backend visibility if needed
        }
        binding.actFaculty.onItemSelected { _, _ ->
            vm.setFaculty(binding.actFaculty.selectedItem?.toString())
        }
        binding.actSort.onItemSelected { _, pos ->
            if (!ready) return@onItemSelected
            when (pos) {
                0 -> vm.setSort("date")     // Newest first
                1 -> vm.setSort("date_asc") // Oldest first
                2 -> vm.setSort("alpha")    // A–Z
                3 -> vm.setSort("alpha_desc")
            }
        }
        // Defaults
        binding.actVisibility.setSelection(0)
        binding.actFaculty.setSelection(0)
        binding.actSort.setSelection(0)
    }

    private fun setupAdminFab() {
        if (isAdmin()) binding.fabAdd.visibility = View.VISIBLE
        binding.fabAdd.setOnClickListener {
            AdminAddResourceBottomSheet().show(supportFragmentManager, "addResource")
        }
    }

    private fun isAdmin(): Boolean = false

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────
    private fun showOverflow(doc: DocumentEntity, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.inflate(R.menu.menu_document_item) // only action_download inside
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_download -> { downloadExternal(doc); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun openDocument(doc: DocumentEntity) = downloadExternal(doc)

    private fun downloadExternal(doc: DocumentEntity) {
        lifecycleScope.launch {
            try {
                val resp = RetrofitInstance.documentsApi.resourceSignedUrl(
                    id = doc.id,
                    disposition = "attachment",
                    provider = null
                )
                if (!resp.isSuccessful) {
                    Snackbar.make(binding.root, "Download link failed: ${resp.code()}", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val url = resp.body()?.url
                if (url.isNullOrBlank()) {
                    Snackbar.make(binding.root, "Empty download URL", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val dm = getSystemService(DownloadManager::class.java)
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(doc.fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "CiteWise/${doc.fileName}"
                    )
                dm.enqueue(req)
                Snackbar.make(binding.root, "Downloading…", Snackbar.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Snackbar.make(binding.root, "Download failed: ${t.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}

/** Tiny helper to reduce TextWatcher boilerplate. */
private class SimpleTextWatcher(
    val onChange: (String) -> Unit
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        onChange(s?.toString().orEmpty())
    }
}

/** Extension to keep Spinner listeners tidy. */
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
