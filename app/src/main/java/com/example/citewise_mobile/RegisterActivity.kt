package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database

class RegisterActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.database

    // UI
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnStudent: MaterialButton
    private lateinit var btnConsultant: MaterialButton
    private lateinit var sectionStudent: View
    private lateinit var sectionConsultant: View

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etFirst: TextInputEditText
    private lateinit var etSur: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var pbStrength: android.widget.ProgressBar
    private lateinit var actvInstitution: AutoCompleteTextView
    private lateinit var etField: TextInputEditText
    private lateinit var etCompany: TextInputEditText
    private lateinit var actvLanguage: AutoCompleteTextView

    private lateinit var cbTerms: MaterialCheckBox
    private lateinit var btnSign: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tvGoLogin: MaterialTextView

    // Mode
    private var isGoogleMode: Boolean = false

    // OPTIONAL convenience for local seeding (not secure; use server-side claims for production)
    private val adminSeedEmails = setOf(
        "admin@citewise.com",
        "you@example.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Read mode from intent ("google" to complete profile for Google users)
        isGoogleMode = intent.getStringExtra("mode")?.equals("google", ignoreCase = true) == true

        // Bind
        toggle = findViewById(R.id.toggleAccountType)
        btnStudent = findViewById(R.id.btnStudent)
        btnConsultant = findViewById(R.id.btnConsultant)
        sectionStudent = findViewById(R.id.sectionStudent)
        sectionConsultant = findViewById(R.id.sectionConsultant)

        etFirst = findViewById(R.id.etFirstName)
        etSur = findViewById(R.id.etSurname)
        etEmail = findViewById(R.id.etEmail)
        etPass = findViewById(R.id.etPassword)
        tilEmail = findViewById(R.id.tilEmail)            // make sure these IDs exist in your layout
        tilPassword = findViewById(R.id.tilPassword)
        pbStrength = findViewById(R.id.pbPasswordStrength)
        actvLanguage = findViewById(R.id.actvLanguage)

        actvInstitution = findViewById(R.id.actvInstitution)
        etField = findViewById(R.id.etFieldOfStudy)

        etCompany = findViewById(R.id.etCompany)

        cbTerms = findViewById(R.id.cbTerms)
        btnSign = findViewById(R.id.btnSignUp)
        progress = findViewById(R.id.progress)
        tvGoLogin = findViewById(R.id.tvGoLogin)

        // Dropdowns
        actvInstitution.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.institutions))
        )
        actvLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.languages))
        )

        // Default role = student
        toggle.check(btnStudent.id)
        showRole("student")

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                btnStudent.id -> showRole("student")
                btnConsultant.id -> showRole("consultant")
            }
            revalidate()
        }

        // Password strength (email mode only)
        etPass.addTextChangedListener { s ->
            if (!isGoogleMode) {
                val n = (s?.length ?: 0).coerceAtMost(12)
                pbStrength.progress = (n * 100 / 12)
            }
            revalidate()
        }

        // Revalidate on changes
        listOf(etFirst, etSur, etEmail, etField, etCompany).forEach {
            it.addTextChangedListener { revalidate() }
        }
        actvInstitution.addTextChangedListener { revalidate() }
        actvLanguage.addTextChangedListener { revalidate() }
        cbTerms.setOnCheckedChangeListener { _, _ -> revalidate() }

        // Mode-specific UI
        if (isGoogleMode) {
            // In Google mode, user should already be signed in with Google
            val gUser = auth.currentUser
            if (gUser == null) {
                // Safety: if somehow not signed in, send them back to login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            // Prefill from Google profile
            etEmail.setText(gUser.email ?: "")
            etFirst.setText((gUser.displayName ?: "").substringBeforeLast(" "))
            etSur.setText((gUser.displayName ?: "").substringAfterLast(" ").takeIf { it != gUser.displayName } ?: "")

            // Disable email & hide password in Google mode
            tilEmail.isEnabled = false
            etEmail.isEnabled = false
            tilPassword.isVisible = false
            pbStrength.isVisible = false
        } else {
            tilEmail.isEnabled = true
            etEmail.isEnabled = true
            tilPassword.isVisible = true
            pbStrength.isVisible = true
        }

        btnSign.setOnClickListener {
            if (isGoogleMode) doRegisterGoogleMode() else doRegisterEmailMode()
        }

        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Initial validation state
        revalidate()
    }

    private fun roleFromToggle(): String = when (toggle.checkedButtonId) {
        R.id.btnStudent -> "student"
        R.id.btnConsultant -> "consultant"
        else -> "student"
    }

    private fun showRole(role: String) {
        sectionStudent.visibility = if (role == "student") View.VISIBLE else View.GONE
        sectionConsultant.visibility = if (role == "consultant") View.VISIBLE else View.GONE
    }

    private fun revalidate() {
        val commonOk = etFirst.text?.isNotBlank() == true &&
                etSur.text?.isNotBlank() == true &&
                validEmail(etEmail.text?.toString()) &&
                actvLanguage.text?.isNotBlank() == true &&
                cbTerms.isChecked

        val passwordOk = if (isGoogleMode) true else (etPass.text?.length ?: 0) >= 6

        val roleOk = when (roleFromToggle()) {
            "student" -> actvInstitution.text?.isNotBlank() == true && etField.text?.isNotBlank() == true
            "consultant" -> etCompany.text?.isNotBlank() == true
            else -> false
        }

        btnSign.isEnabled = commonOk && passwordOk && roleOk
    }

    private fun validEmail(v: String?): Boolean =
        !v.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(v).matches()

    // -------- Email/password mode: creates auth user then writes profile --------
    private fun doRegisterEmailMode() {
        if (!btnSign.isEnabled) { revalidate(); return }

        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPass.text?.toString().orEmpty()
        val lang = actvLanguage.text?.toString()?.trim().orEmpty()
        val chosenRole = roleFromToggle()

        progress.visibility = View.VISIBLE
        btnSign.isEnabled = false

        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (!task.isSuccessful) {
                progress.visibility = View.GONE
                btnSign.isEnabled = true
                return@addOnCompleteListener
            }

            val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
            val finalRole = if (adminSeedEmails.contains(email.lowercase())) "admin" else chosenRole

            val profile = mutableMapOf(
                "uid" to uid,
                "firstName" to first,
                "surname" to sur,
                "email" to email,
                "preferredLanguage" to lang,
                "role" to finalRole,
                "createdAt" to System.currentTimeMillis()
            )

            when (finalRole) {
                "student" -> {
                    profile["institution"] = actvInstitution.text?.toString()?.trim().orEmpty()
                    profile["fieldOfStudy"] = etField.text?.toString()?.trim().orEmpty()
                }
                "consultant" -> {
                    profile["company"] = etCompany.text?.toString()?.trim().orEmpty()
                }
                "admin" -> { /* nothing extra */ }
            }

            db.reference.child("users").child(uid).setValue(profile).addOnCompleteListener {
                progress.visibility = View.GONE
                btnSign.isEnabled = true
                if (it.isSuccessful) routeByRole(finalRole)
            }
        }
    }

    // -------- Google mode: user already signed in; only writes profile --------
    private fun doRegisterGoogleMode() {
        if (!btnSign.isEnabled) { revalidate(); return }

        val gUser = auth.currentUser
        if (gUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = gUser.uid
        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = (gUser.email ?: etEmail.text?.toString() ?: "").trim()
        val lang = actvLanguage.text?.toString()?.trim().orEmpty()
        val chosenRole = roleFromToggle()
        val finalRole = if (adminSeedEmails.contains(email.lowercase())) "admin" else chosenRole

        progress.visibility = View.VISIBLE
        btnSign.isEnabled = false

        // If node exists, just update; else create
        val ref = db.reference.child("users").child(uid)
        ref.get().addOnSuccessListener { snap ->
            val profile = mutableMapOf(
                "uid" to uid,
                "firstName" to first,
                "surname" to sur,
                "email" to email,
                "preferredLanguage" to lang,
                "role" to finalRole,
                "updatedAt" to System.currentTimeMillis()
            )
            if (!snap.exists()) {
                profile["createdAt"] = System.currentTimeMillis()
            }

            when (finalRole) {
                "student" -> {
                    profile["institution"] = actvInstitution.text?.toString()?.trim().orEmpty()
                    profile["fieldOfStudy"] = etField.text?.toString()?.trim().orEmpty()
                }
                "consultant" -> {
                    profile["company"] = etCompany.text?.toString()?.trim().orEmpty()
                }
                "admin" -> { /* nothing extra */ }
            }

            ref.updateChildren(profile as Map<String, Any>).addOnCompleteListener { done ->
                progress.visibility = View.GONE
                btnSign.isEnabled = true
                if (done.isSuccessful) routeByRole(finalRole)
            }
        }.addOnFailureListener {
            progress.visibility = View.GONE
            btnSign.isEnabled = true
        }
    }

    private fun routeByRole(role: String) {
        when (role.lowercase()) {
            "student" -> startActivity(Intent(this, StudentDashboardActivity::class.java))
            "consultant" -> startActivity(Intent(this, ConsultantDashboardActivity::class.java))
            "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
            else -> startActivity(Intent(this, StudentDashboardActivity::class.java))
        }
        finish()
    }
}
