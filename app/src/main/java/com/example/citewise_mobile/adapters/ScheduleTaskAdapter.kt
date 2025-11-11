package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.toPretty
import com.example.citewise_mobile.toPrettyTag

class ScheduleTaskAdapter(
    private var tasks: List<ServiceRequestDto>,
    private val onClick: (ServiceRequestDto) -> Unit
) : RecyclerView.Adapter<ScheduleTaskAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val statusDot: View       = v.findViewById(R.id.statusDot)
        val serviceType: TextView = v.findViewById(R.id.scheduleServiceType)
        val documentName: TextView= v.findViewById(R.id.scheduleDocumentName)
        val urgencyTag: TextView  = v.findViewById(R.id.scheduleUrgencyTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_task, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val task = tasks[position]
        h.serviceType.text  = task.serviceType?.toPretty() ?: "Other Service"
        h.documentName.text = task.customName?.takeIf { it.isNotBlank() } ?: "Document"
        h.urgencyTag.text   = task.priority?.toPrettyTag() ?: "Low"
        h.itemView.setOnClickListener { onClick(task) }
    }

    fun updateList(newList: List<ServiceRequestDto>) {
        tasks = newList
        notifyDataSetChanged()
    }
}
