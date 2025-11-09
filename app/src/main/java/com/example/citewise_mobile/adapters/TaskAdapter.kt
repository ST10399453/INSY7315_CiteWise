package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.toUiDate
import com.example.citewise_mobile.statusToProgress
import com.example.citewise_mobile.toPretty
import com.example.citewise_mobile.toPrettyStatus
import com.example.citewise_mobile.toPrettyTag

/**
 * Adapter for the main task list in the Consultant Dashboard ("Assigned to me").
 * Displays Service Type, Document Name, Priority, Progress, Status, and Deadline.
 */
class TaskAdapter(
    private var tasks: List<ServiceRequestDto>,
    private val onClick: (ServiceRequestDto) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs matching item_consultant_task_card.xml
        val serviceType: TextView = itemView.findViewById(R.id.taskServiceType)
        val documentName: TextView = itemView.findViewById(R.id.taskDocumentName)
        val urgencyTag: TextView = itemView.findViewById(R.id.taskUrgencyTag)
        val progressBar: ProgressBar = itemView.findViewById(R.id.taskProgressBar)
        val progressPercent: TextView = itemView.findViewById(R.id.taskProgressPercent)
        val taskStatus: TextView = itemView.findViewById(R.id.taskStatus)
        val deadline: TextView = itemView.findViewById(R.id.taskDeadline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_consultant_task_card, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val context = holder.itemView.context
        val priority = task.priority ?: ServicePriority.LOW
        val status = task.status.orEmpty()

        //Service Type & Document Name
        holder.serviceType.text = task.serviceType?.toPretty() ?: "Other Service"
        holder.documentName.text = task.customName?.takeIf { it.isNotBlank() } ?: "Document"

        //Urgency Tag (Text & Background Color)
        holder.urgencyTag.text = priority.toPrettyTag()
        val tagColorResId = when (priority) {
            ServicePriority.HIGH -> R.color.priority_High
            ServicePriority.MEDIUM -> R.color.priority_Medium
            ServicePriority.LOW -> R.color.priority_Low
        }

        // Apply tinting to the priority_tag_background drawable
        val drawable = ContextCompat.getDrawable(context, R.drawable.priority_tag_background)
        holder.urgencyTag.background = drawable?.apply {
            setTint(ContextCompat.getColor(context, tagColorResId))
        }

        //Progress and Status
        val progressVal = statusToProgress(status)
        holder.progressBar.progress = progressVal
        holder.progressPercent.text = "$progressVal%"
        holder.taskStatus.text = status.toPrettyStatus()

        //Deadline
        holder.deadline.text = "Deadline: ${task.deadline.toUiDate()}"

        //Click Listener
        holder.itemView.setOnClickListener { onClick(task) }
    }

    /**
     * Updates the list data and refreshes the RecyclerView.
     */
    fun updateList(newList: List<ServiceRequestDto>) {
        this.tasks = newList
        notifyDataSetChanged()
    }
}