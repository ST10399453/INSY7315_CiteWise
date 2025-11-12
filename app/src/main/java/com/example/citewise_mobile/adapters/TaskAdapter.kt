//package com.example.citewise_mobile.adapters
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ProgressBar
//import android.widget.TextView
//import androidx.core.content.ContextCompat
//import androidx.recyclerview.widget.RecyclerView
//import com.example.citewise_mobile.R
//import com.example.citewise_mobile.api.ServicePriority
//import com.example.citewise_mobile.api.ServiceRequestDto
//import com.example.citewise_mobile.api.toUiDate
//import com.example.citewise_mobile.statusToProgress
//import com.example.citewise_mobile.toPretty
//import com.example.citewise_mobile.toPrettyStatus
//import com.example.citewise_mobile.toPrettyTag
//
//class TaskAdapter(
//    private var tasks: List<ServiceRequestDto>,
//    private val onClick: (ServiceRequestDto) -> Unit
//) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {
//
//    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val serviceType: TextView   = itemView.findViewById(R.id.taskServiceType)
//        val documentName: TextView  = itemView.findViewById(R.id.taskDocumentName)
//        val urgencyTag: TextView    = itemView.findViewById(R.id.taskUrgencyTag)
//        val progressBar: ProgressBar= itemView.findViewById(R.id.taskProgressBar)
//        val progressPercent: TextView= itemView.findViewById(R.id.taskProgressPercent)
//        val taskStatus: TextView    = itemView.findViewById(R.id.taskStatus)
//        val deadline: TextView      = itemView.findViewById(R.id.taskDeadline)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
//        val v = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_consultant_task_card, parent, false)
//        return TaskViewHolder(v)
//    }
//
//    override fun getItemCount(): Int = tasks.size
//
//    override fun onBindViewHolder(h: TaskViewHolder, position: Int) {
//        val task = tasks[position]
//        val ctx = h.itemView.context
//        val priority = task.priority ?: ServicePriority.LOW
//        val status = task.status.orEmpty()
//
//        h.serviceType.text  = task.serviceType?.toPretty() ?: "Other Service"
//        h.documentName.text = task.customName?.takeIf { it.isNotBlank() } ?: "Document"
//
//        // Priority chip
//        h.urgencyTag.text = priority.toPrettyTag()
//        val color = when (priority) {
//            ServicePriority.HIGH   -> R.color.priority_High
//            ServicePriority.MEDIUM -> R.color.priority_Medium
//            ServicePriority.LOW    -> R.color.priority_Low
//        }
//        ContextCompat.getDrawable(ctx, R.drawable.priority_tag_background)?.let { bg ->
//            bg.setTint(ContextCompat.getColor(ctx, color))
//            h.urgencyTag.background = bg
//        }
//
//        val progress = statusToProgress(status)
//        h.progressBar.progress = progress
//        h.progressPercent.text = "$progress%"
//        h.taskStatus.text = status.toPrettyStatus()
//
//        h.deadline.text = "Deadline: ${task.deadline.toUiDate()}"
//
//        h.itemView.setOnClickListener { onClick(task) }
//    }
//
//    fun updateList(newList: List<ServiceRequestDto>) {
//        tasks = newList
//        notifyDataSetChanged()
//    }
//}
