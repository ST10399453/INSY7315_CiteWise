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

// Material
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView

// Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase

// Credential Manager + Google Identity
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

    private lateinit var tvForgot: MaterialTextView
    private lateinit var tvGoSignUp: MaterialTextView
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var btnGoogle: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Edge-to-edge insets (root view must have id @id/main)
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
        progress = findViewById(R.id.progress)
        btnGoogle = findViewById(R.id.btnGoogle)

        tvForgot = findViewById(R.id.tvForgot)
        tvGoSignUp = findViewById(R.id.tvGoSignUp)

        // Enable email/password button only when inputs are valid
        val revalidate = {
            val ok = isValidEmail(etEmail.text?.toString()) && (etPassword.text?.length ?: 0) >= 6
            btnLogin.isEnabled = ok
        }
        listOf(etEmail, etPassword).forEach { it.addTextChangedListener(afterTextChanged = { revalidate() }) }

        // Email/password login
        btnLogin.setOnClickListener { tryEmailPasswordLogin() }

        // IME action "done" triggers login
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnLogin.isEnabled) {
                tryEmailPasswordLogin(); true
            } else false
        }

        // Forgot password -> dedicated screen
        tvForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
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

        if (!isValidEmail(email)) { tilEmail.error = "Invalid email address"; return }
        if (pass.length < 6) { tilPassword.error = "Password must be at least 6 characters"; return }

        hideKeyboard()
        showLoading(true)

        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            showLoading(false)
            if (task.isSuccessful) {
                // Email/password users should already have a profile (created at registration)
                routeByRole()
            } else {
                tilPassword.error = task.exception?.localizedMessage ?: "Login failed"
            }
        }
    }

    // ---------------- Google Sign-In (Credential Manager) ----------------

    private fun signInWithGoogle() {
        // Use the Web client ID from google-services.json (or strings.xml if you added it manually)
        val serverClientId = getString(R.string.default_web_client_id)

        // Build the Google Sign-In option (newer API)
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()

        // Build the Credential Manager request
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val cm = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                showLoading(true)
                // May show account picker / One Tap
                val result = cm.getCredential(this@LoginActivity, request)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                showLoading(false)
                tilPassword.error = e.localizedMessage ?: "Google sign-in was cancelled or failed"
            } catch (t: Throwable) {
                showLoading(false)
                tilPassword.error = t.localizedMessage ?: "Google sign-in failed"
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        // Google ID token is delivered as a CustomCredential; createFrom() needs the Bundle 'data'
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        showLoading(false)
                        tilPassword.error = task.exception?.localizedMessage ?: "Google sign-in failed"
                        return@addOnCompleteListener
                    }
                    // Signed in with Google → check if profile exists; if not, complete profile
                    handleFirstTimeGoogleUserOrRoute()
                }
        } else {
            showLoading(false)
            tilPassword.error = "Selected credential is not a Google account"
        }
    }

    /**
     * After Google sign-in, check if /users/{uid} exists.
     *  - If exists → route by role.
     *  - If not → launch RegisterActivity in "google" mode to complete profile.
     */
    private fun handleFirstTimeGoogleUserOrRoute() {
        val uid = auth.currentUser?.uid ?: run {
            showLoading(false)
            return
        }
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        ref.get().addOnCompleteListener { t ->
            showLoading(false)
            if (!t.isSuccessful) {
                tilPassword.error = t.exception?.localizedMessage ?: "Could not verify account"
                return@addOnCompleteListener
            }
            val exists = t.result?.exists() == true
            if (exists) {
                routeByRole()
            } else {
                // First-time Google user → complete profile (role/institution/company/etc.)
                startActivity(
                    Intent(this, RegisterActivity::class.java)
                        .putExtra("mode", "google")
                )
                finish()
            }
        }
    }

    // ---------------- Role Routing ----------------

    private fun routeByRole() {
        val uid = auth.currentUser?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }

        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("role")

        progress.visibility = View.VISIBLE

        ref.get().addOnCompleteListener { t ->
            progress.visibility = View.GONE
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

    private fun showLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        etEmail.isEnabled = !loading
        etPassword.isEnabled = !loading
        btnGoogle.isEnabled = !loading
    }
}
