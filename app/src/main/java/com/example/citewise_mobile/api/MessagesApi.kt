package com.example.citewise_mobile.api

import retrofit2.Response
import retrofit2.http.*

data class MessageDto(
    val id: String? = null,
    val fromUid: String,
    val toUid: String,
    val body: String,
    val clientId: String? = null,
    val status: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class SendMessagePayload(
    val toUid: String,
    val body: String,
    val clientId: String? = null
)

data class ChatPageDto(
    val success: Boolean,
    val chatId: String,
    val messages: List<MessageDto>,
    val nextAfter: Long?,            // server cursor to continue forward
    val meta: Meta?                  // e.g., { order: "asc", limit: 100 }
)
data class Meta(val order: String?, val limit: Int?)

interface MessagesApi {
    @POST("/messages")
    suspend fun send(@Body payload: SendMessagePayload): Response<MessageDto>

    @GET("/messages")
    suspend fun listWithPeer(
        @Query("peerId") peerId: String,
        @Query("limit") limit: Int? = null,
        @Query("before") beforeEpochMs: Long? = null
    ): Response<List<MessageDto>>

    @GET("/messages/since")
    suspend fun listSince(@Query("since") sinceEpochMs: Long): Response<List<MessageDto>>

    @GET("/messages/{chatId}")
    suspend fun listByChat(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int? = 100,
        @Query("after") afterEpochMs: Long? = null
    ): Response<ChatPageDto>
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