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

    /** Apply system bar insets to a root view (top only; bottom is handled by layout padding/nav). */
    protected fun applyInsets(rootId: Int) {
        val root = findViewById<View>(rootId)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top)
            WindowInsetsCompat.CONSUMED
        }
    }

    // ---- Role helpers (kept from your previous BaseActivity) ----
    protected fun getCurrentUserRole(): UserRole {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val roleString = sp.getString("user_role", "STUDENT")
        return runCatching { UserRole.valueOf(roleString ?: "STUDENT") }.getOrDefault(UserRole.STUDENT)
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

    /**
     * One place to handle bottom-nav behavior across screens.
     * Call from each Activity’s onCreate after findViewById(bottomNav).
     */
    protected open fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        // Make sure it’s on top of scroll content
        bottomNav.bringToFront()

        // Add gesture inset + a small gap
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPx = (16 * resources.displayMetrics.density).toInt()
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + extraPx
            v.requestLayout()
            insets
        }

        // No-op on reselect (prevents reloading current screen)
        bottomNav.setOnItemReselectedListener { /* no-op */ }

        // Highlight current tab
        if (bottomNav.selectedItemId != selectedItemId) {
            bottomNav.selectedItemId = selectedItemId
        }

        bottomNav.setOnItemSelectedListener { item ->
            android.util.Log.d("BottomNav", "onItemSelected id=${resources.getResourceEntryName(item.itemId)}")

            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_dashboard -> launchTop(getDashboardActivityClass())
                R.id.nav_request   -> launchTop(ServiceRequestActivity::class.java)
                R.id.nav_resources -> {
                    // TODO replace when you add ResourcesActivity
                    launchTop(ResourcesActivity::class.java)
                }
                R.id.nav_messages  -> launchTop(ChatsActivity::class.java)
                R.id.nav_profile   -> launchTop(getProfileActivityClass())
                else -> false
            }
        }
    }

    /** Starts (or brings to front) a target Activity and finishes the current one. */
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
