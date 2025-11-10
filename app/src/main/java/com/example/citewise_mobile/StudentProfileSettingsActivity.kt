package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth

class StudentProfileSettingsActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    // Toggle & sections
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnOverview: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var sectionOverview: View
    private lateinit var sectionSettings: View

    // Settings rows (containers)
    private lateinit var rowEditProfile: View
    private lateinit var rowChangePassword: View
    private lateinit var rowLanguage: View
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shell
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate page
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val page = layoutInflater.inflate(
            R.layout.activity_student_profile_settings,
            baseContent,
            false
        )
        baseContent.addView(page)

        // Bottom nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_profile)

        // Views
        toggle          = page.findViewById(R.id.toggleSettings)
        btnOverview     = page.findViewById(R.id.btnOverview)
        btnSettings     = page.findViewById(R.id.btnProfileSettings)
        sectionOverview = page.findViewById(R.id.sectionOverview)
        sectionSettings = page.findViewById(R.id.sectionSettings)

        // Default = Overview tab (visually + content)
        toggle.post { toggle.check(btnOverview.id) }
        showTab("overview")

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnOverview        -> showTab("overview")
                R.id.btnProfileSettings -> showTab("settings")
            }
        }

        bindOverview(page)
        initSettingsSection(page)
    }

    private fun showTab(which: String) {
        val isOverview = which == "overview"
        sectionOverview.isVisible = isOverview
        sectionSettings.isVisible = !isOverview
    }

    private fun bindOverview(page: View) {
        val ivAvatar       = page.findViewById<ImageView>(R.id.ivAvatar)
        val tvName         = page.findViewById<TextView>(R.id.tvOverviewName)
        val tvEmail        = page.findViewById<TextView>(R.id.tvOverviewEmail)
        val tvReq          = page.findViewById<TextView>(R.id.tvStatRequests)
        val tvCompleted    = page.findViewById<TextView>(R.id.tvStatCompleted)
        val tvInProgress   = page.findViewById<TextView>(R.id.tvStatInProgress)
        val tvPending      = page.findViewById<TextView>(R.id.tvStatPending)

        val user = auth.currentUser
        val displayName = user?.displayName?.takeIf { !it.isNullOrBlank() } ?: "Student"
        val email = user?.email ?: "you@example.com"

        tvName.text = displayName
        tvEmail.text = email

        tvReq.text = "0"
        tvCompleted.text = "0"
        tvInProgress.text = "0"
        tvPending.text = "0"

        val rowAcademic     = page.findViewById<View>(R.id.rowOverviewAcademic)
        val rowInstitution  = page.findViewById<View>(R.id.rowOverviewInstitution)
        val rowRole         = page.findViewById<View>(R.id.rowOverviewRole)
        val rowLanguage     = page.findViewById<View>(R.id.rowOverviewLanguage)

        rowAcademic.findViewById<TextView>(R.id.tvLabel).text = "Academic Level"
        rowAcademic.findViewById<TextView>(R.id.tvValue).text = "Honours"

        rowInstitution.findViewById<TextView>(R.id.tvLabel).text = "Institution"
        rowInstitution.findViewById<TextView>(R.id.tvValue).text = "IE"

        rowRole.findViewById<TextView>(R.id.tvLabel).text = "Role"
        rowRole.findViewById<TextView>(R.id.tvValue).text = "Student"

        rowLanguage.findViewById<TextView>(R.id.tvLabel).text = "Language"
        rowLanguage.findViewById<TextView>(R.id.tvValue).text = "English"

        rowAcademic.setOnClickListener { }
        rowInstitution.setOnClickListener { }
        rowRole.setOnClickListener { }
        rowLanguage.setOnClickListener { }
    }

    private fun initSettingsSection(page: View) {
        rowEditProfile    = page.findViewById(R.id.rowEditProfile)
        rowChangePassword = page.findViewById(R.id.rowChangePassword)
        rowLanguage       = page.findViewById(R.id.rowLanguage)
        btnLogout         = page.findViewById(R.id.btnLogout)

        rowEditProfile.findViewById<TextView>(R.id.rowLabel).text = "Edit profile"
        rowChangePassword.findViewById<TextView>(R.id.rowLabel).text = "Change password"

        val rowPush = page.findViewById<View>(R.id.rowPushNotifications).apply {
            findViewById<TextView>(R.id.rowLabel).text = "Push notifications"
        }
        rowPush.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = true
            setOnCheckedChangeListener { _, _ -> }
        }

        val rowApp = page.findViewById<View>(R.id.rowAppNotifications).apply {
            findViewById<TextView>(R.id.rowLabel).text = "App notifications"
        }
        rowApp.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = false
            setOnCheckedChangeListener { _, _ -> }
        }

        val rowEmail = page.findViewById<View>(R.id.rowEmailNotifications).apply {
            findViewById<TextView>(R.id.rowLabel).text = "Email notifications"
        }
        rowEmail.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = false
            setOnCheckedChangeListener { _, _ -> }
        }

        rowLanguage.findViewById<TextView>(R.id.tvLabel).text = "Language"
        rowLanguage.findViewById<TextView>(R.id.tvValue).text = "English"
        rowLanguage.setOnClickListener { }

        makeRowClickable(rowEditProfile)
        makeRowClickable(rowChangePassword)

        rowEditProfile.safeClick {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        rowChangePassword.safeClick {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun makeRowClickable(row: View) {
        if (!row.isClickable) row.isClickable = true
        if (!row.isFocusable) row.isFocusable = true
        if (row.foreground == null) {
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = obtainStyledAttributes(attrs)
            val ripple = ta.getDrawable(0)
            ta.recycle()
            row.foreground = ripple
        }
    }

    private fun View.safeClick(intervalMs: Long = 600L, onSafeClick: (View) -> Unit) {
        var lastClick = 0L
        setOnClickListener { v ->
            val now = System.currentTimeMillis()
            if (now - lastClick > intervalMs) {
                lastClick = now
                onSafeClick(v)
            }
        }
    }
}
