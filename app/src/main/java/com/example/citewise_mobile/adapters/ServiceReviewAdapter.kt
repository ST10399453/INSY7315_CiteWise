package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toUiDate

class ServiceReviewAdapter(
    private val items: List<ServiceRequestDto>,
    private val onItemClick: (ServiceRequestDto) -> Unit
) : RecyclerView.Adapter<ServiceReviewAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view

        // Top row
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvPriority: TextView = view.findViewById(R.id.tvPriority)

        // Title + chevron
        val tvServiceTitle: TextView = view.findViewById(R.id.tvServiceTitle)
        val ivChevron: ImageView = view.findViewById(R.id.ivChevron)

        // Meta
        val tvSubmittedDate: TextView = view.findViewById(R.id.tvSubmittedDate)
        val tvStatusLabel: TextView = view.findViewById(R.id.tvStatusLabel)

        // Deadline row
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_review, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]

        // Category
        h.tvCategory.text = item.serviceType.toPretty()

        // Title (prefer CustomName -> file name -> description -> category)
        h.tvServiceTitle.text = when {
            !item.customName.isNullOrBlank() -> item.customName
            !item.originalFileName.isNullOrBlank() -> item.originalFileName
            !item.description.isNullOrBlank() -> item.description
            else -> item.serviceType.toPretty()
        }


        // Status label
        h.tvStatusLabel.text = item.status
            ?.replace("_", " ")
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
            ?: "Pending"

        // Submitted date (safe fallback)
        h.tvSubmittedDate.text = "Submitted: ${item.createdAt?.toUiDate() ?: "—"}"

        // Deadline (robust null/blank handling)
        val deadlineUi = item.deadline?.toUiDate()?.takeIf { !it.isNullOrBlank() }
        if (deadlineUi != null) {
            h.tvDeadline.visibility = View.VISIBLE
            h.tvDeadline.text = "Deadline: $deadlineUi"
        } else {
            h.tvDeadline.visibility = View.GONE

        }

        // Priority text + tint
        val priority = item.priority ?: ServicePriority.LOW
        h.tvPriority.text = priority.name.lowercase().replaceFirstChar { it.uppercase() }
        val colorRes = when (priority) {
            ServicePriority.HIGH -> R.color.priority_High
            ServicePriority.MEDIUM -> R.color.priority_Medium
            ServicePriority.LOW -> R.color.priority_Low
        }
        h.tvPriority.setTextColor(ContextCompat.getColor(h.root.context, colorRes))

        // Item interactions
        h.root.setOnClickListener { onItemClick(item) }
        h.ivChevron.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

/* ---- Helpers ---- */

private fun ServiceType?.toPretty(): String = when (this) {
    ServiceType.PROOFREADING_EDITING -> "Proofreading & Editing"
    ServiceType.FORMATTING_REFERENCING -> "Formatting & Referencing"
    ServiceType.DATA_ANALYSIS_SUPPORT -> "Data Analysis Support"
    ServiceType.RESEARCH_METHODOLOGY_COACHING -> "Research/Methodology Coaching"
    ServiceType.TRANSLATION -> "Translation"
    ServiceType.OTHER, null -> "Other"
}

