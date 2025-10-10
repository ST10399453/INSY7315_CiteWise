package com.example.citewise_mobile.api

enum class ServiceType {
    PROOFREADING_EDITING,
    FORMATTING_REFERENCING,
    DATA_ANALYSIS_SUPPORT,
    RESEARCH_METHODOLOGY_COACHING,
    TRANSLATION,
    OTHER
}

enum class ServicePriority { LOW, MEDIUM, HIGH }

/**
 * Server response after creating/fetching a request.
 * Match these to your backend fields if they differ.
 */
data class ServiceRequestDto(
    val id: String? = null,
    val status: String? = null,
    val documentId: String? = null,
    val userId: String? = null,
    val consultantId: String? = null,
    val serviceType: ServiceType? = null,
    val description: String? = null,
    val priority: ServicePriority? = null,
    val deadline: String? = null,     // ISO-8601
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val feedback: String? = null
)

// Small request payloads for assignment/review ops
data class AssignRequestPayload(
    val consultantId: String,
    val deadline: String? = null
)

data class UpdateAssignmentPayload(
    val consultantId: String? = null,
    val deadline: String? = null
)

data class SubmitReviewPayload(
    val outcome: String,        // "approve" | "reject" | "fail" (align with backend)
    val feedback: String? = null
)
