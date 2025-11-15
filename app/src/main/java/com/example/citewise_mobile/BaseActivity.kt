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

    // ---------------- Lifecycle ----------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    // ---------------- Insets helpers ----------------
    protected fun applyInsets(rootId: Int) {
        val root = findViewById<View>(rootId)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top)
            WindowInsetsCompat.CONSUMED
        }
    }

    protected fun applyBottomNavInsets(bottomNav: BottomNavigationView) {
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPx = (16 * resources.displayMetrics.density).toInt()
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + extraPx
            v.requestLayout()
            insets
        }
    }

    // ---------------- Role helpers ----------------
    protected fun getCurrentUserRole(): UserRole {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val roleString = sp.getString("user_role", "STUDENT")
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

    // Additional destinations used by the nav
    protected fun getRequestsActivityClass(): Class<*> = ServiceRequestActivity::class.java
    protected fun getResourcesActivityClass(): Class<*> = ResourcesActivity::class.java
    protected fun getManageConsultantsActivityClass(): Class<*> = ManageConsultantsActivity::class.java
    protected fun getResourceMgmtActivityClass(): Class<*> = ResourcesActivity::class.java
    protected fun getActiveTasksActivityClass(): Class<*> = ConsultantTasksActivity::class.java
    protected fun getMessagesActivityClass(): Class<*> = ChatsActivity::class.java

    // ---------------- Bottom nav (3 separate menus) ----------------
    /**
     * Call from each Activity after setContentView:
     * setupBottomNav(bottomNav, selectedItemIdForThisScreen)
     *
     * IMPORTANT: Pass the selectedItemId that exists in the *role-specific* menu.
     *  - Admin menu IDs:      nav_dashboard, nav_manage_consultants, nav_resource_mgmt, nav_messages, nav_profile
     *  - Consultant menu IDs: nav_dashboard, nav_active_tasks,      (no resources),     nav_messages, nav_profile
     *  - Student menu IDs:    nav_dashboard, nav_request,           nav_resources,      nav_messages, nav_profile
     */
    protected open fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.bringToFront()
        applyBottomNavInsets(bottomNav)

        // Ensure clean slate
        bottomNav.menu.clear()

        val role = getCurrentUserRole()

        // 1) Inflate the correct menu for the role
        when (role) {
            UserRole.ADMIN -> bottomNav.inflateMenu(R.menu.bottom_nav_admin)
            UserRole.CONSULTANT -> bottomNav.inflateMenu(R.menu.bottom_nav_consultant)
            UserRole.STUDENT -> bottomNav.inflateMenu(R.menu.bottom_nav_student)
        }

        // 2) Set currently-selected item if present in this role's menu
        val existing = bottomNav.menu.findItem(selectedItemId)
        if (existing != null && bottomNav.selectedItemId != selectedItemId) {
            bottomNav.selectedItemId = selectedItemId
        }

        // 3) No-op on reselection
        bottomNav.setOnItemReselectedListener { /* no-op */ }

        // 4) Navigation by role-specific item IDs
        bottomNav.setOnItemSelectedListener { item ->
            // If user taps the already-selected tab, stay put
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            when (role) {
                UserRole.ADMIN -> handleAdminSelection(item.itemId)
                UserRole.CONSULTANT -> handleConsultantSelection(item.itemId)
                UserRole.STUDENT -> handleStudentSelection(item.itemId)
            }
        }
    }

    // ---- Role-specific item handling ----
    private fun handleAdminSelection(itemId: Int): Boolean = when (itemId) {
        R.id.nav_dashboard          -> launchTop(getDashboardActivityClass())
        R.id.nav_manage_consultants -> launchTop(getManageConsultantsActivityClass())
        R.id.nav_resource_mgmt      -> launchTop(getResourceMgmtActivityClass())
        R.id.nav_messages           -> launchTop(getMessagesActivityClass())
        R.id.nav_profile            -> launchTop(getProfileActivityClass())
        else -> false
    }

    private fun handleConsultantSelection(itemId: Int): Boolean = when (itemId) {
        R.id.nav_dashboard    -> launchTop(getDashboardActivityClass())
        R.id.nav_active_tasks -> launchTop(getActiveTasksActivityClass())
        R.id.nav_messages     -> launchTop(getMessagesActivityClass())
        R.id.nav_profile      -> launchTop(getProfileActivityClass())
        else -> false
    }

    private fun handleStudentSelection(itemId: Int): Boolean = when (itemId) {
        R.id.nav_dashboard -> launchTop(getDashboardActivityClass())
//        R.id.nav_request   -> launchTop(getRequestsActivityClass())
       // R.id.nav_request -> launchTop(RequestServiceStepsActivity::class.java)
        R.id.nav_request -> launchTop(ServiceRequestActivity::class.java)
        R.id.nav_resources -> launchTop(getResourcesActivityClass())
        R.id.nav_messages  -> launchTop(getMessagesActivityClass())
        R.id.nav_profile   -> launchTop(getProfileActivityClass())
        else -> false
    }

    // ---------------- Navigation helper ----------------
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
