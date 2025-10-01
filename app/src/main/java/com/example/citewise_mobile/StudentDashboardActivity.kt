package com.example.citewise_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class StudentDashboardActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId") // we inflate the child layout manually below
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Base shell with bottom nav + content container
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // 2) Inflate the dashboard layout *into* the base content container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val childRoot = layoutInflater.inflate(
            R.layout.activity_student_dashboard,
            baseContent,
            /* attachToRoot = */ true
        )

        // 3) Bottom nav wiring (this view lives in activity_base)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        // 4) Views from the *child* layout — look them up from childRoot
        val tvGreeting = childRoot.findViewById<TextView>(R.id.tvGreeting)
        val btnMenu    = childRoot.findViewById<ImageButton>(R.id.btnMenu)

        // --- Greeting: "Hi <firstName>" ---
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("firstName")
                .get()
                .addOnSuccessListener { snap ->
                    val first = snap.getValue(String::class.java)?.trim().orEmpty()
                    tvGreeting.text = if (first.isNotEmpty()) "Hi $first" else "Hi"
                }
                .addOnFailureListener { tvGreeting.text = "Hi" }
        } else {
            tvGreeting.text = "Hi"
        }

        // --- Overflow / sign-out menu ---
        btnMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            MenuInflater(this).inflate(R.menu.menu_dashboard_overflow, popup.menu)
            try {
                val f = PopupMenu::class.java.getDeclaredField("mPopup")
                f.isAccessible = true
                val helper = f.get(popup)
                helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(helper, true)
            } catch (_: Throwable) {}

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sign_out -> {
                        FirebaseAuth.getInstance().signOut()
                        getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
                        startActivity(
                            Intent(this, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
