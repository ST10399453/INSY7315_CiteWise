package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView

open class BaseActivity : AppCompatActivity() {

    enum class UserRole { STUDENT, CONSULTANT, ADMIN }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    protected fun applyInsets(rootId: Int) {
        val root = findViewById<View>(rootId)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top)
            WindowInsetsCompat.CONSUMED
        }
    }

    // ---- Role helpers ----
    protected fun getCurrentUserRole(): UserRole {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        // Normalize to UPPERCASE before mapping to enum
        val roleString = sp.getString("user_role", "STUDENT")?.uppercase()
        return runCatching { UserRole.valueOf(roleString ?: "STUDENT") }
            .getOrDefault(UserRole.STUDENT)
    }

    protected fun getDashboardActivityClass(): Class<*> = when (getCurrentUserRole()) {
        UserRole.STUDENT    -> StudentDashboardActivity::class.java
        UserRole.CONSULTANT -> ConsultantDashboardActivity::class.java
        UserRole.ADMIN      -> AdminDashboardActivity::class.java
    }

    protected fun getProfileActivityClass(): Class<*> = when (getCurrentUserRole()) {
        UserRole.STUDENT    -> StudentProfileSettingsActivity::class.java
        UserRole.CONSULTANT -> ConsultantProfileSettingsActivity::class.java
        UserRole.ADMIN      -> AdminProfileSettingsActivity::class.java
    }

    protected fun getRequestsActivityClass(): Class<*> = ServiceRequestActivity::class.java
    protected fun getResourcesActivityClass(): Class<*> = ResourcesActivity::class.java
    protected fun getManageConsultantsActivityClass(): Class<*> = ManageConsultantsActivity::class.java
    protected fun getResourceMgmtActivityClass(): Class<*> = ResourcesActivity::class.java
    protected fun getActiveTasksActivityClass(): Class<*> = ActiveTasksActivity::class.java
    protected fun getMessagesActivityClass(): Class<*> = ChatsActivity::class.java

    /** Centralized bottom-nav setup */
    protected open fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.bringToFront()

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPx = (16 * resources.displayMetrics.density).toInt()
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + extraPx
            v.requestLayout()
            insets
        }

        // Re-inflate menu only if role changed (prevents flicker)
        val role = getCurrentUserRole()
        val lastRole = bottomNav.getTag(R.id.tag_last_role) as? UserRole
        if (lastRole != role) {
            bottomNav.menu.clear()
            when (role) {
                UserRole.STUDENT    -> bottomNav.inflateMenu(R.menu.bottom_nav_student)
                UserRole.ADMIN      -> bottomNav.inflateMenu(R.menu.bottom_nav_admin)
                UserRole.CONSULTANT -> bottomNav.inflateMenu(R.menu.bottom_nav_consultant)
            }
            bottomNav.setTag(R.id.tag_last_role, role)
        }

        bottomNav.setOnItemReselectedListener { /* no-op */ }

        if (bottomNav.selectedItemId != selectedItemId) {
            bottomNav.selectedItemId = selectedItemId
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            when (item.itemId) {
                // Common
                R.id.nav_dashboard -> launchTop(getDashboardActivityClass())
                R.id.nav_messages  -> launchTop(getMessagesActivityClass())
                R.id.nav_profile   -> launchTop(getProfileActivityClass())

                // Student items
                R.id.nav_request   -> launchTop(getRequestsActivityClass())
                R.id.nav_resources -> launchTop(getResourcesActivityClass())

                // Admin items
                R.id.nav_manage_consultants -> launchTop(getManageConsultantsActivityClass())
                R.id.nav_resource_mgmt      -> launchTop(getResourceMgmtActivityClass())

                // Consultant items
                R.id.nav_active_tasks -> launchTop(getActiveTasksActivityClass())
                else -> false
            }
        }
    }

    protected fun launchTop(target: Class<*>) : Boolean {
        val intent = Intent(this, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
        return true
    }
}
