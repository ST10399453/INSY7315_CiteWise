package com.example.citewise_mobile.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.offline.LocalRepos
import com.example.citewise_mobile.offline.ResourceEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ResourcesViewModel(
    private val api: ResourcesApi,
    private val localRepos: LocalRepos,
    private val docsRepo: DocumentsRepository
) : ViewModel() {

    // inputs
    private val q = MutableStateFlow("")
    private val faculty = MutableStateFlow<String?>(null)
    private val visibilityLabel = MutableStateFlow<String?>("All")
    private val sortKey = MutableStateFlow("date")

    private val _items = MutableStateFlow<List<ResourceEntity>>(emptyList())
    val items: StateFlow<List<ResourceEntity>> = _items

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

            val visApi: String? = when (visibilityLabel.value?.lowercase()) {
                null, "", "all" -> "all"
                "public"        -> "students"
                "private"       -> "admins"
                else            -> "all"
            }
            val facultyApi: String? = faculty.value?.takeUnless { it.equals("All", true) }
            val (sort, dir) = when (sortKey.value) {
                "alpha"      -> "alpha" to "asc"
                "alpha_desc" -> "alpha" to "desc"
                "date_asc"   -> "date"  to "asc"
                else         -> "date"  to "desc"
            }
            val queryText: String? = q.value.ifBlank { null }

            val resp = api.listResources(
                faculty = facultyApi,
                visibility = visApi,
                q = queryText,
                sort = sort,
                dir = dir,
                auth = authHeader
            )

            if (!resp.isSuccessful || resp.body() == null) {
                val code = resp.code()
                val msg = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP $code" }
                _error.emit(msg)
                _items.emit(emptyList())
                _loading.emit(false)
                return@launch
            }

            val remote: List<ResourceEntity> = resp.body()!!.map { it.toResourceEntity() }

            val dao = localRepos.resources
            val existing = runCatching { dao.getAll() }.getOrDefault(emptyList())
            val byRemote = existing.associateBy { it.remoteId }
            val merged = remote.map { r ->
                val old = r.remoteId?.let { byRemote[it] }
                if (old == null) r.copy(updatedAt = System.currentTimeMillis())
                else old.copy(
                    title       = r.title,
                    description = r.description ?: old.description,
                    category    = r.category ?: old.category,
                    faculty     = r.faculty ?: old.faculty,
                    documentId  = r.documentId ?: old.documentId,
                    fileName    = r.fileName ?: old.fileName,
                    updatedAt   = System.currentTimeMillis()
                )
            }
            // simple approach: update or insert one by one
            merged.forEach { entity ->
                if (entity.remoteId != null && byRemote.containsKey(entity.remoteId)) {
                    dao.update(entity)
                } else {
                    dao.insert(entity)
                }
            }

            // order locally according to sort
            val out: List<ResourceEntity> = when (sortKey.value) {
                "alpha"      -> dao.getAll().sortedBy { it.title.lowercase() }
                "alpha_desc" -> dao.getAll().sortedByDescending { it.title.lowercase() }
                "date_asc"   -> dao.getAll().sortedBy { it.updatedAt }
                else         -> dao.getAll().sortedByDescending { it.updatedAt }
            }

            _items.emit(out)
        } catch (t: Throwable) {
            _error.emit(t.localizedMessage ?: "Unexpected error")
            _items.emit(emptyList())
        } finally {
            _loading.emit(false)
        }
    }

    private suspend fun buildAuthHeader(): String? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        val token = runCatching { user.getIdToken(true).await().token }.getOrNull()
        token?.let { "Bearer $it" }
    }

    // In ResourcesViewModel
    suspend fun deleteResource(res: ResourceEntity): Boolean {
        val id = res.remoteId ?: return false
        val token = buildAuthHeader() ?: return false
        return try {
            val resp = api.deleteResource(id, token, strict = false)
            if (resp.isSuccessful) {
                // remove locally so RecyclerView updates immediately
                localRepos.resources.deleteByRemoteId(id)
                // also refresh the stream to reflect server truth
                refresh()
                true
            } else {
                false
            }
        } catch (_: Throwable) { false }
    }


    class Factory(
        private val api: ResourcesApi,
        private val localRepos: LocalRepos,
        private val docsRepo: DocumentsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResourcesViewModel(api, localRepos, docsRepo) as T
    }
}

/*
 * REFERENCES
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 */