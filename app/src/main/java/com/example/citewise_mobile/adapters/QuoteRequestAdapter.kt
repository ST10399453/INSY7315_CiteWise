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

class QuoteRequestAdapter(
    private var requests: List<ServiceRequestDto>,
    private val onClick: (ServiceRequestDto) -> Unit
) : RecyclerView.Adapter<QuoteRequestAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val serviceType: TextView  = v.findViewById(R.id.quoteServiceType)
        val documentName: TextView = v.findViewById(R.id.quoteDocumentName)
        val submissionDate: TextView= v.findViewById(R.id.quoteSubmissionDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_quote_request, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = requests.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = requests[position]
        h.serviceType.text   = r.serviceType?.toPretty() ?: "Quote Request"
        h.documentName.text  = r.customName?.takeIf { it.isNotBlank() } ?: "Document"
        h.submissionDate.text= r.createdAt.toUiDate()
        h.itemView.setOnClickListener { onClick(r) }
    }

    fun updateList(newList: List<ServiceRequestDto>) {
        requests = newList
        notifyDataSetChanged()
    }
}
