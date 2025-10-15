package com.example.citewise_mobile.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.citewise_mobile.api.DocumentsApi
import com.example.citewise_mobile.data.DocumentsRepository
import com.example.citewise_mobile.offline.DocumentEntity
import com.example.citewise_mobile.offline.LocalRepos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ResourcesViewModel(
    private val api: DocumentsApi,
    private val localRepos: LocalRepos,
    private val docsRepo: DocumentsRepository
) : ViewModel() {

    private val q = MutableStateFlow("")
    private val faculty = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow("date") // "alpha" | "date"

    private val _items = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val items: StateFlow<List<DocumentEntity>> = _items

    init { refresh() }

    fun setQuery(s: String) { q.value = s; refresh() }
    fun setFaculty(f: String?) { faculty.value = f; refresh() }
    fun setSort(s: String) { sort.value = s; refresh() }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        // pull remote resources
        val resp = api.listResources(
            faculty = faculty.value,
            q = q.value.ifBlank { null },
            sort = sort.value,
            dir = if (sort.value == "date") "desc" else "asc"
        )
        val remote: List<DocumentEntity> = api.listResources(
            faculty = faculty.value,
            q = q.value.ifBlank { null },
            sort = sort.value,
            dir = if (sort.value == "date") "desc" else "asc"
        ).body().orEmpty().map { it.toDocumentEntity() }

        // merge into Room preserving offline
        val dao = localRepos.documents
        val current = try { dao.getAllByDate() } catch (_: Throwable) { emptyList<DocumentEntity>() }
        val merged: List<DocumentEntity> = remote.map { r ->
            val loc = current.find { it.id == r.id }
            r.copy(localPath = loc?.localPath, downloadedAt = loc?.downloadedAt)
        }
        dao.upsertAll(merged)

        _items.value = when (sort.value) {
            "alpha" -> dao.getAllAlpha()
            else -> dao.getAllByDate()
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
            is com.example.citewise_mobile.data.NetResult.Err -> Result.failure(Exception(res.message))
        }
    }

    suspend fun removeOffline(docId: String) {
        val dao = localRepos.documents
        val d = dao.getById(docId) ?: return
        d.localPath?.let { runCatching { File(it).delete() } }
        dao.update(d.copy(localPath = null, downloadedAt = null))
        refresh()
    }

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
