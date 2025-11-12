package com.example.citewise_mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private val auth = com.google.firebase.Firebase.auth
    private val prefs by lazy { getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogleLogin: View
    private lateinit var tvForgot: MaterialTextView
    private lateinit var tvGoSignUp: MaterialTextView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Whether granted or denied, continue to dashboard
        // User can enable notifications later in settings if denied
        proceedToDashboard()
    }

    private var pendingNavigation: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        etEmail        = findViewById(R.id.etEmail)
        etPassword     = findViewById(R.id.etPassword)
        btnLogin       = findViewById(R.id.btnLogin)
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin)
        tvForgot       = findViewById(R.id.tvForgot)
        tvGoSignUp     = findViewById(R.id.tvGoSignUp)

        btnLogin.isEnabled = true

        etEmail.addTextChangedListener { etEmail.error = null }
        etPassword.addTextChangedListener { etPassword.error = null }

        btnLogin.setOnClickListener { tryEmailPasswordLogin() }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryEmailPasswordLogin(); true
            } else false
        }

        tvForgot.setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java)
                    .putExtra("email", etEmail.text?.toString()?.trim())
            )
        }

        tvGoSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnGoogleLogin.setOnClickListener { signInWithGoogle() }
    }

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser ?: return

        startAllSyncs()
        requestFcmToken()

        val cachedRole = prefs.getString("user_role", null)
        if (cachedRole != null) {
            if (cachedRole.equals("CONSULTANT", ignoreCase = true)) {
                routeConsultantByApproval(user.uid)
                lifecycleScope.launch { refreshRoleCache(user.uid) }
            } else {
                checkNotificationPermissionAndNavigate(Intent(this, destForRole(cachedRole)))
                lifecycleScope.launch { refreshRoleCache(user.uid) }
            }
        } else {
            routeByRole(fetchAndCache = true)
        }
    }


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
                    startAllSyncs()
                    requestFcmToken()
                    routeByRole(fetchAndCache = true)
                } else {
                    etPassword.error = task.exception?.localizedMessage ?: "Login failed"
                }
            }
    }

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

//    private fun handleGoogleCredential(credential: Credential) {
//        if (credential is CustomCredential &&
//            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
//        ) {
//            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
//            val idToken = googleIdTokenCredential.idToken
//            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
//            auth.signInWithCredential(firebaseCredential)
//                .addOnCompleteListener(this) { task ->
//                    if (!task.isSuccessful) {
//                        etPassword.error = task.exception?.localizedMessage ?: "Google sign-in failed"
//                        return@addOnCompleteListener
//                    }
//                    requestFcmToken()
//                    handleFirstTimeGoogleUserOrRoute()
//                }
//        } else {
//            etPassword.error = "Selected credential is not a Google account"
//        }
//    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            // ✅ Extract profile info directly from Google
            val idToken = googleIdTokenCredential.idToken
            val displayName = googleIdTokenCredential.displayName
            val email = googleIdTokenCredential.id
            val profilePicUri = googleIdTokenCredential.profilePictureUri

            // Optional: log or preview
            android.util.Log.d("LoginActivity", "Google user = $displayName ($email)")
            android.util.Log.d("LoginActivity", "Photo = $profilePicUri")

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        etPassword.error = task.exception?.localizedMessage ?: "Google sign-in failed"
                        return@addOnCompleteListener
                    }

                    val user = auth.currentUser
                    if (user != null && profilePicUri != null) {
                        // ✅ Optionally store photo URL in Realtime Database
                        val ref = FirebaseDatabase.getInstance().getReference("users").child(user.uid)
                        ref.child("photoUrl").setValue(profilePicUri.toString())
                    }

                    requestFcmToken()
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
                startAllSyncs()
                routeByRole(fetchAndCache = true)
            } else {
                goToRegister()
            }
        }
    }

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
                checkNotificationPermissionAndNavigate(Intent(this, destForRole(roleUpper)))
            }
        }
    }

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
            checkNotificationPermissionAndNavigate(Intent(this, next))
        }
    }

    private fun checkNotificationPermissionAndNavigate(destination: Intent) {
        pendingNavigation = destination

        // Only check for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted, proceed
                    proceedToDashboard()
                }
                else -> {
                    // Request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 and below don't need runtime permission
            proceedToDashboard()
        }
    }

    private fun proceedToDashboard() {
        pendingNavigation?.let {
            startActivity(it)
            finish()
        }
    }

    private fun goToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java).putExtra("mode", "google"))
        finish()
    }

    private fun destForRole(role: String): Class<*> = when (role.uppercase()) {
        "STUDENT"    -> StudentDashboardActivity::class.java
        "CONSULTANT" -> ConsultantDashboardActivity::class.java
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

    private fun startAllSyncs() {
        UsersSyncWorker.schedule(this)
        MessagesSyncWorker.schedule(this)
        DocumentsSyncWorker.schedule(this)
        RequestsSyncWorker.schedulePeriodic(this)

        RequestsPullWorker.oneShot(this)
        UsersSyncWorker.oneShot(this)
    }

    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    private fun requestFcmToken() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            android.util.Log.w("LoginActivity", "Cannot request FCM token: No user logged in")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                android.util.Log.w("LoginActivity", "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            android.util.Log.d("LoginActivity", "FCM token retrieved: $token")

            // Save token to Firestore
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(mapOf("createdAt" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    android.util.Log.d("LoginActivity", "FCM token saved to Firestore for user=$uid")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("LoginActivity", "Failed to save FCM token to Firestore", e)
                }
        }
    }
}
