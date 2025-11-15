package com.example.citewise_mobile.api

import com.example.citewise_mobile.offline.ResourceEntity
import com.example.citewise_mobile.offline.SyncState
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** DTO returned by the backend for a resource. */
data class ResourceDto(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val faculty: String? = null,
    val category: String? = null,
    val description: String? = null,
    val adminUid: String? = null,
    val documentId: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val createdAt: FlexTime? = null,
    val updatedAt: FlexTime? = null,
    val etag: String? = null,
    val signedUrlHint: String? = null
)

/** Map DTO -> Room entity used by your UI. */
fun ResourceDto.toResourceEntity(): ResourceEntity {
    val title = (displayName ?: name).trim().ifEmpty { "Untitled" }
    val file  = (fileName ?: displayName ?: name).trim().ifEmpty { null }
    val updatedMs = updatedAt?.epochMillis ?: System.currentTimeMillis()
    val createdMs = createdAt?.epochMillis ?: updatedMs

    return ResourceEntity(
        remoteId   = id,
        adminUid   = adminUid,
        title      = title,
        description= description,
        category   = category,
        faculty    = faculty,
        documentId = documentId,
        fileName   = file,
        filePath   = null,
        syncState  = SyncState.SYNCED,
        createdAt  = createdMs,
        updatedAt  = updatedMs
    )
}

interface ResourcesApi {

    @Multipart
    @POST("resources")
    suspend fun createResourceMultipart(
        @Part file: MultipartBody.Part,
        @Part("mime") mime: RequestBody?,
        @Part("name") name: RequestBody,
        @Part("faculty") faculty: RequestBody,
        @Part("category") category: RequestBody?,
        @Part("description") description: RequestBody?,
        @Header("Authorization") auth: String? = null
    ): Response<ResourceDto>

    @GET("resources")
    suspend fun listResources(
        @Query("faculty") faculty: String? = null,
        @Query("visibility") visibility: String? = null,   // "all" | "students" | "admins"
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,               // "alpha" | "date"
        @Query("dir") dir: String? = null,                 // "asc" | "desc"
        @Header("Authorization") auth: String? = null
    ): Response<List<ResourceDto>>

    @GET("resources/{id}")
    suspend fun getResource(
        @Path("id") id: String,
        @Header("Authorization") auth: String? = null
    ): Response<ResourceDto>

    @DELETE("resources/{id}")
    suspend fun deleteResource(
        @Path("id") id: String,
        @Header("Authorization") auth: String,
        @Query("strict") strict: Boolean = false
    ): Response<Unit>

    @GET("resources/{id}/download")
    suspend fun getResourceDownloadUrl(
        @Path("id") id: String?,
        @Query("disposition") disposition: String = "attachment",
        @Query("expires") expires: Int = 900
    ): SignedUrlDto

    @Streaming
    @GET("resources/{id}/file")
    suspend fun streamResourceFile(
        @Path("id") id: String?,
        @Query("disposition") disposition: String = "attachment"
    ): Response<ResponseBody>

}
/*
 * REFERENCES
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”.
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 */