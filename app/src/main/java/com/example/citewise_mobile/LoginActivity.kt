package com.example.citewise_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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

// Workers
import com.example.citewise_mobile.offline.RequestsPullWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val auth = com.google.firebase.Firebase.auth
    private val prefs by lazy { getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogleLogin: View
    private lateinit var tvForgot: MaterialTextView
    private lateinit var tvGoSignUp: MaterialTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Bind views
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        tvForgot = findViewById(R.id.tvForgot)
        tvGoSignUp = findViewById(R.id.tvGoSignUp)

        btnLogin.isEnabled = true

        // Clear errors while typing
        etEmail.addTextChangedListener { etEmail.error = null }
        etPassword.addTextChangedListener { etPassword.error = null }

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

        // Go to Register
        tvGoSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Google Sign-In
        btnGoogleLogin.setOnClickListener { signInWithGoogle() }
    }

    /**
     * IMPORTANT: skip the login form if we already have a Firebase session.
     * We route by cached role instantly, then refresh from DB in the background.
     */
    override fun onStart() {
        super.onStart()
        val user = auth.currentUser ?: return

        val cachedRole = prefs.getString("user_role", null)
        if (cachedRole != null) {
            startActivity(Intent(this, destForRole(cachedRole)))
            finish()
            // Refresh role quietly (will update cache if changed)
            lifecycleScope.launch { refreshRoleCache(user.uid) }
        } else {
            // No cache yet – fetch role before routing
            routeByRole(fetchAndCache = true)
        }
    }

    // ---------------- Email/Password ----------------
    private fun tryEmailPasswordLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPassword.text?.toString().orEmpty()

        etEmail.error = null
        etPassword.error = null

        if (email.isEmpty()) { etEmail.error = "Email is required"; etEmail.requestFocus(); return }
        if (!isValidEmail(email)) { etEmail.error = "Enter a valid email address"; etEmail.requestFocus(); return }
        if (pass.isEmpty()) { etPassword.error = "Password is required"; etPassword.requestFocus(); return }
        if (pass.length < 6) { etPassword.error = "Password must be at least 6 characters"; etPassword.requestFocus(); return }

        hideKeyboard()

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    kickOffInitialSync()
                    routeByRole(fetchAndCache = true)
                } else {
                    etPassword.error = task.exception?.localizedMessage ?: "Login failed"
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
                etPassword.error = "Google sign-in aborted"
            } catch (t: Throwable) {
                etPassword.error = t.localizedMessage ?: "Google sign-in failed"
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
                        etPassword.error = task.exception?.localizedMessage ?: "Google sign-in failed"
                        return@addOnCompleteListener
                    }
                    handleFirstTimeGoogleUserOrRoute()
                }
        } else {
            etPassword.error = "Selected credential is not a Google account"
        }
    }

    private fun handleFirstTimeGoogleUserOrRoute() {
        val uid = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        ref.get().addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                etPassword.error = t.exception?.localizedMessage ?: "Could not verify account"
                return@addOnCompleteListener
            }
            if (t.result?.exists() == true) {
                kickOffInitialSync()
                routeByRole(fetchAndCache = true)
            } else {
                startActivity(
                    Intent(this, RegisterActivity::class.java)
                        .putExtra("mode", "google")
                )
                finish()
            }
        }
    }

    // ---------------- Role Routing ----------------
    /**
     * Fetches role from RTDB and routes. Optionally caches it.
     */
    private fun routeByRole(fetchAndCache: Boolean) {
        val uid = auth.currentUser?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("role")

        ref.get().addOnCompleteListener { t ->
            val role = t.result?.getValue(String::class.java)?.lowercase() ?: "student"
            if (fetchAndCache) prefs.edit().putString("user_role", role).apply()
            startActivity(Intent(this, destForRole(role)))
            finish()
        }
    }

    private fun destForRole(role: String): Class<*> = when (role.lowercase()) {
        "student"    -> StudentDashboardActivity::class.java
        "consultant" -> ConsultantDashboardActivity::class.java
        "admin"      -> AdminDashboardActivity::class.java
        else         -> StudentDashboardActivity::class.java
    }

    private suspend fun refreshRoleCache(uid: String) {
        val ref = FirebaseDatabase.getInstance()
            .reference.child("users").child(uid).child("role")

        // run off main to avoid blocking UI
        val snapshot = withContext(Dispatchers.IO) { ref.get().await() }
        val latest = snapshot.getValue(String::class.java)?.lowercase()
        if (!latest.isNullOrBlank()) {
            getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit().putString("user_role", latest).apply()
        }
    }

    // ---------------- Login-time Pull ----------------
    private fun kickOffInitialSync() {
        RequestsPullWorker.oneShot(this)
        // Add other workers if/when you need them
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
