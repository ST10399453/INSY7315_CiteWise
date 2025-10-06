package com.example.citewise_mobile.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

/**
 * Represents a user's service request ready to be reviewed/assigned.
 */
enum class ServiceType { PROOFREADING_EDITING, FORMATTING_REFERENCING, DATA_ANALYSIS_SUPPORT, RESEARCH_METHODOLOGY_COACHING, TRANSLATION, OTHER }
enum class ServicePriority { LOW, MEDIUM, HIGH }

data class ServiceReview(
    val userId: String,
    val documentId: String,
    val consultantId: String?,      // assigned later
    val serviceType: ServiceType,
    val description: String,
    val priority: ServicePriority,
    val deadline: Timestamp?,       // nullable
    val createdAt: Timestamp?       // set by server
) {
    fun toMapWithServerTimestamp(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "documentId" to documentId,
        "consultantId" to consultantId,
        "serviceType" to serviceType.name,
        "description" to description,
        "priority" to priority.name,
        "deadline" to deadline,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
