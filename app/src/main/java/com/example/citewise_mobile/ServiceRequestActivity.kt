package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import com.example.citewise_mobile.databinding.ActivityServiceRequestBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class ServiceRequestActivity : BaseActivity() {

    private lateinit var binding: ActivityServiceRequestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Base shell with bottom nav + container
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate the screen content into the base container via ViewBinding
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        binding = ActivityServiceRequestBinding.inflate(layoutInflater, baseContent, true)

        // Highlight the Requests tab
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_request)

        // Clicks
        binding.layoutViewAllRequests.setOnClickListener {
            startActivity(
                Intent(this, ServiceReviewsActivity::class.java)
                    .putExtra(ServiceReviewsActivity.EXTRA_MODE, ServiceReviewsActivity.MODE_ALL)
            )
        }

        binding.layoutRecentProgress.setOnClickListener {
            startActivity(
                Intent(this, ServiceReviewsActivity::class.java)
                    .putExtra(ServiceReviewsActivity.EXTRA_MODE, ServiceReviewsActivity.MODE_RECENT)
            )
        }

        binding.layoutRequestService.setOnClickListener {
            startActivity(Intent(this, RequestServiceStepsActivity::class.java))
        }
    }
}
