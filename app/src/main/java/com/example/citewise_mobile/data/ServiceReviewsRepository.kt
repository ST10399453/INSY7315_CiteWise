package com.example.citewise_mobile.data

import com.example.citewise_mobile.api.*
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

    suspend fun listMyRequests(
        status: String? = null,
        userId: String?
    ): NetResult<List<ServiceRequestDto>> = safe {
        if (userId.isNullOrBlank()) return@safe Response.success(emptyList())
        api.listRequests(status = status, userId = userId, consultantId = null)
    }

    suspend fun listGeneralRequests(
        consultantId: String? = null,
        status: String? = null,
        userId: String? = null
    ): NetResult<List<ServiceRequestDto>> = safe {
        api.listRequests(consultantId = consultantId, status = status, userId = userId)
    }
//Unused functions related to API
//    suspend fun listPendingAssignments(): NetResult<List<ServiceRequestDto>> = safe {
//        api.listPendingAssignments()
//    }
//
//    suspend fun listUnassignedConsultants(): NetResult<List<ConsultantDto>> =
//        when (val resp = safe { api.listUnassignedConsultants() }) {
//            is NetResult.Ok -> NetResult.Ok(resp.data.items)
//            is NetResult.Err -> resp
//        }

    /** Upload annotated file (feedback). Optionally bump status (e.g. "review_submitted"). */
    suspend fun uploadAnnotatedFile(
        requestId: String,
        file: File,
        mime: String = "application/pdf",
        newStatus: String? = null
    ): NetResult<ServiceRequestDto> = safe {
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody(mime.toMediaTypeOrNull())
        )
        val statusRb: RequestBody? = newStatus?.toRb()
        api.uploadAnnotated(
            id = requestId,
            file = filePart,
            status = statusRb
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
                    if (body != null) NetResult.Ok(body)
                    else NetResult.Err("Empty response body", resp.code())
                } else {
                    val err = resp.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    NetResult.Err(err ?: "HTTP ${resp.code()}", resp.code())
                }
            } catch (e: Exception) {
                NetResult.Err(e.message ?: "Network error")
            }
        }
}
