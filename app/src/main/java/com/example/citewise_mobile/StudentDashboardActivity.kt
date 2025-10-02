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
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class StudentDashboardActivity : BaseActivity() {

    private lateinit var btnRequestService: MaterialButton

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Base shell
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // 2) Inflate child INTO base container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val childRoot = layoutInflater.inflate(
            R.layout.activity_student_dashboard,
            baseContent,
            true
        )

        // 3) Now find views from the child layout
        btnRequestService = childRoot.findViewById(R.id.btnRequestService)
        val tvGreeting = childRoot.findViewById<TextView>(R.id.tvGreeting)
        val btnMenu    = childRoot.findViewById<ImageButton>(R.id.btnMenu)

        // 4) Wire bottom nav (lives in activity_base)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        // 5) Button -> Step 1 screen
        btnRequestService.setOnClickListener {
            startActivity(Intent(this, RequestServiceStepsActivity::class.java))
        }

        // 6) Greeting
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

        // 7) Overflow / sign out
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
