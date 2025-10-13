// app/src/main/java/com/example/citewise_mobile/api/MessagesApi.kt
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

interface MessagesApi {

    @POST("/messages")
    suspend fun send(@Body payload: SendMessagePayload): Response<MessageDto>

    // conversation with a peer
    @GET("/messages")
    suspend fun listWithPeer(
        @Query("peerId") peerId: String,
        @Query("limit") limit: Int? = null,
        @Query("before") beforeEpochMs: Long? = null
    ): Response<List<MessageDto>>

    // delta sync (involving current user)
    @GET("/messages/since")
    suspend fun listSince(
        @Query("since") sinceEpochMs: Long
    ): Response<List<MessageDto>>
}
