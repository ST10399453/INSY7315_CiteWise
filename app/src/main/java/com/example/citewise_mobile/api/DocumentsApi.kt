package com.example.citewise_mobile.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

data class SignedUrlDto(
    val url: String,
    val expiresInSeconds: Long,
    val provider: String
)

interface DocumentsApi {

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