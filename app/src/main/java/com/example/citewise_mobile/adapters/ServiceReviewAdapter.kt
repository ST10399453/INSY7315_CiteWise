package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class ServiceReviewAdapter(
    private val data: MutableList<ServiceRequestDto>,
    private val onClick: (ServiceRequestDto) -> Unit
) : RecyclerView.Adapter<ServiceReviewAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView   = v.findViewById(R.id.taskCard)
        val tvServiceType: TextView  = v.findViewById(R.id.tvCategory)
        val tvServiceTitle: TextView = v.findViewById(R.id.tvServiceTitle)
        val tvStatus: TextView       = v.findViewById(R.id.tvStatusLabel)
        val tvPriority: TextView     = v.findViewById(R.id.tvPriority)
        val tvCreated: TextView      = v.findViewById(R.id.tvSubmittedDate)
        val tvDeadline: TextView     = v.findViewById(R.id.tvDeadline)
        val statusDot: View          = v.findViewById(R.id.statusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service_review, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val req = data[pos]
        val context = h.itemView.context

        // ─── Service Info ───
        h.tvServiceType.text = req.serviceType?.name?.replace('_', ' ') ?: "Other"
        h.tvServiceTitle.text = req.customName ?: req.originalFileName ?: "Service Request"

        // ─── Status ───
        val statusLabel = req.status?.replace('_', ' ')?.lowercase()?.replaceFirstChar {
            it.titlecase(Locale.getDefault())
        } ?: "Pending"
        h.tvStatus.text = statusLabel

        // Change DOT color instead of text color
        val dotColorRes = when (statusLabel.lowercase()) {
            "assigned" -> R.color.blue_400        // Assigned → Blue
            "completed", "done" -> R.color.green_500   // Completed/Done → Green
            "pending", "submitted" -> R.color.priority_Medium // Pending/Submitted → Orange
            else -> R.color.light_highlight              // Default fallback
        }
        h.statusDot.background.setTint(ContextCompat.getColor(context, dotColorRes))

        // ─── Priority ───
        val priority = req.priority?.name ?: "MEDIUM"
        val priorityColor = when (priority.uppercase()) {
            "HIGH" -> R.color.priority_High
            "MEDIUM" -> R.color.priority_Medium
            else -> R.color.priority_Low
        }
        h.tvPriority.text = priority.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        h.tvPriority.setTextColor(ContextCompat.getColor(context, priorityColor))

        // ─── Dates ───
        val createdMillis = req.createdAt?.epochMillis ?: 0L
        h.tvCreated.text = "Created: ${formatDate(createdMillis)}"

        val deadlineMillis = req.deadline?.epochMillis
        if (deadlineMillis != null) {
            h.tvDeadline.visibility = View.VISIBLE
            h.tvDeadline.text = "Deadline: ${formatDate(deadlineMillis)}"
        } else {
            h.tvDeadline.visibility = View.GONE
        }

        // ─── Click ───
        h.card.setOnClickListener { onClick(req) }
    }

    private fun formatDate(ms: Long?): String =
        ms?.takeIf { it > 0 }?.let { dateFmt.format(Date(it)) } ?: "—"

    fun reset(newItems: List<ServiceRequestDto>) {
        data.clear()
        data.addAll(newItems)
        notifyDataSetChanged()
    }
}
