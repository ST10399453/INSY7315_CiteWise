package com.example.citewise_mobile
import android.content.Intent

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.example.citewise_mobile.AdminDashboardActivity
import com.example.citewise_mobile.AdminProfileSettingsActivity
import com.example.citewise_mobile.ChatsActivity
import com.example.citewise_mobile.ConsultantDashboardActivity
import com.example.citewise_mobile.ConsultantProfileSettingsActivity
import com.example.citewise_mobile.R
import com.example.citewise_mobile.ServiceRequestActivity
import com.example.citewise_mobile.StudentDashboardActivity
import com.example.citewise_mobile.StudentProfileSettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

open class BaseActivity : AppCompatActivity() {

    enum class UserRole { STUDENT, CONSULTANT, ADMIN }

    private fun getCurrentUserRole(): UserRole {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val roleString = sp.getString("user_role", "STUDENT")
        return runCatching { UserRole.valueOf(roleString ?: "STUDENT") }.getOrDefault(UserRole.STUDENT)
    }

    private fun getDashboardActivityClass(): Class<*> = when (getCurrentUserRole()) {
        UserRole.STUDENT -> StudentDashboardActivity::class.java
        UserRole.CONSULTANT -> ConsultantDashboardActivity::class.java
        UserRole.ADMIN -> AdminDashboardActivity::class.java
    }

    private fun getProfileActivityClass(): Class<*> = when (getCurrentUserRole()) {
        UserRole.STUDENT -> StudentProfileSettingsActivity::class.java
        UserRole.CONSULTANT -> ConsultantProfileSettingsActivity::class.java
        UserRole.ADMIN -> AdminProfileSettingsActivity::class.java
    }

    protected fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNav) // <- ID fixed

        bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    val clazz = getDashboardActivityClass()
                    if (this::class.java != clazz) {
                        startActivity(Intent(this, clazz))
                        overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_request -> {
                    if (this !is ServiceRequestActivity) {
                        startActivity(Intent(this, ServiceRequestActivity::class.java))
                        overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_resources -> {
                    // TODO: replace with your actual Resources activity
                    // startActivity(Intent(this, ResourcesActivity::class.java))
                    // For now, route to dashboard or keep as no-op:
                    true
                }
                R.id.nav_messages -> {
                    if (this !is ChatsActivity) {
                        startActivity(Intent(this, ChatsActivity::class.java))
                        overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_profile -> {
                    val clazz = getProfileActivityClass()
                    if (this::class.java != clazz) {
                        startActivity(Intent(this, clazz))
                        overridePendingTransition(0, 0)
                    }
                    true
                }
                else -> false
            }
        }
    }

    protected fun setSelectedNavItem(itemId: Int) {
        findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = itemId
    }

    override fun onResume() {
        super.onResume()
        when (this) {
            is StudentDashboardActivity, is ConsultantDashboardActivity, is AdminDashboardActivity ->
                setSelectedNavItem(R.id.nav_dashboard)
            is ServiceRequestActivity ->
                setSelectedNavItem(R.id.nav_request)
            is ChatsActivity ->
                setSelectedNavItem(R.id.nav_messages)
            is StudentProfileSettingsActivity, is ConsultantProfileSettingsActivity, is AdminProfileSettingsActivity ->
                setSelectedNavItem(R.id.nav_profile)

            // If/when you add ResourcesActivity:
            // is ResourcesActivity -> setSelectedNavItem(R.id.nav_resources)
        }
    }
}
