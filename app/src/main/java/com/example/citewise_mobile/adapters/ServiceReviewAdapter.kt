package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.FlexTime
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
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvStatusLabel: TextView = view.findViewById(R.id.tvStatusLabel)
//        val statusDonut: CircularProgressIndicator = view.findViewById(R.id.statusDonut)
        val ivChevron: ImageView = view.findViewById(R.id.ivChevron)
        val tvServiceTitle: TextView = view.findViewById(R.id.tvServiceTitle)
        //val tvStage: TextView = view.findViewById(R.id.tvStage)

        val priorityIndicator: View = view.findViewById(R.id.priorityIndicator)
        val tvPriority: TextView = view.findViewById(R.id.tvPriority)


        val tvSubmittedDate: TextView = view.findViewById(R.id.tvSubmittedDate)

        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_review, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]

        // Service type / category
        h.tvCategory.text = item.serviceType.toPretty()

        // Status label
        h.tvStatusLabel.text = item.status?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() }
            ?: "Pending"

        // Progress donut animation
        val progress = statusToProgress(item.status.orEmpty())
//        h.statusDonut.setProgressCompat(progress, true)

        // Chevron
        h.ivChevron.setOnClickListener { onItemClick(item) }

        // Big title: now shows Service Title (fallbacks to category, then description)
        h.tvServiceTitle.text = when {
            !item.title.isNullOrBlank() -> item.title
            !item.description.isNullOrBlank() -> item.description
            else -> item.serviceType.toPretty()
        }

        h.tvSubmittedDate.text = "Submitted: ${item.createdAt.toUiDate()}"

        // Due / deadline date //DONT DISPLAY??????
        h.tvDeadline.text = "Deadline: ${item.deadline.toUiDate()}"



        val priority = item.priority ?: ServicePriority.LOW
        h.tvPriority.text = priority.name.lowercase().replaceFirstChar { it.uppercase() }

// Tint the indicator instead of replacing background
        val indicatorDrawable = h.priorityIndicator.background.mutate()
        val colorRes = when(priority) {
            ServicePriority.HIGH -> R.color.gradient_middle
            ServicePriority.MEDIUM -> R.color.gradient_start
            ServicePriority.LOW -> R.color.gradient_end
        }
        indicatorDrawable.setTint(h.root.context.getColor(colorRes))
        h.priorityIndicator.background = indicatorDrawable

// Text color
        h.tvPriority.setTextColor(h.root.context.getColor(colorRes))


        // Stage pill (shows status in friendly form)
//        h.tvStage.text = item.status?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() }
//            ?: "Pending"

        // Root click
        h.root.setOnClickListener { onItemClick(item) }
//        h.statusDonut.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

// ---- Helpers ----

private fun ServiceType?.toPretty(): String = when (this) {
    ServiceType.PROOFREADING_EDITING -> "Proofreading & Editing"
    ServiceType.FORMATTING_REFERENCING -> "Formatting & Referencing"
    ServiceType.DATA_ANALYSIS_SUPPORT -> "Data Analysis Support"
    ServiceType.RESEARCH_METHODOLOGY_COACHING -> "Research/Methodology Coaching"
    ServiceType.TRANSLATION -> "Translation"
    ServiceType.OTHER, null -> "Other"
}

private fun statusToProgress(status: String): Int = when (status.trim().lowercase()) {
    "submitted", "pending" -> 25
    "assigned", "in_progress", "in progress", "in review" -> 50
    "feedback", "awaiting_feedback", "awaiting feedback", "feedback ready" -> 75
    "complete", "completed", "done", "approved" -> 100
    else -> 25
}
