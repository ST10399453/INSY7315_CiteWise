package com.example.citewise_mobile.data

import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceReviewsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

sealed class NetResult<out T> {
    data class Ok<T>(val data: T): NetResult<T>()
    data class Err(val message: String, val code: Int? = null): NetResult<Nothing>()
}

class ServiceReviewsRepository(
    private val api: ServiceReviewsApi
) {
    suspend fun createRequestMultipart(
        file: File,
        mime: String,
        documentName: String,
        customName: String?,
        serviceType: String,
        description: String,
        priority: String,
        deadlineIso: String?
    ): NetResult<ServiceRequestDto> = safe {
        val mediaType = mime.toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = documentName,
            body = file.asRequestBody(mediaType)
        )

        api.createRequestMultipart(
            file = filePart,
            documentName = documentName.toRb(),
            customName = customName?.toRb(),
            serviceType = serviceType.toRb(),
            description = description.toRb(),
            priority = priority.toRb(),
            deadline = deadlineIso?.toRb()
        )
    }

    /** List requests for the current user id. */
    suspend fun listMyRequests(
        status: String? = null,
        userId: String?
    ): NetResult<List<ServiceRequestDto>> = safe {
        if (userId.isNullOrBlank()) return@safe Response.success(emptyList())
        api.listRequests(
            status = status,
            userId = userId,
            consultantId = null
        )
    }

    // ---- helpers ----
    private fun String.toRb(): RequestBody =
        toRequestBody("text/plain".toMediaTypeOrNull())

    private suspend fun <T> safe(block: suspend () -> Response<T>): NetResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val resp = block()
                if (resp.isSuccessful) {
                    val body = resp.body()
                    android.util.Log.d("API", "success code=${resp.code()} body=$body")
                    if (body != null) NetResult.Ok(body)
                    else NetResult.Err("Empty response body", resp.code())
                } else {
                    val err = resp.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    android.util.Log.w("API", "error code=${resp.code()} err=$err")
                    NetResult.Err(err ?: "HTTP ${resp.code()}", resp.code())
                }
            } catch (e: Exception) {
                android.util.Log.e("API", "exception=${e.message}", e)
                NetResult.Err(e.message ?: "Network error")
            }
        }
}
