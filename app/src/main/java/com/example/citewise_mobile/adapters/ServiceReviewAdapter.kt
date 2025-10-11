package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toUiDate
import com.google.android.material.progressindicator.LinearProgressIndicator

class ServiceReviewAdapter(
    private val items: List<ServiceRequestDto>
) : RecyclerView.Adapter<ServiceReviewAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvServiceTitle: TextView = view.findViewById(R.id.tvServiceTitle)
        val tvCreatedDate: TextView = view.findViewById(R.id.tvCreatedDate)
        val tvUpdated: TextView = view.findViewById(R.id.tvUpdated)
        val tvStatusChip: TextView = view.findViewById(R.id.tvStatusChip)
        val progressStage: LinearProgressIndicator = view.findViewById(R.id.progressStage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_review, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = items[position]

        // Title = enum pretty name (fallback to "Request")
        h.tvServiceTitle.text = it.serviceType.toPretty().ifBlank { "Request" }

        // Dates: FlexTime -> yyyy-MM-dd or "—"
        h.tvCreatedDate.text = "Created: ${it.createdAt.toUiDate()}"
        h.tvUpdated.text     = "Updated: ${it.updatedAt.toUiDate()}"

        // Status chip + staged progress
        val status = it.status.orEmpty()
        h.tvStatusChip.text = status.ifBlank { "Submitted" }
        h.progressStage.progress = statusToProgress(status) // 0/33/66/100
    }

    override fun getItemCount(): Int = items.size
}

private fun ServiceType?.toPretty(): String =
    when (this) {
        ServiceType.PROOFREADING_EDITING -> "Proofreading & Editing"
        ServiceType.FORMATTING_REFERENCING -> "Formatting & Referencing"
        ServiceType.DATA_ANALYSIS_SUPPORT -> "Data Analysis Support"
        ServiceType.RESEARCH_METHODOLOGY_COACHING -> "Methodology Coaching"
        ServiceType.TRANSLATION -> "Translation"
        ServiceType.OTHER, null -> "Other"
    }

private fun statusToProgress(status: String): Int = when (status.trim().lowercase()) {
    "submitted", "pending" -> 0
    "in_progress", "in progress" -> 33
    "awaiting_feedback", "awaiting feedback" -> 66
    "complete", "completed", "done", "approved" -> 100
    else -> 0
}
