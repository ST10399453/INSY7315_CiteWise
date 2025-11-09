package com.example.citewise_mobile.data

import com.example.citewise_mobile.api.ResourceDto
import com.example.citewise_mobile.api.ResourcesApi
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Repository wrapper for Resources API with NetResult safety.
 */
class ResourcesRepository(
    private val api: ResourcesApi
) {

    suspend fun createResourceMultipart(
        file: File,
        mime: String,
        name: String,
        faculty: String,
        category: String?,
        description: String?,
        auth: String?
    ): NetResult<ResourceDto> = safeCall {
        val mediaType = (mime.ifBlank { "application/octet-stream" }).toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody(mediaType)
        )
        val mimePart: RequestBody? = mime.ifBlank { null }?.toPlainPart()
        val namePart = name.toPlainPart()
        val facultyPart = faculty.toPlainPart()
        val categoryPart = category?.takeIf { it.isNotBlank() }?.toPlainPart()
        val descPart = description?.takeIf { it.isNotBlank() }?.toPlainPart()

        api.createResourceMultipart(
            file = filePart,
            mime = mimePart,
            name = namePart,
            faculty = facultyPart,
            category = categoryPart,
            description = descPart,
            auth = auth
        )
    }

    suspend fun listResources(
        faculty: String? = null,
        visibility: String? = null,
        q: String? = null,
        sort: String? = null,
        dir: String? = null,
        auth: String? = null
    ): NetResult<List<ResourceDto>> = safeCall {
        api.listResources(faculty, visibility, q, sort, dir, auth)
    }

    suspend fun getResource(id: String, auth: String? = null): NetResult<ResourceDto> = safeCall {
        api.getResource(id, auth)
    }

    suspend fun deleteResource(id: String, auth: String, strict: Boolean = false): NetResult<Unit> =
        safeCall { api.deleteResource(id, auth, strict) }

    private fun String.toPlainPart(): RequestBody =
        this.toRequestBody("text/plain".toMediaTypeOrNull())

    private inline fun <reified T> safeCall(block: () -> retrofit2.Response<T>): NetResult<T> =
        try {
            val resp = block()
            if (resp.isSuccessful) {
                NetResult.Ok(resp.body() as T)
            } else {
                NetResult.Err(resp.errorBody()?.string() ?: "HTTP ${resp.code()},resp.code() }")
            }
        } catch (t: Throwable) {
            NetResult.Err(t.message ?: "Network error",null )
        }
}
