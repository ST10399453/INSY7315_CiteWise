package com.example.citewise_mobile.offline

import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toFlex
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
fun ServiceRequestDto.toEntityPreservingLocalFallback(localFallback: ServiceRequestEntity?): ServiceRequestEntity {
    val docName    = localFallback?.documentName ?: (documentId ?: "document")
    val svcType    = serviceType?.name ?: (localFallback?.serviceType ?: "OTHER")
    //val titleText  = title ?: (localFallback?.title ?: "")
    val quotationId =quotationId ?: (localFallback?.quotationId?: "")
    val desc       = description ?: (localFallback?.description ?: "")
    val prio       = priority?.name ?: (localFallback?.priority ?: "MEDIUM")
    val deadlineIso = epochToIsoUTC(deadline?.epochMillis)

    return (localFallback ?: ServiceRequestEntity(
        remoteId     = id,
        userId       = userId ?: localFallback?.userId,          // ✅ keep userId
        consultantId = consultantId ?: localFallback?.consultantId,
        documentName = docName,
        serviceType  = svcType,
        //title        = titleText,
        quotationId = quotationId,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        filePath     = localFallback?.filePath,
        status       = status ?: "submitted",
        syncState    = SyncState.SYNCED
    )).copy(
        remoteId     = id,
        userId       = userId ?: localFallback?.userId,          // ✅ keep userId
        consultantId = consultantId ?: localFallback?.consultantId,
        documentId   = documentId,                                // ✅ take server documentId (GUID)
        status       = status ?: localFallback?.status ?: "submitted",
        serviceType  = svcType,
        //title        = titleText,
        quotationId = quotationId,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        updatedAt    = System.currentTimeMillis()
    )
}

/** Map local entity → DTO for API communication */
fun ServiceRequestEntity.toServiceRequestDto(): ServiceRequestDto =
    ServiceRequestDto(
        id = remoteId,
        status = status,
        documentId = documentId,
        userId = userId,
        consultantId = consultantId,
        serviceType = runCatching { ServiceType.valueOf(serviceType) }.getOrNull(),
        //title = title,
        quotationId = quotationId,
        description = description,
        priority = runCatching { ServicePriority.valueOf(priority ?: "LOW") }.getOrNull(),
        deadline = null,
        createdAt = createdAt.toFlex(),
        updatedAt = updatedAt.toFlex(),
        feedback = feedback,
        studentName = null,
        originalFileName = documentName.takeIf { it.isNotEmpty() },
        originalFileUrl = null,
        feedbackFileName = null,
        feedbackFileUrl = null
    )
