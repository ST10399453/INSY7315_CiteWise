package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.ManageConsultantsActivity.Assignment
import com.example.citewise_mobile.ManageConsultantsActivity.Consultant
import com.example.citewise_mobile.ManageConsultantsActivity.Priority
import com.example.citewise_mobile.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PriorityFilter { ALL, LOW, MEDIUM, HIGH }

private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
class AssignmentAdapter(
    private val base: MutableList<Assignment>,
    private val onClick: (Assignment) -> Unit,
    private var isAdmin: Boolean,
    private val onAssign: (Assignment, Consultant) -> Unit
) : RecyclerView.Adapter<AssignmentAdapter.VH>() {

    private val visible = mutableListOf<Assignment>()
    private var filter: PriorityFilter = PriorityFilter.ALL
    private val dateFmt = DATE_FMT
    private var assignableConsultants: List<Consultant> = emptyList()

    init { resetBase(base) }

    fun resetBase(newItems: List<Assignment>) {
        val snapshot = newItems.toList()
        base.clear()
        base.addAll(snapshot)
        applyFilter()
    }

    fun setPriorityFilter(f: PriorityFilter) {
        filter = f
        applyFilter()
    }

    fun setAssignableConsultants(list: List<Consultant>) {
        assignableConsultants = list
        notifyDataSetChanged()
    }

    fun setIsAdmin(value: Boolean) {
        isAdmin = value
        notifyDataSetChanged()
    }

    private fun applyFilter() {
        visible.clear()
        visible += when (filter) {
            PriorityFilter.ALL -> base
            PriorityFilter.LOW -> base.filter { it.priority == Priority.LOW }
            PriorityFilter.MEDIUM -> base.filter { it.priority == Priority.MEDIUM }
            PriorityFilter.HIGH -> base.filter { it.priority == Priority.HIGH }
        }
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v.findViewById(R.id.taskCard)
        val tvCategory: TextView = v.findViewById(R.id.tvCategory)
        val tvPriority: TextView = v.findViewById(R.id.tvPriority)
        val tvServiceTitle: TextView = v.findViewById(R.id.tvServiceTitle)
        val tvSubmittedDate: TextView = v.findViewById(R.id.tvSubmittedDate)
        val tvStatusLabel: TextView = v.findViewById(R.id.tvStatusLabel)
        val tvDeadline: TextView = v.findViewById(R.id.tvDeadline)
        val ddConsultants: MaterialAutoCompleteTextView? = v.findViewById(R.id.ddConsultants)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_service_review, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = visible[position]
        val context = h.itemView.context

        h.tvCategory.text = item.serviceType ?: "Other"
        h.tvServiceTitle.text = item.title()

        val statusLabel = item.status
            ?.replace('_', ' ')
            ?.lowercase()
            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            ?: "Pending"
        h.tvStatusLabel.text = statusLabel

        val statusColor = when (statusLabel.lowercase()) {
            "assigned" -> R.color.blue_400
            "completed", "done" -> R.color.green_500
            "pending", "submitted" -> R.color.priority_Medium
            else -> R.color.light_highlight
        }
        h.tvStatusLabel.setTextColor(context.getColor(statusColor))

        h.tvSubmittedDate.text = "Submitted: ${dateFmt.safe(item.createdAt)}"
        val deadlineText = dateFmt.safe(item.deadline)
        h.tvDeadline.visibility = if (deadlineText == "—") View.GONE else View.VISIBLE
        h.tvDeadline.text = "Deadline: $deadlineText"

        h.tvPriority.text = when (item.priority) {
            Priority.HIGH -> "High"
            Priority.MEDIUM -> "Medium"
            Priority.LOW -> "Low"
        }
        val priorityColor = when (item.priority) {
            Priority.HIGH -> R.color.priority_High
            Priority.MEDIUM -> R.color.priority_Medium
            Priority.LOW -> R.color.priority_Low
        }
        h.tvPriority.setTextColor(context.getColor(priorityColor))

        h.card.setOnClickListener { onClick(item) }

        val showDropdown = isAdmin && item.consultantId.isNullOrBlank() && h.ddConsultants != null
        h.ddConsultants?.visibility = if (showDropdown) View.VISIBLE else View.GONE

        if (showDropdown) {
            val dropdownAdapter = ConsultantDropdownAdapter(context, assignableConsultants)
            h.ddConsultants?.apply {
                setAdapter(dropdownAdapter)
                setDropDownBackgroundResource(R.drawable.bg_spinner_popup_white)

                // open dropdown when field tapped
                setOnClickListener { showDropDown() }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) (v as? MaterialAutoCompleteTextView)?.showDropDown()
                }

                // clear any old selection when reused
                setText("", false)
                tag = null

                setOnItemClickListener { _, _, idx, _ ->
                    val chosen = assignableConsultants.getOrNull(idx) ?: return@setOnItemClickListener
                    onAssign(item, chosen)
                    // Show selected consultant in the field
                    val label = buildString {
                        append(if (chosen.firstName.isBlank()) "Unknown" else chosen.firstName)
                        append(" · ")
                        append(chosen.email)
                    }
                    setText(label, false)
                    clearFocus() // collapse dropdown
                }
            }
        }
    }

    override fun getItemCount(): Int = visible.size

    private fun SimpleDateFormat.safe(ts: Long?): String =
        ts?.let { format(Date(it)) } ?: "—"

    private class ConsultantDropdownAdapter(
        context: android.content.Context,
        private val items: List<Consultant>
    ) : ArrayAdapter<Consultant>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Text shown in the field after selection
            return createItemView(position, convertView, parent)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Rows in the dropdown list
            return createItemView(position, convertView, parent)
        }

        private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_assign_consultant_option, parent, false)

            val consultant = items[position]

            val tvAvatar = v.findViewById<TextView>(R.id.tvAvatar)
            val tvName = v.findViewById<TextView>(R.id.tvName)
            val tvEmail = v.findViewById<TextView>(R.id.tvEmail)

            val initial = (consultant.firstName.trim().firstOrNull()
                ?: consultant.email.trim().firstOrNull()
                ?: '?').uppercaseChar()

            tvAvatar.text = initial.toString()
            tvName.text = if (consultant.firstName.isBlank()) "Unknown" else consultant.firstName
            tvEmail.text = consultant.email

            return v
        }
    }

}
