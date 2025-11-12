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
    val docName     = localFallback?.documentName ?: (documentId ?: "document")
    val svcType     = serviceType?.name ?: (localFallback?.serviceType ?: "OTHER")
    val quotationId = quotationId ?: (localFallback?.quotationId ?: "")
    val desc        = description ?: (localFallback?.description ?: "")
    val prio        = priority?.name ?: (localFallback?.priority ?: "MEDIUM")
    val deadlineIso = epochToIsoUTC(deadline?.epochMillis)
    val custom      = this.customName ?: localFallback?.customName ?: ""

    return (localFallback ?: ServiceRequestEntity(
        remoteId     = id,
        userId       = userId ?: localFallback?.userId,
        consultantId = consultantId ?: localFallback?.consultantId,
        documentName = docName,
        serviceType  = svcType,
        quotationId  = quotationId,
        quotationWords   = this.quotationWords ?: localFallback?.quotationWords,
        quotationAmount  = this.quotationAmount ?: localFallback?.quotationAmount,
        quotationCurrency= this.quotationCurrency ?: localFallback?.quotationCurrency,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        filePath     = localFallback?.filePath,
        status       = status ?: "submitted",
        feedback     = feedback ?: localFallback?.feedback,
        feedbackFileName = feedbackFileName ?: localFallback?.feedbackFileName,
        feedbackFileUrl  = feedbackFileUrl  ?: localFallback?.feedbackFileUrl,
        syncState    = SyncState.SYNCED
    )).copy(
        remoteId     = id,
        userId       = userId ?: localFallback?.userId,
        consultantId = consultantId ?: localFallback?.consultantId,
        documentId   = documentId,
        status       = status ?: localFallback?.status ?: "submitted",
        serviceType  = svcType,
        quotationId  = quotationId,
        quotationWords   = this.quotationWords ?: localFallback?.quotationWords,
        quotationAmount  = this.quotationAmount ?: localFallback?.quotationAmount,
        quotationCurrency= this.quotationCurrency ?: localFallback?.quotationCurrency,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        customName   = custom,
        feedback     = feedback ?: localFallback?.feedback,
        feedbackFileName = feedbackFileName ?: localFallback?.feedbackFileName,
        feedbackFileUrl  = feedbackFileUrl  ?: localFallback?.feedbackFileUrl,
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
        quotationId = quotationId,

        quotationWords   = quotationWords,
        quotationAmount  = quotationAmount,
        quotationCurrency= quotationCurrency,

        description = description,
        priority = runCatching { ServicePriority.valueOf(priority ?: "LOW") }.getOrNull(),
        deadline = null,
        createdAt = createdAt.toFlex(),
        updatedAt = updatedAt.toFlex(),
        feedback = feedback,
        studentName = null,
        originalFileName = documentName.takeIf { it.isNotEmpty() },
        customName = customName.takeIf { it.isNotEmpty() },
        originalFileUrl = null,
        feedbackFileName = feedbackFileName,
        feedbackFileUrl = feedbackFileUrl
    )
