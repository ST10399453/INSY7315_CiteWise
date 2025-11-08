package com.example.citewise_mobile.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.citewise_mobile.api.DocumentsApi
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.offline.DocumentEntity
import com.example.citewise_mobile.offline.LocalRepos
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class ResourcesViewModel(
    private val api: DocumentsApi,
    private val localRepos: LocalRepos,
    private val docsRepo: DocumentsRepository
) : ViewModel() {

    // UI inputs
    private val q = MutableStateFlow("")
    private val faculty = MutableStateFlow<String?>(null)           // "All" or actual faculty; we normalize to null if "All"
    private val visibilityLabel = MutableStateFlow<String?>("All")  // "All" | "Public" | "Private"
    private val sortKey = MutableStateFlow("date")                  // "date" | "date_asc" | "alpha" | "alpha_desc"

    // Outputs
    private val _items = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val items: StateFlow<List<DocumentEntity>> = _items

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { refresh() }

    fun setQuery(s: String) { q.value = s; refresh() }
    fun setFaculty(label: String?) { faculty.value = label; refresh() }
    fun setVisibility(label: String?) { visibilityLabel.value = label; refresh() }
    fun setSort(key: String) { sortKey.value = key; refresh() }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        _loading.emit(true)
        _error.emit(null)

        try {
            val authHeader = buildAuthHeader() ?: run {
                _loading.emit(false)
                _error.emit("Not signed in.")
                _items.emit(emptyList())
                return@launch
            }

            // Normalize UI -> API
            val visApi = mapVisibility(visibilityLabel.value)             // "all" | "students" | "admins" | null
            val facultyApi = faculty.value?.takeUnless { it.equals("All", true) }
            val (sort, dir) = mapSort(sortKey.value)                      // ("alpha"|"date", "asc"|"desc")
            val queryText = q.value.ifBlank { null }

            // Single network call (no duplication)
            val resp = api.listResources(
                faculty = facultyApi,
                visibility = visApi,     // server treats "all" as everyone
                q = queryText,
                sort = sort,
                dir = dir,
                auth = authHeader        // 🔐 IMPORTANT
            )

            if (!resp.isSuccessful || resp.body() == null) {
                val code = resp.code()
                val msg = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP $code" }
                _error.emit(msg)
                _items.emit(emptyList())
                _loading.emit(false)
                return@launch
            }

            val remote = resp.body()!!.map { it.toDocumentEntity() }

            // Merge with local (preserve offline fields)
            val dao = localRepos.documents
            val current = runCatching { dao.getAllByDate() }.getOrDefault(emptyList())
            val merged = remote.map { r ->
                val loc = current.find { it.id == r.id }
                r.copy(localPath = loc?.localPath, downloadedAt = loc?.downloadedAt)
            }

            dao.upsertAll(merged)

            _items.emit(
                when (sortKey.value) {
                    "alpha"      -> dao.getAllAlpha()
                    "alpha_desc" -> dao.getAllAlpha().asReversed()
                    "date_asc"   -> dao.getAllByDate().asReversed()
                    else         -> dao.getAllByDate() // "date" desc default
                }
            )
        } catch (t: Throwable) {
            _error.emit(t.localizedMessage ?: "Unexpected error")
            _items.emit(emptyList())
        } finally {
            _loading.emit(false)
        }
    }

    suspend fun markOffline(docId: String, preferredName: String?): Result<File> {
        return when (val res = docsRepo.downloadToDisk(docId, preferredName)) {
            is com.example.citewise_mobile.data.NetResult.Ok -> {
                val dao = localRepos.documents
                val d = dao.getById(docId) ?: return Result.failure(IllegalStateException("Not found"))
                dao.update(d.copy(localPath = res.data.absolutePath, downloadedAt = System.currentTimeMillis()))
                refresh()
                Result.success(res.data)
            }
            is com.example.citewise_mobile.data.NetResult.Err ->
                Result.failure(Exception(res.message))
        }
    }

    suspend fun removeOffline(docId: String) {
        val dao = localRepos.documents
        val d = dao.getById(docId) ?: return
        d.localPath?.let { runCatching { File(it).delete() } }
        dao.update(d.copy(localPath = null, downloadedAt = null))
        refresh()
    }

    // ─────────── Helpers ───────────

    private suspend fun buildAuthHeader(): String? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        val token = runCatching { user.getIdToken(true).await().token }.getOrNull()
        token?.let { "Bearer $it" }
    }

    private fun mapVisibility(label: String?): String? = when (label?.lowercase()) {
        null, "", "all"   -> "all"      // you can also return null to omit the filter
        "public"          -> "students" // public = visible to students
        "private"         -> "admins"   // private = admins only
        else              -> "all"
    }

    private fun mapSort(key: String): Pair<String, String> = when (key) {
        "alpha"       -> "alpha" to "asc"
        "alpha_desc"  -> "alpha" to "desc"
        "date_asc"    -> "date"  to "asc"
        else          -> "date"  to "desc" // default "Newest first"
    }

    // Factory
    class Factory(
        private val api: DocumentsApi,
        private val localRepos: LocalRepos,
        private val docsRepo: DocumentsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResourcesViewModel(api, localRepos, docsRepo) as T
    }
}
