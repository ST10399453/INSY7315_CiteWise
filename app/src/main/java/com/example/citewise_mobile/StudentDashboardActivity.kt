package com.example.citewise_mobile

import BaseActivity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge

class StudentDashboardActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_base)

        layoutInflater.inflate(
            R.layout.activity_student_dashboard,
            findViewById(R.id.baseContent),
            true
        )

        setupBottomNavigation()
        }
}
