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
) : RecyclerView.Adapter<QuoteRequestAdapter.QuoteRequestViewHolder>() {

    class QuoteRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Match IDs from item_quote_request.xml
        val serviceType: TextView = itemView.findViewById(R.id.quoteServiceType)
        val documentName: TextView = itemView.findViewById(R.id.quoteDocumentName)
        val submissionDate: TextView = itemView.findViewById(R.id.quoteSubmissionDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteRequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quote_request, parent, false)
        return QuoteRequestViewHolder(view)
    }

    override fun getItemCount(): Int = requests.size

    override fun onBindViewHolder(holder: QuoteRequestViewHolder, position: Int) {
        val request = requests[position]

        //Service Type
        holder.serviceType.text = request.serviceType?.toPretty() ?: "Quote Request"

        //Document Name
        holder.documentName.text = request.customName?.takeIf { it.isNotBlank() } ?: "Document"

        //Submission/Creation Date
        // We use the creation date here, as it signifies when the student submitted it for quote.
        holder.submissionDate.text = request.createdAt.toUiDate()

        //Click Listener (Leads to Task Details or Quote Confirmation Screen)
        holder.itemView.setOnClickListener { onClick(request) }
    }

    fun updateList(newList: List<ServiceRequestDto>) {
        this.requests = newList
        notifyDataSetChanged()
    }
}