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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// Workers (periodic + one-shot triggers)
import com.example.citewise_mobile.offline.RequestsPullWorker
import com.example.citewise_mobile.offline.UsersSyncWorker
import com.example.citewise_mobile.offline.MessagesSyncWorker
import com.example.citewise_mobile.offline.DocumentsSyncWorker
import com.example.citewise_mobile.offline.RequestsSyncWorker

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
        etEmail        = findViewById(R.id.etEmail)
        etPassword     = findViewById(R.id.etPassword)
        btnLogin       = findViewById(R.id.btnLogin)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        tvForgot       = findViewById(R.id.tvForgot)
        tvGoSignUp     = findViewById(R.id.tvGoSignUp)

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
     * If already signed in, route immediately and ensure syncs are running.
     * Added: consultants are routed via isApproved check.
     */
    override fun onStart() {
        super.onStart()
        val user = auth.currentUser ?: return

        // Start/ensure background syncs even when user is already signed in
        startAllSyncs()

        val cachedRole = prefs.getString("user_role", null)
        if (cachedRole != null) {
            if (cachedRole.equals("CONSULTANT", ignoreCase = true)) {
                // Check approval before routing
                routeConsultantByApproval(user.uid)
                // Also refresh role cache in the background
                lifecycleScope.launch { refreshRoleCache(user.uid) }
            } else {
                startActivity(Intent(this, destForRole(cachedRole)))
                finish()
                lifecycleScope.launch { refreshRoleCache(user.uid) }
            }
        } else {
            // No cache yet – read DB; if user/role missing, go to Register.
            routeByRole(fetchAndCache = true)
        }
    }

    // ───────────────────────── Email/Password ─────────────────────────
    private fun tryEmailPasswordLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass  = etPassword.text?.toString().orEmpty()

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
                    // Kick all syncs on every fresh login
                    startAllSyncs()
                    routeByRole(fetchAndCache = true)
                } else {
                    etPassword.error = task.exception?.localizedMessage ?: "Login failed"
                }
            }
    }

    // ───────────────────────── Google Sign-In ─────────────────────────
    private fun signInWithGoogle() {
        val serverClientId = getString(R.string.default_web_client_id)
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
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

    /**
     * After Google auth:
     *  - If /users/{uid} exists -> start syncs + route by role (with consultant approval check)
     *  - Else -> registration flow
     */
    private fun handleFirstTimeGoogleUserOrRoute() {
        val uid = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        ref.get().addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                etPassword.error = t.exception?.localizedMessage ?: "Could not verify account"
                return@addOnCompleteListener
            }
            if (t.result?.exists() == true) {
                startAllSyncs()
                routeByRole(fetchAndCache = true)
            } else {
                goToRegister()
            }
        }
    }

    // ───────────────────────── Role Routing ─────────────────────────
    /**
     * Reads /users/{uid}; if missing or role missing -> Register.
     * Otherwise route and (optionally) cache role. (Role normalized to UPPERCASE)
     * For CONSULTANT, checks isApproved and routes to PendingApproval if false/missing.
     */
    private fun routeByRole(fetchAndCache: Boolean) {
        val uid = auth.currentUser?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }

        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        userRef.get().addOnCompleteListener { t ->
            if (!t.isSuccessful) { goToRegister(); return@addOnCompleteListener }

            val snap = t.result
            if (snap == null || !snap.exists()) { goToRegister(); return@addOnCompleteListener }

            val roleUpper = snap.child("role").getValue(String::class.java)
                ?.trim()
                ?.uppercase()

            if (roleUpper.isNullOrBlank()) { goToRegister(); return@addOnCompleteListener }

            if (fetchAndCache) prefs.edit().putString("user_role", roleUpper).apply()

            if (roleUpper == "CONSULTANT") {
                routeConsultantByApproval(uid)
            } else {
                startActivity(Intent(this, destForRole(roleUpper)))
                finish()
            }
        }
    }

    /**
     * Consultant-specific router. Reads /users/{uid}/isApproved once.
     * - true  -> ConsultantDashboard
     * - false -> PendingApproval
     * - null/error -> PendingApproval (safe default)
     */
    private fun routeConsultantByApproval(uid: String) {
        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("isApproved")

        ref.get().addOnCompleteListener { t ->
            val approved = t.isSuccessful && (t.result?.getValue(Boolean::class.java) == true)
            val next = if (approved) {
                ConsultantDashboardActivity::class.java
            } else {
                PendingApprovalActivity::class.java
            }
            startActivity(Intent(this, next))
            finish()
        }
    }

    private fun goToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java).putExtra("mode", "google"))
        finish()
    }

    private fun destForRole(role: String): Class<*> = when (role.uppercase()) {
        "STUDENT"    -> StudentDashboardActivity::class.java
        "CONSULTANT" -> ConsultantDashboardActivity::class.java // NOTE: not used for consultants anymore without approval check
        "ADMIN"      -> AdminDashboardActivity::class.java
        else         -> StudentDashboardActivity::class.java
    }

    private suspend fun refreshRoleCache(uid: String) {
        val ref = FirebaseDatabase.getInstance()
            .reference.child("users").child(uid).child("role")
        val snapshot = withContext(Dispatchers.IO) { ref.get().await() }
        val latestUpper = snapshot.getValue(String::class.java)
            ?.trim()
            ?.uppercase()
        if (!latestUpper.isNullOrBlank()) {
            prefs.edit().putString("user_role", latestUpper).apply()
        }
    }

    // ───────────────────────── Sync Kickers ─────────────────────────
    /**
     * Call this after any successful sign-in AND when app opens with an existing session.
     * It:
     *  - Schedules periodic workers (idempotent).
     *  - Triggers one-shot initial pulls to populate local DB quickly.
     */
    private fun startAllSyncs() {
        // Periodic (idempotent enqueueUnique… UPDATE)
        UsersSyncWorker.schedule(this)
        MessagesSyncWorker.schedule(this)
        DocumentsSyncWorker.schedule(this)
        RequestsSyncWorker.schedulePeriodic(this)

        // One-shot “prime” pulls for faster first-run UX
        RequestsPullWorker.oneShot(this)   // pulls my service requests into Room
        UsersSyncWorker.oneShot(this)      // fetches all users immediately
    }

    // ───────────────────────── Helpers ─────────────────────────
    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }
}
