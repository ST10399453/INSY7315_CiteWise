package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database

class RegisterActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.database

    // Toggle / sections
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnStudent: MaterialButton
    private lateinit var btnConsultant: MaterialButton
    private lateinit var sectionStudent: View
    private lateinit var sectionConsultant: View

    // Inputs
    private lateinit var etFirst: TextInputEditText
    private lateinit var etSur: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var etPassConfirm: TextInputEditText
    private lateinit var etFieldOfStudy: TextInputEditText
    private lateinit var etSpecialisation: TextInputEditText

    // Actions
    private lateinit var btnSign: MaterialButton
    private lateinit var tvGoLogin: MaterialTextView

    private var isGoogleMode: Boolean = false

    // Optional seed admins
    private val adminSeedEmails = setOf(
        "admin@citewise.com",
        "you@example.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        isGoogleMode = intent.getStringExtra("mode")?.equals("google", ignoreCase = true) == true

        toggle = findViewById(R.id.toggleAccountType)
        btnStudent = findViewById(R.id.btnStudent)
        btnConsultant = findViewById(R.id.btnConsultant)
        sectionStudent = findViewById(R.id.sectionStudent)
        sectionConsultant = findViewById(R.id.sectionConsultant)

        etFirst = findViewById(R.id.etFirstName)
        etSur = findViewById(R.id.etSurname)
        etEmail = findViewById(R.id.etEmail)
        etPass = findViewById(R.id.etPassword)
        etPassConfirm = findViewById(R.id.etConfirmPassword)
        etFieldOfStudy = findViewById(R.id.etFieldOfStudy)
        etSpecialisation = findViewById(R.id.etSpecialisation)

        btnSign = findViewById(R.id.btnSignUp)
        tvGoLogin = findViewById(R.id.tvGoLogin)

        // Default role = student
        toggle.check(btnStudent.id)
        showRole("student")

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnStudent -> showRole("student")
                R.id.btnConsultant -> showRole("consultant")
            }
        }

        listOf(etFirst, etSur, etEmail, etPass, etPassConfirm, etFieldOfStudy, etSpecialisation)
            .forEach { it.addTextChangedListener { revalidate() } }

        if (isGoogleMode) {
            val gUser = auth.currentUser
            if (gUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            etEmail.setText(gUser.email ?: "")
            val name = (gUser.displayName ?: "")
            etFirst.setText(name.substringBeforeLast(" "))
            etSur.setText(name.substringAfterLast(" ").takeIf { it != name } ?: "")

            etEmail.isEnabled = false
            etPass.isVisible = false
            etPassConfirm.isVisible = false
        } else {
            etEmail.isEnabled = true
            etPass.isVisible = true
            etPassConfirm.isVisible = true
        }

        btnSign.setOnClickListener {
            if (isGoogleMode) doRegisterGoogleMode() else doRegisterEmailMode()
        }
        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        revalidate()
    }

    private fun roleFromToggle(): String = when (toggle.checkedButtonId) {
        R.id.btnStudent -> "student"
        R.id.btnConsultant -> "consultant"
        else -> "student"
    }

    private fun showRole(role: String) {
        sectionStudent.visibility = if (role == "student") View.VISIBLE else View.GONE
        sectionConsultant.visibility = if (role == "consultant") View.VISIBLE else View.GONE
        revalidate()
    }

    private fun revalidate() {
        etEmail.error = null
        etPass.error = null
        etPassConfirm.error = null

        val firstOk = etFirst.text?.isNotBlank() == true
        val surOk   = etSur.text?.isNotBlank() == true
        val emailOk = isValidEmail(etEmail.text?.toString())

        val p1 = etPass.text?.toString().orEmpty()
        val p2 = etPassConfirm.text?.toString().orEmpty()

        val pwOk = if (isGoogleMode) {
            true
        } else {
            var ok = true
            if (p1.isEmpty() || p2.isEmpty()) ok = false
            if (p1.isNotEmpty() && p1.length < 6) { etPass.error = "At least 6 characters"; ok = false }
            if (p2.isNotEmpty() && p1 != p2) { etPassConfirm.error = "Passwords do not match"; ok = false }
            ok
        }

        val roleOk = when (roleFromToggle()) {
            "student"    -> etFieldOfStudy.text?.isNotBlank() == true
            "consultant" -> etSpecialisation.text?.isNotBlank() == true
            else -> false
        }

        btnSign.isEnabled = firstOk && surOk && emailOk && pwOk && roleOk
    }

    private fun isValidEmail(v: String?): Boolean =
        !v.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(v).matches()

    // ---------------- Email/password flow ----------------
    private fun doRegisterEmailMode() {
        if (!btnSign.isEnabled) { revalidate(); return }

        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPass.text?.toString().orEmpty()
        val chosenRole = roleFromToggle()
        val finalRole = if (adminSeedEmails.contains(email.lowercase())) "admin" else chosenRole

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (!task.isSuccessful) {
                setLoading(false)
                etPass.error = task.exception?.localizedMessage ?: "Register failed"
                return@addOnCompleteListener
            }

            val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

            val profile = mutableMapOf(
                "uid" to uid,
                "firstName" to first,
                "surname" to sur,
                "email" to email,
                "role" to finalRole,
                "createdAt" to System.currentTimeMillis()
            )

            when (finalRole) {
                "student"    -> profile["fieldOfStudy"] = etFieldOfStudy.text?.toString()?.trim().orEmpty()
                "consultant" -> profile["specialisation"] = etSpecialisation.text?.toString()?.trim().orEmpty()
            }

            if (finalRole == "consultant") {
                profile["isApproved"] = false
            }

            db.reference.child("users").child(uid).setValue(profile).addOnCompleteListener {
                setLoading(false)
                if (it.isSuccessful) routeByRole(finalRole)
            }
        }
    }

    // ---------------- Google mode (profile completion only) ----------------
    private fun doRegisterGoogleMode() {
        if (!btnSign.isEnabled) { revalidate(); return }

        val gUser = auth.currentUser ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = gUser.uid
        val first = etFirst.text?.toString()?.trim().orEmpty()
        val sur = etSur.text?.toString()?.trim().orEmpty()
        val email = (gUser.email ?: etEmail.text?.toString() ?: "").trim()
        val chosenRole = roleFromToggle()
        val finalRole = if (adminSeedEmails.contains(email.lowercase())) "admin" else chosenRole

        setLoading(true)

        val ref = db.reference.child("users").child(uid)
        ref.get().addOnSuccessListener { snap ->
            val profile = mutableMapOf(
                "uid" to uid,
                "firstName" to first,
                "surname" to sur,
                "email" to email,
                "role" to finalRole,
                "updatedAt" to System.currentTimeMillis()
            )
            if (!snap.exists()) profile["createdAt"] = System.currentTimeMillis()

            when (finalRole) {
                "student"    -> profile["fieldOfStudy"] = etFieldOfStudy.text?.toString()?.trim().orEmpty()
                "consultant" -> profile["specialisation"] = etSpecialisation.text?.toString()?.trim().orEmpty()
            }

            if (finalRole == "consultant") {
                profile["isApproved"] = false
            }

            ref.updateChildren(profile as Map<String, Any>).addOnCompleteListener { done ->
                setLoading(false)
                if (done.isSuccessful) routeByRole(finalRole)
            }
        }.addOnFailureListener {
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSign.isEnabled = !loading
    }

    /**
     * Route by role. Consultants are sent to PendingApproval if not approved yet.
     */
    private fun routeByRole(role: String) {
        when (role.lowercase()) {
            "student" -> {
                startActivity(Intent(this, StudentDashboardActivity::class.java))
                finish()
            }
            "consultant" -> {
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    // Fallback if somehow no user
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    return
                }
                db.reference.child("users").child(uid).child("isApproved").get()
                    .addOnSuccessListener { snap ->
                        val approved = snap.getValue(Boolean::class.java) == true
                        val next = if (approved) {
                            Intent(this, ConsultantDashboardActivity::class.java)
                        } else {
                            Intent(this, PendingApprovalActivity::class.java)
                        }
                        startActivity(next)
                        finish()
                    }
                    .addOnFailureListener {
                        // If we can't read, be safe and show pending page
                        startActivity(Intent(this, PendingApprovalActivity::class.java))
                        finish()
                    }
            }
            "admin" -> {
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
            }
            else -> {
                startActivity(Intent(this, StudentDashboardActivity::class.java))
                finish()
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

