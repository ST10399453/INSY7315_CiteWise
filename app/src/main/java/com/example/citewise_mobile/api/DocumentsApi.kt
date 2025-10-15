package com.example.citewise_mobile.api

import com.example.citewise_mobile.offline.DocumentEntity
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

// ─────────────────────────────────────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────────────────────────────────────

data class SignedUrlDto(
    val url: String,
    val expiresInSeconds: Long,
    val provider: String
)

/** Server payload for a resource visible in the Resources screen. */
data class ResourceDto(
    val id: String,
    val name: String,
    val faculty: String,
    val category: String,           // WRITING_GUIDE | TEMPLATE | AI_USAGE
    val mimeType: String? = null,
    val size: Long? = null,
    val updatedAt: Long? = null,
    val etag: String? = null,
    val signedUrlHint: String? = null
)

/** Map a ResourceDto to your Room entity used by the grid. */
fun ResourceDto.toDocumentEntity(): DocumentEntity = DocumentEntity(
    id = id,
    ownerUid = "",                                   // shared resource; not a single owner
    fileName = name.ifBlank { "Untitled" },
    mimeType = mimeType ?: "application/pdf",
    sizeBytes = size,
    updatedAt = updatedAt ?: System.currentTimeMillis(),
    etag = etag,
    remoteUrlHint = signedUrlHint,
    localPath = null,                                // set when user marks as offline
    downloadedAt = null
)

// ─────────────────────────────────────────────────────────────────────────────
// API
// ─────────────────────────────────────────────────────────────────────────────

interface DocumentsApi {

    // Existing documents endpoints (in-app viewer / downloads for request files)
    @GET("documents/{documentId}/download")
    suspend fun signedUrl(
        @Path("documentId") documentId: String,
        @Query("provider") provider: String? = null,           // "r2" | "azure"
        @Query("disposition") disposition: String? = "attachment",
        @Query("expires") expires: Int? = 900
    ): Response<SignedUrlDto>

    @GET("documents/{documentId}/file")
    @Streaming
    suspend fun streamFile(
        @Path("documentId") documentId: String,
        @Query("provider") provider: String? = null,
        @Query("disposition") disposition: String? = "attachment"
    ): Response<ResponseBody>

    // Resources (students): list with optional filters + sort
    @GET("resources")
    suspend fun listResources(
        @Query("faculty") faculty: String? = null,          // e.g. "Engineering"
        @Query("visibility") visibility: String? = null,    // "all" | "students" | "admins"
        @Query("q") q: String? = null,                      // name search
        @Query("sort") sort: String? = null,                // "alpha" | "date"
        @Query("dir") dir: String? = null                   // "asc" | "desc"
    ): Response<List<ResourceDto>>

    // Resources (admins): create via multipart
    @Multipart
    @POST("resources")
    suspend fun createResource(
        @Part("name") name: RequestBody,
        @Part("faculty") faculty: RequestBody,
        @Part("category") category: RequestBody,            // "WRITING_GUIDE" | "TEMPLATE" | "AI_USAGE"
        @Part file: MultipartBody.Part
    ): Response<ResourceDto>

    @GET("resources/{id}/download")
    suspend fun resourceSignedUrl(
        @Path("id") id: String,
        @Query("disposition") disposition: String = "inline",   // or "attachment"
        @Query("provider") provider: String? = null
    ): Response<SignedUrlDto>

    @GET("resources/{id}/file")
    @Streaming
    suspend fun streamResource(
        @Path("id") id: String,
        @Query("provider") provider: String? = null,        // optional: "r2" | "azure"
        @Query("disposition") disposition: String? = "inline"
    ): Response<ResponseBody>
}
