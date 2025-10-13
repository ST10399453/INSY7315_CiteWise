// app/src/main/java/com/example/citewise_mobile/api/DocumentsApi.kt
package com.example.citewise_mobile.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SignedUrlDto(val url: String, val expiresInSeconds: Long, val provider: String)

interface DocumentsApi {
    // Signed URL by documentId
    @GET("/documents/{documentId}/download")
    suspend fun signedUrl(
        @Path("documentId") documentId: String,
        @Query("provider") provider: String? = null,          // "r2"|"azure"
        @Query("disposition") disposition: String? = "inline",// "inline"|"attachment"
        @Query("expires") expires: Int? = 900
    ): Response<SignedUrlDto>

    // Direct streamed file (proxy)
    @GET("/documents/{documentId}/file")
    suspend fun streamFile(
        @Path("documentId") documentId: String,
        @Query("provider") provider: String? = null,
        @Query("disposition") disposition: String? = "inline"
    ): Response<ResponseBody>
}
