package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.ManageConsultantsActivity.Consultant
import com.example.citewise_mobile.R

class PendingConsultantAdapter(
    private val data: MutableList<Consultant>,
    private val onApprove: (Consultant) -> Unit,
    private val onReject: (Consultant) -> Unit
) : RecyclerView.Adapter<PendingConsultantAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvInitial: TextView = v.findViewById(R.id.tvInitial)
        val tvUsername: TextView = v.findViewById(R.id.tvUsername)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val btnAccept: View = v.findViewById(R.id.btnAccept)
        val btnReject: View = v.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_consultant, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = data[position]
        val initial = (c.firstName.trim().firstOrNull() ?: c.email.trim().firstOrNull() ?: '?')
            .uppercaseChar().toString()
        h.tvInitial.text = initial
        h.tvUsername.text = c.email
        h.tvName.text = if (c.firstName.isBlank()) "(no name)" else c.firstName

        h.btnAccept.setOnClickListener { onApprove(c) }
        h.btnReject.setOnClickListener { onReject(c) }
    }

    override fun getItemCount(): Int = data.size
}