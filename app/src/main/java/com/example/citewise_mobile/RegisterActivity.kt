package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database

class RegisterActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.database

    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnStudent: MaterialButton
    private lateinit var btnConsultant: MaterialButton

    private lateinit var sectionStudent: View
    private lateinit var sectionConsultant: View

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Toggle + sections
        toggle = findViewById(R.id.toggleAccountType)
        btnStudent = findViewById(R.id.btnStudent)
        btnConsultant = findViewById(R.id.btnConsultant)
        sectionStudent = findViewById(R.id.sectionStudent)
        sectionConsultant = findViewById(R.id.sectionConsultant)

        // Common fields
        etFirst = findViewById(R.id.etFirstName)
        etSur = findViewById(R.id.etSurname)
        etEmail = findViewById(R.id.etEmail)
        etPass = findViewById(R.id.etPassword)
        pbStrength = findViewById(R.id.pbPasswordStrength)
        actvLanguage = findViewById(R.id.actvLanguage)

        // Student fields
        actvInstitution = findViewById(R.id.actvInstitution)
        etField = findViewById(R.id.etFieldOfStudy)

        // Consultant fields
        etCompany = findViewById(R.id.etCompany)

        // Actions
        cbTerms = findViewById(R.id.cbTerms)
        btnSign = findViewById(R.id.btnSignUp)
        progress = findViewById(R.id.progress)
        tvGoLogin = findViewById(R.id.tvGoLogin)

        // Dropdown adapters
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

        // Password strength (very simple)
        etPass.addTextChangedListener { s ->
            val n = (s?.length ?: 0).coerceAtMost(12)
            pbStrength.progress = (n * 100 / 12)
            revalidate()
        }

        // Revalidate on changes
        listOf(etFirst, etSur, etEmail, etField, etCompany).forEach {
            it.addTextChangedListener { revalidate() }
        }
        actvInstitution.addTextChangedListener { revalidate() }
        actvLanguage.addTextChangedListener { revalidate() }
        cbTerms.setOnCheckedChangeListener { _, _ -> revalidate() }

        btnSign.setOnClickListener { doRegister() }
        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun role(): String = when (toggle.checkedButtonId) {
        R.id.btnStudent -> "student"
        R.id.btnConsultant -> "consultant"
        else -> "student" // fallback
    }

    private fun showRole(role: String) {
        sectionStudent.visibility = if (role == "student") View.VISIBLE else View.GONE
        sectionConsultant.visibility = if (role == "consultant") View.VISIBLE else View.GONE
    }

    private fun revalidate() {
        val commonOk = etFirst.text?.isNotBlank() == true &&
                etSur.text?.isNotBlank() == true &&
                validEmail(etEmail.text?.toString()) &&
                (etPass.text?.length ?: 0) >= 6 &&
                actvLanguage.text?.isNotBlank() == true &&
                cbTerms.isChecked

        val roleOk = when (role()) {
            "student" -> actvInstitution.text?.isNotBlank() == true && etField.text?.isNotBlank() == true
            "consultant" -> etCompany.text?.isNotBlank() == true
            else -> false
        }

        btnSign.isEnabled = commonOk && roleOk
    }

    private fun validEmail(value: String?): Boolean =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun doRegister() {
        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPass.text?.toString().orEmpty()
        val lang = actvLanguage.text?.toString()?.trim().orEmpty()
        val r = role()

        if (!btnSign.isEnabled) {
            revalidate(); return
        }

        progress.visibility = View.VISIBLE
        btnSign.isEnabled = false

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    progress.visibility = View.GONE
                    btnSign.isEnabled = true
                    return@addOnCompleteListener
                }

                val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                val profile = mutableMapOf(
                    "uid" to uid,
                    "firstName" to first,
                    "surname" to sur,
                    "email" to email,
                    "preferredLanguage" to lang,
                    "role" to r,
                    "createdAt" to System.currentTimeMillis()
                )

                when (r) {
                    "student" -> {
                        profile["institution"] = actvInstitution.text?.toString()?.trim().orEmpty()
                        profile["fieldOfStudy"] = etField.text?.toString()?.trim().orEmpty()
                    }
                    "consultant" -> {
                        profile["company"] = etCompany.text?.toString()?.trim().orEmpty()
                    }
                }

                db.reference.child("users").child(uid).setValue(profile)
                    .addOnCompleteListener {
                        progress.visibility = View.GONE
                        btnSign.isEnabled = true
                        if (it.isSuccessful) {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    }
            }
    }
}
