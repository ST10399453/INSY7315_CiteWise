package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R

data class TopConsultant(
    val id: String,
    val name: String,
    val rating: Double
)
class TopConsultantAdapter(
    private val items: MutableList<TopConsultant>
) : RecyclerView.Adapter<TopConsultantAdapter.TopConsultantViewHolder>() {

    inner class TopConsultantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvInitials: TextView = itemView.findViewById(R.id.tvInitials)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        val tvRatingChip: TextView = itemView.findViewById(R.id.tvRatingChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopConsultantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_consultant, parent, false)
        return TopConsultantViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopConsultantViewHolder, position: Int) {
        val item = items[position]

        // Rank 1, 2, 3...
        holder.tvRank.text = (position + 1).toString()

        // Name
        holder.tvName.text = item.name

        // Subtitle by rank (Gold/Silver/Bronze)
        holder.tvSubtitle.text = when (position) {
            0 -> "Gold performer"
            1 -> "Silver performer"
            2 -> "Bronze performer"
            else -> "Top performing consultant"
        }

        // Initials from name
        val initials = item.name
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }

        holder.tvInitials.text = initials.ifBlank { "?" }

        // Rating chip
        holder.tvRatingChip.text = "${String.format("%.1f", item.rating)}"
    }

    override fun getItemCount(): Int = items.size

    fun setData(newItems: List<TopConsultant>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
