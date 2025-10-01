package com.example.citewise_mobile

import android.os.Bundle
import android.view.ViewGroup
import com.google.android.material.bottomnavigation.BottomNavigationView

class ServiceRequestActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        layoutInflater.inflate(R.layout.activity_service_request, baseContent, true)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_request)
    }
}
