package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class LoginActivity : AppCompatActivity() {

    private val auth = Firebase.auth

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progress: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progress)
        val tvForgot = findViewById<MaterialTextView>(R.id.tvForgot)
        val tvGoSignUp = findViewById<MaterialTextView>(R.id.tvGoSignUp)

        // Enable button only when inputs are valid
        val revalidate = {
            val ok = isValidEmail(etEmail.text?.toString()) &&
                    (etPassword.text?.length ?: 0) >= 6
            btnLogin.isEnabled = ok
        }
        listOf(etEmail, etPassword).forEach {
            it.addTextChangedListener(afterTextChanged = { revalidate() })
        }

        // Login action
        btnLogin.setOnClickListener { tryLogin() }

        // Keyboard "done" triggers login
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnLogin.isEnabled) {
                tryLogin(); true
            } else false
        }

        // Forgot password (sends reset email if email looks valid)
        tvForgot.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            if (!isValidEmail(email)) {
                tilEmail.error = "Enter a valid email to reset"
                return@setOnClickListener
            }
            tilEmail.error = null
            progress.show()
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener {
                    progress.hide()
                    if (it.isSuccessful) {
                        tilEmail.helperText = "Reset link sent to $email"
                    } else {
                        tilEmail.error = it.exception?.localizedMessage ?: "Failed to send reset"
                    }
                }
        }

        // Go to registration
        tvGoSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun tryLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPassword.text?.toString().orEmpty()

        tilEmail.error = null
        tilPassword.error = null

        if (!isValidEmail(email)) {
            tilEmail.error = "Invalid email address"
            return
        }
        if (pass.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            return
        }

        progress.show()
        btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                progress.hide()
                btnLogin.isEnabled = true

                if (task.isSuccessful) {
                    // TODO: navigate to your home screen
                    startActivity(Intent(this, ConsultantDashboardActivity::class.java))
                    finish()
                } else {
                    tilPassword.error = task.exception?.localizedMessage ?: "Login failed"
                }
            }
    }

    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    // tiny helpers for progress visibility
    private fun LinearProgressIndicator.show() { this.visibility = android.view.View.VISIBLE }
    private fun LinearProgressIndicator.hide() { this.visibility = android.view.View.GONE }
}
