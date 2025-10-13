// app/src/main/java/com/example/citewise_mobile/data/MessagesRepository.kt
package com.example.citewise_mobile.data

import com.example.citewise_mobile.api.MessagesApi
import com.example.citewise_mobile.api.MessageDto
import com.example.citewise_mobile.api.SendMessagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class MessagesRepository(private val api: MessagesApi) {

    suspend fun send(toUid: String, body: String, clientId: String? = null): NetResult<MessageDto> =
        safe { api.send(SendMessagePayload(toUid, body, clientId)) }

    suspend fun since(sinceEpochMs: Long): NetResult<List<MessageDto>> =
        safe { api.listSince(sinceEpochMs) }

    suspend fun withPeer(peerId: String, limit: Int? = null, before: Long? = null): NetResult<List<MessageDto>> =
        safe { api.listWithPeer(peerId, limit, before) }

    private suspend fun <T> safe(block: suspend () -> Response<T>): NetResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val r = block()
                if (r.isSuccessful) {
                    r.body()?.let { NetResult.Ok(it) } ?: NetResult.Err("Empty body", r.code())
                } else {
                    NetResult.Err(r.errorBody()?.string().orEmpty().ifBlank { "HTTP ${r.code()}" }, r.code())
                }
            } catch (t: Throwable) {
                NetResult.Err(t.message ?: "Network error")
            }
        }
}
