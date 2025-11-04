package com.example.citewise_mobile.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.citewise_mobile.api.MessageDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

class ConversationViewModel(
    private val repo: MessagesRepository,
    private val myUid: String,
    private val chatId: String
) : ViewModel() {

    private val _items = MutableStateFlow<List<Message>>(emptyList())
    val items = _items.asStateFlow()

    private var nextAfter: Long? = null
    private var lastSeenEpochMs: Long = System.currentTimeMillis() - 60_000
    private var pollJob: Job? = null

    fun loadInitial() = viewModelScope.launch {
        when (val r = repo.byChat(chatId, limit = 100, after = null)) {
            is NetResult.Ok -> {
                val ui = r.data.messages.map { it.toUi(myUid) }
                nextAfter = r.data.nextAfter
                _items.value = ui
                lastSeenEpochMs = ui.maxOfOrNull { it.timestamp } ?: lastSeenEpochMs
            }
            is NetResult.Err -> { /* TODO: expose error state */ }
        }
        startDeltaPolling()
    }

    fun loadMore() = viewModelScope.launch {
        val cursor = nextAfter ?: return@launch
        when (val r = repo.byChat(chatId, limit = 100, after = cursor)) {
            is NetResult.Ok -> {
                val append = r.data.messages.map { it.toUi(myUid) }
                nextAfter = r.data.nextAfter
                _items.value = (_items.value + append).distinctBy { it.id }
                lastSeenEpochMs = max(lastSeenEpochMs, append.maxOfOrNull { it.timestamp } ?: lastSeenEpochMs)
            }
            is NetResult.Err -> { /* ignore transient errors */ }
        }
    }

    fun send(toUid: String, text: String) = viewModelScope.launch {
        val clientId = UUID.randomUUID().toString()
        val optimistic = Message(
            id = "local-$clientId",
            text = text,
            isMine = true,
            timestamp = System.currentTimeMillis()
        )
        _items.value = _items.value + optimistic

        when (val r = repo.send(toUid, text, clientId)) {
            is NetResult.Ok -> {
                val serverMsg = r.data
                _items.value = _items.value.map {
                    if (it.id == optimistic.id) serverMsg.toUi(myUid) else it
                }
                lastSeenEpochMs = max(lastSeenEpochMs, serverMsg.createdAt ?: lastSeenEpochMs)
            }
            is NetResult.Err -> {
                _items.value = _items.value.filterNot { it.id == optimistic.id }
            }
        }
    }

    private fun startDeltaPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                when (val r = repo.since(lastSeenEpochMs)) {
                    is NetResult.Ok -> {
                        val newOnes = r.data
                            .filter { it.createdAt != null && it.createdAt!! > lastSeenEpochMs }
                            .map { it.toUi(myUid) }
                            .sortedBy { it.timestamp }

                        if (newOnes.isNotEmpty()) {
                            _items.value = (_items.value + newOnes).distinctBy { it.id }
                            lastSeenEpochMs = newOnes.maxOf { it.timestamp }
                        }
                    }
                    else -> { /* ignore */ }
                }
                delay(4_000)
            }
        }
    }

    // --- simple mapper (so this file compiles standalone) ---
    private fun MessageDto.toUi(myUid: String) = Message(
        id = this.id ?: "${this.fromUid}_${this.toUid}_${this.createdAt ?: 0}",
        text = this.body,
        isMine = this.fromUid == myUid,
        timestamp = this.createdAt ?: System.currentTimeMillis()
    )
}
