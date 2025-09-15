package com.example.citewise_mobile

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

open class BaseActivity : AppCompatActivity() {

    enum class UserRole { STUDENT, CONSULTANT, ADMIN }

    private fun getCurrentUserRole(): UserRole {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val roleString = sp.getString("user_role", "STUDENT")
        return runCatching { UserRole.valueOf(roleString ?: "STUDENT") }
            .getOrDefault(UserRole.STUDENT)
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
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNav) ?: return

        // Ignore reselect (don’t reload the same screen)
        bottomNavigation.setOnItemReselectedListener { /* no-op */ }

        bottomNavigation.setOnItemSelectedListener { item ->
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
                    // TODO: start your ResourcesActivity when it exists
                    // startActivity(Intent(this, ResourcesActivity::class.java))
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
