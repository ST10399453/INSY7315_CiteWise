package com.example.citewise_mobile

import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceType
import java.util.Locale


// Extension Functions for DTO Display


/** Maps ServiceType enum to readable string (e.g., Proofreading & Editing) */
fun ServiceType.toPretty(): String = when (this) {
    ServiceType.PROOFREADING_EDITING -> "Proofreading & Editing"
    ServiceType.FORMATTING_REFERENCING -> "Formatting & Referencing"
    ServiceType.DATA_ANALYSIS_SUPPORT -> "Data Analysis Support"
    ServiceType.RESEARCH_METHODOLOGY_COACHING -> "Research Methodology"
    ServiceType.TRANSLATION -> "Translation"
    ServiceType.OTHER -> "Other"
}

/** Maps ServicePriority enum to dashboard tag text (e.g., HIGH -> "Urgent") */
fun ServicePriority.toPrettyTag(): String = when (this) {
    ServicePriority.LOW -> "Low"
    ServicePriority.MEDIUM -> "Medium"
    ServicePriority.HIGH -> "Urgent"
}

/** Maps status string to readable progress text (e.g., "in_progress" -> "In Progress") */
fun String.toPrettyStatus(): String = when (this.trim().lowercase(Locale.ROOT)) {
    "submitted", "pending" -> "Submitted"
    "assigned" -> "Assigned"
    "in_progress", "in progress" -> "In Progress"
    "feedback", "awaiting_feedback", "feedback ready" -> "Awaiting Feedback"
    "complete", "completed", "done" -> "Completed"
    else -> this // return original if not matched
}

/** Maps status string to a progress bar percentage (0-100) */
fun statusToProgress(status: String): Int = when (status.trim().lowercase(Locale.ROOT)) {
    "submitted", "pending" -> 25
    "assigned" -> 40
    "in_progress", "in progress" -> 50
    "feedback", "awaiting_feedback", "feedback ready" -> 75
    "complete", "completed", "done" -> 100
    else -> 25
}
