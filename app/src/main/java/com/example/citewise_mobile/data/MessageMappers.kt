package com.example.citewise_mobile.data

import com.example.citewise_mobile.api.MessageDto

fun MessageDto.toUi(myUid: String) = Message(
    id = id ?: "${clientId ?: "pending"}-${createdAt ?: System.nanoTime()}",
    text = body,
    isMine = fromUid == myUid,
    timestamp = createdAt ?: System.currentTimeMillis()
)
