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

    //Reference: Based on code from Android Knowledge (2024),
    //"Bottom Navigation Bar in Android Studio using Java | Explanation"
    //https://www.youtube.com/watch?v=0x5kmLY16qE

    enum class UserRole {
        STUDENT, CONSULTANT, ADMIN
    }

    private fun getCurrentUserRole(): UserRole {
        val sharedPreferences: SharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val roleString = sharedPreferences.getString("user_role", "STUDENT")
        return try {
            UserRole.valueOf(roleString ?: "STUDENT")
        } catch (e: IllegalArgumentException) {
            UserRole.STUDENT // Default to student if role is invalid
        }
    }

    private fun getDashboardActivityClass(): Class<*> {
        return when (getCurrentUserRole()) {
            UserRole.STUDENT -> StudentDashboardActivity::class.java
            UserRole.CONSULTANT -> ConsultantDashboardActivity::class.java
            UserRole.ADMIN -> AdminDashboardActivity::class.java
        }
    }

    private fun getProfileActivityClass(): Class<*> {
        return when (getCurrentUserRole()) {
            UserRole.STUDENT -> StudentProfileSettingsActivity::class.java
            UserRole.CONSULTANT -> ConsultantProfileSettingsActivity::class.java
            UserRole.ADMIN -> AdminProfileSettingsActivity::class.java
        }
    }

    protected fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val dashboardClass = getDashboardActivityClass()
                    if (!dashboardClass.isInstance(this)) {
                        startActivity(Intent(this, dashboardClass))
                        overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_help -> {
                    if (this !is ServiceRequestActivity) {
                        startActivity(Intent(this, ServiceRequestActivity::class.java))
                        overridePendingTransition(0, 0)
                    }
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
                    val profileClass = getProfileActivityClass()
                    if (!profileClass.isInstance(this)) {
                        startActivity(Intent(this, profileClass))
                        overridePendingTransition(0, 0)
                    }
                    true
                }
                else -> false
            }
        }
    }

    protected fun setSelectedNavItem(itemId: Int) {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation?.selectedItemId = itemId
    }

    override fun onResume() {
        super.onResume()

        when (this) {
            is StudentDashboardActivity, is ConsultantDashboardActivity, is AdminDashboardActivity ->
                setSelectedNavItem(R.id.nav_home)
            is ServiceRequestActivity -> setSelectedNavItem(R.id.nav_help)
            is ChatsActivity -> setSelectedNavItem(R.id.nav_messages)
            is StudentProfileSettingsActivity, is ConsultantProfileSettingsActivity, is AdminProfileSettingsActivity ->
                setSelectedNavItem(R.id.nav_profile)
        }
    }
}
