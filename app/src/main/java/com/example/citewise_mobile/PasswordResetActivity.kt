package com.example.citewise_mobile

import android.content.Context
import android.net.Uri
import android.os.Bundle
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

class PasswordResetActivity : AppCompatActivity() {

    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnConfirm: MaterialButton
    private lateinit var progress: LinearProgressIndicator

    private val auth by lazy { FirebaseAuth.getInstance() }
    private var oobCode: String? = null  // the reset code from the email link

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_password_reset)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tilNewPassword = findViewById(R.id.tilNewPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnConfirm = findViewById(R.id.btnConfirmReset)
        progress = findViewById(R.id.progress)

        // Pull deep link (Dynamic Link or App Link)
        // Most clients put it in intent.data; keep fallback if needed.
        resolveDeepLink(intent?.data)

        // Re-validate inputs
        val revalidate = {
            val p1 = etNewPassword.text?.toString().orEmpty()
            val p2 = etConfirmPassword.text?.toString().orEmpty()
            btnConfirm.isEnabled = p1.length >= 6 && p1 == p2 && !oobCode.isNullOrEmpty()
        }
        listOf(etNewPassword, etConfirmPassword).forEach {
            it.addTextChangedListener(afterTextChanged = { revalidate() })
        }

        btnConfirm.setOnClickListener {
            tilNewPassword.error = null
            tilConfirmPassword.error = null
            val code = oobCode
            val p1 = etNewPassword.text?.toString().orEmpty()
            val p2 = etConfirmPassword.text?.toString().orEmpty()

            if (code.isNullOrEmpty()) {
                tilConfirmPassword.error = "Invalid or missing reset code. Reopen the link from your email."
                return@setOnClickListener
            }
            if (p1.length < 6 || p1 != p2) {
                tilConfirmPassword.error = "Passwords must match and be at least 6 characters"
                return@setOnClickListener
            }

            hideKeyboard()
            setLoading(true)

            // Optional: verify first (nice UX messages)
            auth.verifyPasswordResetCode(code)
                .addOnSuccessListener {
                    auth.confirmPasswordReset(code, p1)
                        .addOnCompleteListener { t ->
                            setLoading(false)
                            if (t.isSuccessful) {
                                // Done. You can route to Login screen or auto-sign-in flow.
                                finish() // or startActivity(Intent(this, LoginActivity::class.java))
                            } else {
                                tilConfirmPassword.error = t.exception?.localizedMessage ?: "Failed to reset password"
                            }
                        }
                }
                .addOnFailureListener { ex ->
                    setLoading(false)
                    tilConfirmPassword.error = ex.localizedMessage ?: "Invalid or expired reset link"
                }
        }
    }

    private fun resolveDeepLink(data: Uri?) {
        // Expected oobCode is a query parameter on the deep link
        // e.g. https://citewise.page.link/reset?oobCode=XXXXXX&mode=resetPassword&apiKey=...
        oobCode = data?.getQueryParameter("oobCode")
        // Some providers stick the full Firebase URL inside a "link" param. Handle that too:
        if (oobCode.isNullOrEmpty()) {
            val inner = data?.getQueryParameter("link")?.let(Uri::parse)
            oobCode = inner?.getQueryParameter("oobCode")
        }
    }

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnConfirm.isEnabled = !loading
        etNewPassword.isEnabled = !loading
        etConfirmPassword.isEnabled = !loading
    }
}
