package com.example.citewise_mobile

import android.content.Context
import android.content.Intent
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
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseTooManyRequestsException
import android.os.Handler
import android.os.Looper
import android.widget.Toast


class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSend: MaterialButton

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tilEmail = findViewById(R.id.tilEmail)
        etEmail  = findViewById(R.id.etEmail)
        btnSend  = findViewById(R.id.btnSendEmailLink)

        // Prefill if provided
        intent.getStringExtra("email")?.let { etEmail.setText(it) }

        // Revalidate on change
        etEmail.addTextChangedListener(afterTextChanged = {
            tilEmail.error = null
            tilEmail.helperText = null
            revalidate()
        })
        // Initial state
        revalidate()

        btnSend.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()

            tilEmail.error = null
            tilEmail.helperText = null

            if (!isValidEmail(email)) {
                tilEmail.error = "Enter a valid email"
                return@setOnClickListener
            }

            hideKeyboard()


            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        // Let the user know it can take a bit, and to check Spam/Junk
                        tilEmail.helperText =
                            "Reset link sent to $email. It may take a few minutes — please also check your Spam/Junk folder."
                        Toast.makeText(
                            this,
                            "Reset link sent. Check your inbox (and Spam/Junk).",
                            Toast.LENGTH_LONG
                        ).show()

                        // Wait 5 seconds, then go back to Login
                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(
                                Intent(this, LoginActivity::class.java)
                            )
                            finish()
                        }, 5000)
                    } else {
                        val ex = task.exception
                        tilEmail.error = when (ex) {
                            is FirebaseAuthInvalidUserException ->
                                "No account found with that email"
                            is FirebaseTooManyRequestsException ->
                                "Too many requests. Please try again later"
                            else -> ex?.localizedMessage ?: "Failed to send reset link"
                        }
                    }
                }


        }
    }

    private fun revalidate() {
        btnSend.isEnabled = isValidEmail(etEmail.text?.toString())
    }

    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }


}
