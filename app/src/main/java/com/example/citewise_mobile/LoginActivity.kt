package com.example.citewise_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val auth = com.google.firebase.Firebase.auth

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogle: ImageButton
    private lateinit var tvForgot: MaterialTextView
    private lateinit var tvGoSignUp: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Bind views
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogle = findViewById(R.id.btnGoogle)
        tvForgot = findViewById(R.id.tvForgot)
        tvGoSignUp = findViewById(R.id.tvGoSignUp)

        // Always enable the button; we handle validation on click
        btnLogin.isEnabled = true

        // Clear errors while typing
        etEmail.addTextChangedListener {
            tilEmail.error = null
            tilEmail.helperText = null
        }
        etPassword.addTextChangedListener {
            tilPassword.error = null
        }

        // Email/password login
        btnLogin.setOnClickListener { tryEmailPasswordLogin() }

        // IME action "done" triggers login
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryEmailPasswordLogin(); true
            } else false
        }

        // Forgot password
        tvForgot.setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java)
                    .putExtra("email", etEmail.text?.toString()?.trim())
            )
        }

        // Sign up
        tvGoSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Google Sign-In
        btnGoogle.setOnClickListener { signInWithGoogle() }
    }

    // ---------------- Email/Password ----------------
    private fun tryEmailPasswordLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPassword.text?.toString().orEmpty()

        tilEmail.error = null
        tilEmail.helperText = null
        tilPassword.error = null

        // Explicit empty + validity checks
        if (email.isEmpty()) {
            tilEmail.error = "Email is required"
            etEmail.requestFocus()
            return
        }
        if (!isValidEmail(email)) {
            tilEmail.error = "Enter a valid email address"
            etEmail.requestFocus()
            return
        }
        if (pass.isEmpty()) {
            tilPassword.error = "Password is required"
            etPassword.requestFocus()
            return
        }
        if (pass.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            etPassword.requestFocus()
            return
        }

        hideKeyboard()

        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                routeByRole()
            } else {
                tilPassword.error = task.exception?.localizedMessage ?: "Login failed"
            }
        }
    }

    // ---------------- Google Sign-In ----------------
    private fun signInWithGoogle() {
        val serverClientId = getString(R.string.default_web_client_id)
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
        val cm = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = cm.getCredential(this@LoginActivity, request)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                tilPassword.error = "Google sign-in Aborted"
            } catch (t: Throwable) {
                tilPassword.error = t.localizedMessage ?: "Google sign-in failed"
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        tilPassword.error = task.exception?.localizedMessage ?: "Google sign-in failed"
                        return@addOnCompleteListener
                    }
                    handleFirstTimeGoogleUserOrRoute()
                }
        } else {
            tilPassword.error = "Selected credential is not a Google account"
        }
    }

    private fun handleFirstTimeGoogleUserOrRoute() {
        val uid = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        ref.get().addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                tilPassword.error = t.exception?.localizedMessage ?: "Could not verify account"
                return@addOnCompleteListener
            }
            if (t.result?.exists() == true) {
                routeByRole()
            } else {
                startActivity(Intent(this, RegisterActivity::class.java).putExtra("mode", "google"))
                finish()
            }
        }
    }

    // ---------------- Role Routing ----------------
    private fun routeByRole() {
        val uid = auth.currentUser?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid).child("role")
        ref.get().addOnCompleteListener { t ->
            val role = t.result?.getValue(String::class.java)?.lowercase() ?: "student"
            when (role) {
                "student" -> startActivity(Intent(this, StudentDashboardActivity::class.java))
                "consultant" -> startActivity(Intent(this, ConsultantDashboardActivity::class.java))
                "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
                else -> startActivity(Intent(this, StudentDashboardActivity::class.java))
            }
            finish()
        }
    }

    // ---------------- Helpers ----------------
    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }
}
