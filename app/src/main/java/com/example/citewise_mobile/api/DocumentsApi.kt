package com.example.citewise_mobile.api

import com.example.citewise_mobile.offline.DocumentEntity
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

data class SignedUrlDto(
    val url: String,
    val expiresInSeconds: Long,
    val provider: String
)

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

data class DeleteResourceResult(
    val ok: Boolean,
    val id: String
)

fun ResourceDto.toDocumentEntity(): DocumentEntity = DocumentEntity(
    id = id,
    ownerUid = "",
    fileName = name.ifBlank { "Untitled" },
    mimeType = mimeType ?: "application/pdf",
    sizeBytes = size,
    updatedAt = updatedAt ?: System.currentTimeMillis(),
    etag = etag,
    remoteUrlHint = signedUrlHint,
    localPath = null,
    downloadedAt = null
)

interface DocumentsApi {

    // documents/*
    @GET("documents/{documentId}/download")
    suspend fun signedUrl(
        @Path("documentId") documentId: String,
        @Query("provider") provider: String? = null,
        @Query("disposition") disposition: String? = "attachment",
        @Query("expires") expires: Int? = 900,
        @Header("Authorization") auth: String? = null
    ): Response<SignedUrlDto>

    @GET("documents/{documentId}/file")
    @Streaming
    suspend fun streamFile(
        @Path("documentId") documentId: String,
        @Query("provider") provider: String? = null,
        @Query("disposition") disposition: String? = "attachment",
        @Header("Authorization") auth: String? = null
    ): Response<ResponseBody>

    // resources/*
    @GET("resources")
    suspend fun listResources(
        @Query("faculty") faculty: String? = null,
        @Query("visibility") visibility: String? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        @Query("dir") dir: String? = null,
        @Header("Authorization") auth: String? = null
    ): Response<List<ResourceDto>>

    @Multipart
    @POST("resources")
    suspend fun createResource(
        @Part("name") name: RequestBody,
        @Part("faculty") faculty: RequestBody,
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
        @Header("Authorization") auth: String? = null
    ): Response<ResourceDto>

    @GET("resources/{id}/download")
    suspend fun resourceSignedUrl(
        @Path("id") id: String,
        @Query("disposition") disposition: String = "inline",
        @Query("provider") provider: String? = null,
        @Header("Authorization") auth: String? = null
    ): Response<SignedUrlDto>

    @GET("resources/{id}/file")
    @Streaming
    suspend fun streamResource(
        @Path("id") id: String,
        @Query("provider") provider: String? = null,
        @Query("disposition") disposition: String? = "inline",
        @Header("Authorization") auth: String? = null
    ): Response<ResponseBody>

    @DELETE("resources/{id}")
    suspend fun deleteResource(
        @Path("id") id: String
    ): Response<DeleteResourceResult>
}
