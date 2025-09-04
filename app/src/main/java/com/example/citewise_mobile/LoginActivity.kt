package com.example.citewise_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progress)

        val tvForgot = findViewById<MaterialTextView>(R.id.tvForgot)
        val tvGoSignUp = findViewById<MaterialTextView>(R.id.tvGoSignUp)

        // Enable login only when valid
        val revalidate = {
            val ok = isValidEmail(etEmail.text?.toString()) && (etPassword.text?.length ?: 0) >= 6
            btnLogin.isEnabled = ok
        }
        listOf(etEmail, etPassword).forEach { it.addTextChangedListener(afterTextChanged = { revalidate() }) }

        // Login
        btnLogin.setOnClickListener { tryLogin() }

        // IME "done" triggers login
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnLogin.isEnabled) {
                tryLogin(); true
            } else false
        }

        // Forgot password -> ALWAYS redirect; email is asked on the reset screen
        findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvForgot)
            .setOnClickListener {
                startActivity(Intent(this, ForgotPasswordActivity::class.java))
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
        tilEmail.helperText = null
        tilPassword.error = null

        if (!isValidEmail(email)) {
            tilEmail.error = "Invalid email address"
            return
        }
        if (pass.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            return
        }

        hideKeyboard()
        progress.show()
        btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            progress.hide()
            btnLogin.isEnabled = true

            if (task.isSuccessful) {
                startActivity(Intent(this, StudentDashboardActivity::class.java))
                finish()
            } else {
                tilPassword.error = task.exception?.localizedMessage ?: "Login failed"
            }
        }
    }

    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    private fun LinearProgressIndicator.show() { this.visibility = View.VISIBLE }
    private fun LinearProgressIndicator.hide() { this.visibility = View.GONE }
}
