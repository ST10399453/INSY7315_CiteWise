package com.example.citewise_mobile

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestsActivity : AppCompatActivity() {
    private lateinit var requestListContainer: LinearLayout
    private lateinit var tvNoRequests: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_requests)

        requestListContainer = findViewById(R.id.requestListContainer)
        tvNoRequests = findViewById(R.id.tvNoRequests)

        val activeRequests = getActiveRequestsFromBackend()

        if(activeRequests.isEmpty()){
            tvNoRequests.visibility = View.VISIBLE
        }
        else{
            tvNoRequests.visibility = View.GONE
            for(request in activeRequests){
                addRequestToUI(request)
            }
        }
    }

    private fun addRequestToUI(request: Request){
        val requestItemView = layoutInflater.inflate(R.layout.request_list_item, requestListContainer, false)

        val tvServiceName: TextView = requestItemView.findViewById(R.id.tvServiceName)
        val tvRequestTitle: TextView = requestItemView.findViewById(R.id.tvRequestTitle)
        val tvDateStamp: TextView = requestItemView.findViewById(R.id.tvDateStamp)
        val requestProgressBar: ProgressBar = requestItemView.findViewById(R.id.requestProgressBar)
        val ivCompletedTick: ImageView = requestItemView.findViewById(R.id.ivCompletedTick)
        val tvProgressStatus: TextView = requestItemView.findViewById(R.id.tvProgressStatus)

        // Populate content
        tvServiceName.text = request.serviceName
        tvRequestTitle.text = request.requestTitle

        tvDateStamp.text = request.dateSubmitted

        // Update progress
        updateProgressBar(requestProgressBar, ivCompletedTick, tvProgressStatus, request.status)

        // add new item
        requestListContainer.addView(requestItemView)
    }

    private fun updateProgressBar(progressBar: ProgressBar, tickIcon: ImageView, statusText: TextView, status: String){
        val progressMap = mapOf(
            "submitted" to 25,
            "in_progress" to 50,
            "feedback" to 75,
            "completed" to 100
        )

        val progress = progressMap[status] ?: 0
        progressBar.progress = progress

        statusText.text = when(status){
            "submitted" -> "Submitted"
            "in_progress" -> "In Progress"
            "feedback" -> "Feedback Ready"
            "completed" -> "Completed"
            else -> status.replaceFirstChar {if(it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()}
        }

        // Show tick when complete and hide progress bar
        if(status == "completed"){
            progressBar.visibility = View.GONE
            tickIcon.visibility = View.VISIBLE
        }
        else{
            progressBar.visibility = View.VISIBLE
            tickIcon.visibility = View.GONE
        }
    }

    // Data model
    data class Request(
        val serviceName: String,
        val requestTitle: String,
        val dateSubmitted: String,
        val status: String
    )

    private fun getActiveRequestsFromBackend(): List<Request>{
        val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

        // no requests
        // return emptyList()

        return listOf(
            Request("Proofreading & Editing", "Business plan draft", currentDate, "in_progress"),
            Request("Referencing", "Marketing strategy review", currentDate, "submitted"),
            Request("Proofreading & Editing", "Thesis abstract", currentDate, "completed")


        )
    }

}