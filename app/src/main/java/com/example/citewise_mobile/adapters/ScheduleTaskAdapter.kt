package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat.getColorStateList
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceReviewsApi
import com.example.citewise_mobile.toPrettyTag
import com.example.citewise_mobile.toPretty

class ScheduleTaskAdapter(
    private var tasks: List<ServiceRequestDto>,
    private val onClick: (ServiceRequestDto) -> Unit
) : RecyclerView.Adapter<ScheduleTaskAdapter.ScheduleTaskViewHolder>() {

    class ScheduleTaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Match IDs from item_schedule_task.xml
        val statusDot: View = itemView.findViewById(R.id.statusDot)
        val serviceType: TextView = itemView.findViewById(R.id.scheduleServiceType)
        val documentName: TextView = itemView.findViewById(R.id.scheduleDocumentName)
        val urgencyTag: TextView = itemView.findViewById(R.id.scheduleUrgencyTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleTaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_task, parent, false)
        return ScheduleTaskViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: ScheduleTaskViewHolder, position: Int) {
        val task = tasks[position]

        //Service Type
        holder.serviceType.text = task.serviceType?.toPretty() ?: "Other Service"

        //Document Name
        holder.documentName.text = task.customName?.takeIf { it.isNotBlank() } ?: "Document"

        //Urgency Tag (Text only, since the dot and text color imply urgency)
        holder.urgencyTag.text = task.priority?.toPrettyTag() ?: "Low"

        //Status Dot (You would dynamically change the color of the dot drawable here)
      //  holder.statusDot.backgroundTintList = getColorStateList(context, R.color.priority_Low)

        //Click Listener
        holder.itemView.setOnClickListener { onClick(task) }
    }

    fun updateList(newList: List<ServiceRequestDto>) {
        this.tasks = newList
        notifyDataSetChanged()
    }
}