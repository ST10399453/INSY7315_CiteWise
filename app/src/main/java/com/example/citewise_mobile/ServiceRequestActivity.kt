package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import com.google.android.material.bottomnavigation.BottomNavigationView

class ServiceRequestActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        layoutInflater.inflate(R.layout.activity_service_request, baseContent, true)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_role_action)

        // View all requests
        findViewById<ViewGroup>(R.id.layoutViewAllRequests).setOnClickListener {
            startActivity(
                Intent(this, ServiceReviewsActivity::class.java)
                    .putExtra(ServiceReviewsActivity.EXTRA_MODE, ServiceReviewsActivity.MODE_ALL)
            )
        }

        // Recent request progress (top 5 in last 5 days)
        findViewById<ViewGroup>(R.id.layoutRecentProgress).setOnClickListener {
            startActivity(
                Intent(this, ServiceReviewsActivity::class.java)
                    .putExtra(ServiceReviewsActivity.EXTRA_MODE, ServiceReviewsActivity.MODE_RECENT)
            )
        }

        // Request a service
        findViewById<ViewGroup>(R.id.layoutRequestService).setOnClickListener {
            startActivity(Intent(this, RequestServiceStepsActivity::class.java))
        }
    }
}
