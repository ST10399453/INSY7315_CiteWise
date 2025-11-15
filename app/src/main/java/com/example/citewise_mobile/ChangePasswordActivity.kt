package com.example.citewise_mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth

class ChangePasswordActivity : AppCompatActivity() {

    private val auth = Firebase.auth

    private lateinit var btnBack: ImageView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var etOldPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSave: Button

    private var loading = false
        set(value) {
            field = value
            btnSave.isEnabled = !value && inputsValid()
            btnSave.alpha = if (value) 0.6f else 1f
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // Bind
        btnBack = findViewById(R.id.btnBack)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        etOldPassword = findViewById(R.id.etOldPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSave = findViewById(R.id.btnSave)

        tvHeaderTitle.text = "Change Password"
        btnBack.setOnClickListener { finish() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnSave.isEnabled = !loading && inputsValid()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etOldPassword.addTextChangedListener(watcher)
        etNewPassword.addTextChangedListener(watcher)
        etConfirmPassword.addTextChangedListener(watcher)

        btnSave.setOnClickListener { changePassword() }
        btnSave.isEnabled = false
    }

    private fun inputsValid(): Boolean {
        val old = etOldPassword.text?.toString()?.trim().orEmpty()
        val new = etNewPassword.text?.toString()?.trim().orEmpty()
        val confirm = etConfirmPassword.text?.toString()?.trim().orEmpty()
        var ok = true

        // basic length rule (match your app policy if different)
        if (new.isNotEmpty() && new.length < 6) ok = false
        if (confirm.isNotEmpty() && confirm != new) ok = false
        if (old.isEmpty() || new.isEmpty() || confirm.isEmpty()) ok = false

        return ok
    }

    private fun changePassword() {
        if (!inputsValid()) {
            Toast.makeText(this, "Please fix the highlighted fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide keyboard
        currentFocus?.let { v ->
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        val oldPw = etOldPassword.text.toString().trim()
        val newPw = etNewPassword.text.toString().trim()
        val confirmPw = etConfirmPassword.text.toString().trim()

        // Inline validation
        if (newPw.length < 6) {
            etNewPassword.error = "At least 6 characters"
            etNewPassword.requestFocus()
            return
        }
        if (newPw != confirmPw) {
            etConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return
        }
        if (oldPw == newPw) {
            etNewPassword.error = "New password must be different"
            etNewPassword.requestFocus()
            return
        }

        loading = true

        // If user is not password provider (e.g., Google), guide via reset email
        val providers = user.providerData.map { it.providerId }
        val isPasswordProvider = providers.contains("password")
        val email = user.email

        if (!isPasswordProvider || email.isNullOrBlank()) {
            // Send password reset email instead
            auth.sendPasswordResetEmail(email ?: "").addOnCompleteListener { t ->
                loading = false
                if (t.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        t.exception?.localizedMessage ?: "Could not send reset email",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            return
        }

        // Reauthenticate with old password, then update
        val cred = EmailAuthProvider.getCredential(email, oldPw)
        user.reauthenticate(cred).addOnCompleteListener { reauth ->
            if (!reauth.isSuccessful) {
                loading = false
                etOldPassword.error = reauth.exception?.localizedMessage ?: "Old password incorrect"
                etOldPassword.requestFocus()
                return@addOnCompleteListener
            }

            user.updatePassword(newPw).addOnCompleteListener { upd ->
                loading = false
                if (upd.isSuccessful) {
                    Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        upd.exception?.localizedMessage ?: "Failed to update password",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 *
 * Android Developer. 2024. “Grant Partial Access to Photos and Videos”.
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Knowledge. 2023a. “Bottom Navigation Bar in Android Studio Using Java | Explanation”.
 * https://www.youtube.com/watch?v=0x5kmLY16qE
 * [accessed 20 September 2023].
 *
 * Android Knowledge. 2023b. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
 * https://www.youtube.com/watch?v=oGyQMBKPuNY
 * [accessed 21 September 2025].
 *
 * Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
 * https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494
 * [accessed 30 September 2025].
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2017. “How to Use Glide Image Loader Library in Android Apps?”
 * https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/
 * [accessed 30 September 2025].
 *
 * GeeksforGeeks. 2020. “SimpleAdapter in Android with Example”.
 * https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/
 * [accessed 19 August 2025].
 *
 * GeeksforGeeks. 2021. “State ProgressBar in Android”.
 * https://www.geeksforgeeks.org/android/state-progressbar-in-android/
 * [accessed 22 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”.
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */
