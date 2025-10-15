package com.example.citewise_mobile.offline

import com.example.citewise_mobile.api.ServiceRequestDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun epochToIsoUTC(epochMillis: Long?): String? =
    epochMillis?.let { isoFmt.format(Date(it)) }

/** Map server → local. We keep server truth for status/documentId/etc. */
fun ServiceRequestDto.toEntityPreservingLocal(
    localFallback: ServiceRequestEntity? = null
): ServiceRequestEntity {
    val docName = localFallback?.documentName ?: (documentId ?: "document")
    val svcType = serviceType?.name ?: (localFallback?.serviceType ?: "OTHER")
    val desc    = description ?: (localFallback?.description ?: "")
    val prio    = priority?.name ?: (localFallback?.priority ?: "MEDIUM")
    val deadlineIso = epochToIsoUTC(deadline?.epochMillis)

    return (localFallback ?: ServiceRequestEntity(
        remoteId     = id,
        documentName = docName,
        serviceType  = svcType,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        filePath     = localFallback?.filePath,
        status       = status ?: "submitted",
        syncState    = SyncState.SYNCED
    )).copy(
        remoteId    = id,
        documentId  = documentId, // preserve server documentId
        status      = status ?: localFallback?.status ?: "submitted",
        serviceType = svcType,
        description = desc,
        priority    = prio,
        deadlineIso = deadlineIso,
        updatedAt   = System.currentTimeMillis()
    )
}
