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
        val roleString = sp.getString("user_role", "STUDENT")
        // Convert the stored string (e.g., "consultant") to UPPERCASE before parsing
        val roleUpper = (roleString ?: "STUDENT").uppercase(java.util.Locale.ROOT)
        return runCatching { UserRole.valueOf(roleUpper) }.getOrDefault(UserRole.STUDENT)
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

    //Additional destinations
    protected fun getRequestsActivityClass(): Class<*> = ServiceRequestActivity::class.java
    protected fun getResourcesActivityClass(): Class<*> = ResourcesActivity::class.java
    protected fun getManageConsultantsActivityClass(): Class<*> = ManageConsultantsActivity::class.java
    protected fun getResourceMgmtActivityClass(): Class<*> = ResourceManagementActivity::class.java
    protected fun getActiveTasksActivityClass(): Class<*> = ConsultantTasksActivity::class.java
    protected fun getMessagesActivityClass(): Class<*> = ChatsActivity::class.java

    /**
     * Handles bottom-nav behavior, visibility, and navigation logic based on the 5-item consolidated menu.
     */
    protected open fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.bringToFront()

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPx = (16 * resources.displayMetrics.density).toInt()
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + extraPx
            v.requestLayout()
            insets
        }

        bottomNav.setOnItemReselectedListener { /* no-op */ }

        val role = getCurrentUserRole()
        applyRoleVisibility(bottomNav, role)

        if (bottomNav.selectedItemId != selectedItemId) {
            bottomNav.selectedItemId = selectedItemId
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            when (item.itemId) {
                //Common targets
                R.id.nav_dashboard -> launchTop(getDashboardActivityClass())
                R.id.nav_messages  -> launchTop(getMessagesActivityClass())
                R.id.nav_profile   -> launchTop(getProfileActivityClass())

                //Consolidated action slot (Slot 2)
                R.id.nav_role_action -> when (role) {
                    UserRole.STUDENT    -> launchTop(getRequestsActivityClass())
                    UserRole.CONSULTANT -> launchTop(getActiveTasksActivityClass())
                    UserRole.ADMIN      -> launchTop(getManageConsultantsActivityClass())
                    else -> false
                }

                //Consolidated action slot (Slot 3)
                R.id.nav_role_secondary -> when (role) {
                    UserRole.STUDENT    -> launchTop(getResourcesActivityClass())
                    UserRole.ADMIN      -> launchTop(getResourceMgmtActivityClass())
                    else -> false
                }

                else -> false
            }
        }
    }

    /** Shows/hides menu items based on role, using the 5 consolidated IDs. */
    private fun applyRoleVisibility(bottomNav: BottomNavigationView, role: UserRole) {
        val m = bottomNav.menu

        //Hide ALL items first
        for (i in 0 until m.size()) m.getItem(i).isVisible = false

        when (role) {
            UserRole.STUDENT -> {
                m.findItem(R.id.nav_dashboard)?.isVisible = true
                m.findItem(R.id.nav_role_action)?.isVisible = true
                m.findItem(R.id.nav_role_secondary)?.isVisible = true
                m.findItem(R.id.nav_messages)?.isVisible = true
                m.findItem(R.id.nav_profile)?.isVisible = true
            }
            UserRole.CONSULTANT -> {
                m.findItem(R.id.nav_dashboard)?.isVisible = true
                m.findItem(R.id.nav_role_action)?.isVisible = true
                // Secondary slot is hidden for consultant: m.findItem(R.id.nav_role_secondary)?.isVisible = false
                m.findItem(R.id.nav_messages)?.isVisible = true
                m.findItem(R.id.nav_profile)?.isVisible = true
            }
            UserRole.ADMIN -> {
                m.findItem(R.id.nav_dashboard)?.isVisible = true
                m.findItem(R.id.nav_role_action)?.isVisible = true
                m.findItem(R.id.nav_role_secondary)?.isVisible = true
                m.findItem(R.id.nav_messages)?.isVisible = true
                m.findItem(R.id.nav_profile)?.isVisible = true
            }
        }
    }

    /** Starts a target Activity and finishes the current one. */
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
