package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.toUiDate
import com.example.citewise_mobile.toPretty

//This adapter is designed to display a list of completed ServiceRequestDto objects.
class HistoryAdapter(
    private var tasks: List<ServiceRequestDto>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs matching item_consultant_history.xml
        val tvServiceType: TextView = itemView.findViewById(R.id.tvHistoryServiceType)
        val tvHistoryDate: TextView = itemView.findViewById(R.id.tvHistoryDate)
        val tvHistoryStatus: TextView = itemView.findViewById(R.id.tvHistoryStatus)
        val statusDot: View = itemView.findViewById(R.id.statusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_consultant_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val task = tasks[position]

        //Service Type (e.g., Proofreading & Editing)
        holder.tvServiceType.text = task.serviceType?.toPretty() ?: "Service Completed"

        //Completion Date
        holder.tvHistoryDate.text = task.updatedAt.toUiDate()

        //Status (e.g., Completed)
        // Assume all tasks here are "completed" but use the status from DTO for robustness
        val statusText = task.status?.takeIf { it.isNotBlank() } ?: "Completed"
        holder.tvHistoryStatus.text = statusText

        //Status Dot Color
        val dotColorRes = if (statusText == "Completed") R.drawable.ic_dot_blue else R.drawable.ic_dot_green
        holder.statusDot.setBackgroundResource(dotColorRes)

        // Add click listener to view details (will come back)
        holder.itemView.setOnClickListener { /* Navigate to task details */ }
    }

    fun updateList(newList: List<ServiceRequestDto>) {
        this.tasks = newList
        notifyDataSetChanged()
    }
}