////package com.example.citewise_mobile.reviews
////
////import android.view.LayoutInflater
////import android.view.View
////import android.view.ViewGroup
////import android.widget.TextView
////import androidx.recyclerview.widget.RecyclerView
////import com.example.citewise_mobile.R
////import com.example.citewise_mobile.api.ServiceRequestDto
////
////class RateConsultantAdapter(
////    private val items: List<ServiceRequestDto>
////) : RecyclerView.Adapter<RateConsultantAdapter.ViewHolder>() {
////
////    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
////        val tvRequestTitle: TextView = view.findViewById(R.id.tvRequestTitle)
////        val tvConsultantName: TextView = view.findViewById(R.id.tvConsultantName)
////    }
////
////    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
////        val view = LayoutInflater.from(parent.context)
////            .inflate(R.layout.item_rate_consultant, parent, false)
////        return ViewHolder(view)
////    }
////
////    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
////        val item = items[position]
////        holder.tvRequestTitle.text = item.customName ?: item.originalFileName ?: "Untitled"
////        holder.tvConsultantName.text = item.studentName ?: "Unknown Consultant"
////    }
////
////    override fun getItemCount(): Int = items.size
////}
//package com.example.citewise_mobile.adapters
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.RatingBar
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//import com.example.citewise_mobile.R
//import com.example.citewise_mobile.api.ServiceRequestDto
//import com.example.citewise_mobile.reviews.RateConsultantListActivity
//
//class RateConsultantAdapter(
//    private val items: List<ServiceRequestDto>,
//    private val onRatingChanged: (consultantId: String, rating: Float) -> Unit
//) : RecyclerView.Adapter<RateConsultantAdapter.ViewHolder>() {
//
//    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val tvRequestTitle: TextView = view.findViewById(R.id.tvRequestTitle)
//        val tvConsultantName: TextView = view.findViewById(R.id.tvConsultantName)
//        val ratingBar: RatingBar = view.findViewById(R.id.ratingBar)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_rate_consultant, parent, false)
//        return ViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        val item = items[position]
//        holder.tvRequestTitle.text = item.customName ?: item.originalFileName ?: "Untitled"
//        holder.tvConsultantName.text = item.studentName ?: "Unknown Consultant"
//
//        // Pre-fill existing rating if any
//        holder.ratingBar.rating = item.studentRating ?: 0f
//
//        holder.ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
//            if (fromUser) {
//                item.consultantId?.let { consultantId ->
//                    (context as RateConsultantListActivity).saveConsultantRating(consultantId, rating, position)
//                }
//            }
//        }
//    }
//
//    override fun getItemCount(): Int = items.size
//}
package com.example.citewise_mobile.reviews

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.api.ServiceRequestDto

class RateConsultantAdapter(
    private val items: List<ServiceRequestDto>,
    private val context: Context
) : RecyclerView.Adapter<RateConsultantAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRequestTitle: TextView = view.findViewById(R.id.tvRequestTitle)
        val tvConsultantName: TextView = view.findViewById(R.id.tvConsultantName)
        val ratingBar: RatingBar = view.findViewById(R.id.ratingBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rate_consultant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvRequestTitle.text = item.customName ?: item.originalFileName ?: "Untitled"
        holder.tvConsultantName.text = item.studentName ?: "Unknown Consultant"

        // Pre-fill rating if previously exists (optional)
        holder.ratingBar.rating = item.studentRating ?: 0f

        holder.ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) {
                val activity = context as RateConsultantListActivity
                item.consultantId?.let { consultantId ->
                    activity.saveConsultantRating(consultantId, rating, position)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
