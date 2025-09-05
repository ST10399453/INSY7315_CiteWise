package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
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

    // Toggle / sections
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnStudent: MaterialButton
    private lateinit var btnConsultant: MaterialButton
    private lateinit var sectionStudent: View
    private lateinit var sectionConsultant: View

    // Inputs (common)
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etFirst: TextInputEditText
    private lateinit var etSur: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var etPassConfirm: TextInputEditText

    // Role-specific
    private lateinit var etFieldOfStudy: TextInputEditText       // student
    private lateinit var etSpecialisation: TextInputEditText     // consultant

    // Actions / progress
    private lateinit var btnSign: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tvGoLogin: MaterialTextView

    // Mode (if launched for Google profile completion)
    private var isGoogleMode: Boolean = false

    // Optional: seed admins by email (keep if you still want it)
    private val adminSeedEmails = setOf(
        "admin@citewise.com",
        "you@example.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        isGoogleMode = intent.getStringExtra("mode")?.equals("google", ignoreCase = true) == true

        // Bind views
        toggle = findViewById(R.id.toggleAccountType)
        btnStudent = findViewById(R.id.btnStudent)
        btnConsultant = findViewById(R.id.btnConsultant)
        sectionStudent = findViewById(R.id.sectionStudent)
        sectionConsultant = findViewById(R.id.sectionConsultant)

        etFirst = findViewById(R.id.etFirstName)
        etSur = findViewById(R.id.etSurname)
        etEmail = findViewById(R.id.etEmail)
        etPass = findViewById(R.id.etPassword)
        etPassConfirm = findViewById(R.id.etConfirmPassword)

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)

        etFieldOfStudy = findViewById(R.id.etFieldOfStudy)
        etSpecialisation = findViewById(R.id.etSpecialisation)

        btnSign = findViewById(R.id.btnSignUp)
        tvGoLogin = findViewById(R.id.tvGoLogin)

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

        // Revalidate on input changes
        listOf(etFirst, etSur, etEmail, etPass, etPassConfirm, etFieldOfStudy, etSpecialisation)
            .forEach { it.addTextChangedListener { revalidate() } }

        // Google mode: prefill + hide password fields
        if (isGoogleMode) {
            val gUser = auth.currentUser
            if (gUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            etEmail.setText(gUser.email ?: "")
            val name = (gUser.displayName ?: "")
            etFirst.setText(name.substringBeforeLast(" "))
            etSur.setText(name.substringAfterLast(" ").takeIf { it != name } ?: "")

            tilEmail.isEnabled = false
            etEmail.isEnabled = false
            tilPassword.isVisible = false
            tilConfirmPassword.isVisible = false
        } else {
            tilEmail.isEnabled = true
            etEmail.isEnabled = true
            tilPassword.isVisible = true
            tilConfirmPassword.isVisible = true
        }

        // Actions
        btnSign.setOnClickListener {
            if (isGoogleMode) doRegisterGoogleMode() else doRegisterEmailMode()
        }
        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Initial state
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
        // clear previous
        tilEmail.error = null
        tilPassword.error = null
        tilConfirmPassword.error = null

        val firstOk = etFirst.text?.isNotBlank() == true
        val surOk   = etSur.text?.isNotBlank() == true
        val emailOk = validEmail(etEmail.text?.toString())

        val p1 = etPass.text?.toString().orEmpty()
        val p2 = etPassConfirm.text?.toString().orEmpty()

        val pwOk = if (isGoogleMode) {
            true
        } else {
            // Don't show errors until the user starts typing
            if (p1.isEmpty() && p2.isEmpty()) {
                false
            } else {
                var ok = true
                if (p1.length < 6) {
                    tilPassword.error = "At least 6 characters"
                    ok = false
                }
                // Only show mismatch once confirm has something
                if (p2.isNotEmpty() && p1 != p2) {
                    tilConfirmPassword.error = "Passwords do not match"
                    ok = false
                }
                ok
            }
        }

        val roleOk = when (roleFromToggle()) {
            "student"    -> etFieldOfStudy.text?.isNotBlank() == true
            "consultant" -> etSpecialisation.text?.isNotBlank() == true
            else -> false
        }

        // Require both password fields filled (in email mode) before enabling
        val passwordsFilled = isGoogleMode || (p1.isNotEmpty() && p2.isNotEmpty())

        btnSign.isEnabled = firstOk && surOk && emailOk && passwordsFilled && pwOk && roleOk
    }

    private fun validEmail(v: String?): Boolean =
        !v.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(v).matches()

    // ---------------- Email/password flow ----------------
    private fun doRegisterEmailMode() {
        if (!btnSign.isEnabled) { revalidate(); return }

        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPass.text?.toString().orEmpty()
        val chosenRole = roleFromToggle()
        val finalRole = if (adminSeedEmails.contains(email.lowercase())) "admin" else chosenRole

        progress.visibility = View.VISIBLE
        btnSign.isEnabled = false

        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (!task.isSuccessful) {
                progress.visibility = View.GONE
                btnSign.isEnabled = true
                tilPassword.error = task.exception?.localizedMessage ?: "Register failed"
                return@addOnCompleteListener
            }

            val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

            val profile = mutableMapOf(
                "uid" to uid,
                "firstName" to first,
                "surname" to sur,
                "email" to email,
                "role" to finalRole,
                "createdAt" to System.currentTimeMillis()
            )

            when (finalRole) {
                "student" -> profile["fieldOfStudy"] = etFieldOfStudy.text?.toString()?.trim().orEmpty()
                "consultant" -> profile["specialisation"] = etSpecialisation.text?.toString()?.trim().orEmpty()
                "admin" -> { /* nothing extra */ }
            }

            db.reference.child("users").child(uid).setValue(profile).addOnCompleteListener {
                progress.visibility = View.GONE
                btnSign.isEnabled = true
                if (it.isSuccessful) routeByRole(finalRole)
            }
        }
    }

    // ---------------- Google mode (profile completion only) ----------------
    private fun doRegisterGoogleMode() {
        if (!btnSign.isEnabled) { revalidate(); return }

        val gUser = auth.currentUser ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = gUser.uid
        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = (gUser.email ?: etEmail.text?.toString() ?: "").trim()
        val chosenRole = roleFromToggle()
        val finalRole = if (adminSeedEmails.contains(email.lowercase())) "admin" else chosenRole

        progress.visibility = View.VISIBLE
        btnSign.isEnabled = false

        val ref = db.reference.child("users").child(uid)
        ref.get().addOnSuccessListener { snap ->
            val profile = mutableMapOf(
                "uid" to uid,
                "firstName" to first,
                "surname" to sur,
                "email" to email,
                "role" to finalRole,
                "updatedAt" to System.currentTimeMillis()
            )
            if (!snap.exists()) {
                profile["createdAt"] = System.currentTimeMillis()
            }

            when (finalRole) {
                "student" -> profile["fieldOfStudy"] = etFieldOfStudy.text?.toString()?.trim().orEmpty()
                "consultant" -> profile["specialisation"] = etSpecialisation.text?.toString()?.trim().orEmpty()
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
