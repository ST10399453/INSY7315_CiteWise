package com.example.citewise_mobile.api

import java.io.Serializable

enum class ServiceType : Serializable {
    PROOFREADING_EDITING,
    FORMATTING_REFERENCING,
    DATA_ANALYSIS_SUPPORT,
    RESEARCH_METHODOLOGY_COACHING,
    TRANSLATION,
    OTHER
}

enum class ServicePriority : Serializable { LOW, MEDIUM, HIGH }

data class ServiceRequestDto(
    val id: String? = null,
    val status: String? = null,
    val documentId: String? = null,
    val userId: String? = null,
    val consultantId: String? = null,
    val serviceType: ServiceType? = null,

    // quotation link
    val quotationId: String? = null,

    val quotationWords: Int? = null,
    val quotationAmount: Double? = null,
    val quotationCurrency: String? = null,

    val description: String? = null,
    val priority: ServicePriority? = null,
    val deadline: FlexTime? = null,
    val createdAt: FlexTime? = null,
    val updatedAt: FlexTime? = null,
    val feedback: String? = null,
    val studentName: String? = null,
    val originalFileName: String? = null,
    val customName: String? = null,

    // files
    val originalFileUrl: String? = null,
    val feedbackFileName: String? = null,
    val feedbackFileUrl: String? = null
) : Serializable

// ---- Small request payloads ----

data class AssignRequestPayload(
    val consultantId: String,
    val deadline: String? = null // ISO-8601 string
)

data class UpdateAssignmentPayload(
    val consultantId: String? = null,
    val deadline: String? = null
)

data class SubmitReviewPayload(
    val outcome: String,        // "approve" | "reject" | "fail"
    val feedback: String? = null
)
