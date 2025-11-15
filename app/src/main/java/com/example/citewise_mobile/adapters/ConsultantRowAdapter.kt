package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.ManageConsultantsActivity.Consultant
import com.example.citewise_mobile.R

class ConsultantRowAdapter(
    private val all: MutableList<Consultant>,
    private val onClick: (Consultant) -> Unit
) : RecyclerView.Adapter<ConsultantRowAdapter.VH>() {

    private val visible = mutableListOf<Consultant>()
    var filter: String = ""
        set(value) {
            field = value
            apply()
        }

    init { reset() }

    fun reset() {
        visible.clear()
        visible.addAll(all)
        notifyDataSetChanged()
    }

    private fun apply() {
        val f = filter.trim()
        visible.clear()
        if (f.isEmpty()) {
            visible.addAll(all)
        } else {
            visible.addAll(all.filter {
                it.firstName.contains(f, true) ||
                        it.email.contains(f, true) ||
                        it.specialty.contains(f, true)
            })
        }
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvInitials: TextView = v.findViewById(R.id.tvAvatarInitials)
        val tvName: TextView = v.findViewById(R.id.tvConsultantName)
        val tvEmail: TextView = v.findViewById(R.id.tvConsultantEmail)
        val tvSpecialty: TextView = v.findViewById(R.id.tvSpecialty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_consultant_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = visible[position]
        h.tvInitials.text = c.firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        h.tvName.text = c.firstName
        h.tvEmail.text = c.email
        h.tvSpecialty.text = if (c.specialty.isBlank()) "General" else c.specialty
        h.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount(): Int = visible.size
}
