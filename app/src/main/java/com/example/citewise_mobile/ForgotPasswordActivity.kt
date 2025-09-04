package com.example.citewise_mobile

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.View
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
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progress: LinearProgressIndicator

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Bind views
        tilEmail = findViewById(R.id.tilEmail)
        etEmail  = findViewById(R.id.etEmail)
        btnSend  = findViewById(R.id.btnSendEmailLink)
        progress = findViewById(R.id.progress)

        // Prefill if email was passed from LoginActivity (optional)
        intent.getStringExtra("email")?.let { etEmail.setText(it) }

        // Enable button only when email looks valid
        etEmail.addTextChangedListener(afterTextChanged = {
            btnSend.isEnabled = isValidEmail(etEmail.text?.toString())
            if (tilEmail.error != null) tilEmail.error = null
            if (!btnSend.isEnabled) tilEmail.helperText = null
        })

        // Send reset link using DEFAULT Firebase domain (no ActionCodeSettings)
        btnSend.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()

            tilEmail.error = null
            tilEmail.helperText = null

            if (!isValidEmail(email)) {
                tilEmail.error = "Enter a valid email"
                return@setOnClickListener
            }

            hideKeyboard()
            setLoading(true)

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        // Success == request accepted (email may still land in Spam)
                        tilEmail.helperText = "Reset link sent to $email"
                    } else {
                        tilEmail.error = task.exception?.localizedMessage ?: "Failed to send reset link"
                    }
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

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !loading
        etEmail.isEnabled = !loading
    }
}
