package com.example.citewise_mobile

import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class EditProfileActivity : AppCompatActivity() {

    // Firebase
    private val auth = Firebase.auth
    private lateinit var usersRef: DatabaseReference

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var etFullName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etFieldOfStudy: EditText
    private lateinit var etInstitution: EditText
    private lateinit var btnSave: Button

    // Originals to detect changes
    private var originalFullName: String = ""
    private var originalPhone: String = ""
    private var originalEmail: String? = null
    private var originalFieldOfStudy: String = ""
    private var originalInstitution: String = ""

    private var loading = false
        set(value) {
            field = value
            btnSave.isEnabled = !value && canSave()
            btnSave.alpha = if (value) 0.6f else 1f
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Bind
        btnBack        = findViewById(R.id.btnBack)
        tvHeaderTitle  = findViewById(R.id.tvHeaderTitle)
        etFullName     = findViewById(R.id.etFullName)
        etPhone        = findViewById(R.id.etPhone)
        etEmail        = findViewById(R.id.etEmail)
        etFieldOfStudy = findViewById(R.id.etFieldOfStudy)
        etInstitution  = findViewById(R.id.etInstitution)
        btnSave        = findViewById(R.id.btnSave)

        tvHeaderTitle.text = "Edit Profile"
        btnBack.setOnClickListener { finish() }

        // DB ref
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "No authenticated user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        usersRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)

        // Load current values
        loadProfile()

        // Enable save on edits
        val watcher = SimpleTextWatcher { btnSave.isEnabled = !loading && canSave() }
        etFullName.addTextChangedListener(watcher)
        etPhone.addTextChangedListener(watcher)
        etEmail.addTextChangedListener(watcher)
        etFieldOfStudy.addTextChangedListener(watcher)
        etInstitution.addTextChangedListener(watcher)

        btnSave.setOnClickListener { saveChanges() }
    }

    // -------------------- Load --------------------
    private fun loadProfile() {
        loading = true
        usersRef.get().addOnCompleteListener { t ->
            loading = false
            val user = auth.currentUser

            val snap = t.result
            val first   = snap?.child("firstName")?.getValue(String::class.java)?.trim().orEmpty()
            val sur     = snap?.child("surname")?.getValue(String::class.java)?.trim().orEmpty()
            val phone   = snap?.child("phoneNumber")?.getValue(String::class.java)?.trim().orEmpty()
            val dbEmail = snap?.child("email")?.getValue(String::class.java)?.trim()

            val fieldOfStudy = snap?.child("fieldOfStudy")?.getValue(String::class.java)?.trim().orEmpty()
            // prefer new key "institution", fall back to legacy "organisation"
            val institution = (snap?.child("institution")?.getValue(String::class.java)
                ?: snap?.child("organisation")?.getValue(String::class.java))?.trim().orEmpty()

            val full = buildFullName(first, sur).ifBlank { user?.displayName ?: "" }
            val finalEmail = dbEmail ?: user?.email ?: ""

            etFullName.setText(full)
            etPhone.setText(phone)
            etEmail.setText(finalEmail)
            etFieldOfStudy.setText(fieldOfStudy)
            etInstitution.setText(institution)

            // Originals
            originalFullName = full
            originalPhone = phone
            originalEmail = finalEmail
            originalFieldOfStudy = fieldOfStudy
            originalInstitution = institution

            btnSave.isEnabled = canSave()
        }
    }

    // -------------------- Save --------------------
    private fun saveChanges() {
        if (!canSave()) return

        // Hide keyboard
        currentFocus?.let { v ->
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
        }

        val fullName      = etFullName.text.toString().trim()
        val phone         = etPhone.text.toString().trim()
        val emailNew      = etEmail.text.toString().trim()
        val fieldOfStudy  = etFieldOfStudy.text.toString().trim()
        val institution   = etInstitution.text.toString().trim()

        // Validate required basics
        if (fullName.isBlank()) { etFullName.error = "Full name is required"; etFullName.requestFocus(); return }
        if (emailNew.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(emailNew).matches()) {
            etEmail.error = "Enter a valid email address"; etEmail.requestFocus(); return
        }
        if (phone.isNotBlank() && phone.length < 7) {
            etPhone.error = "Enter a valid phone number"; etPhone.requestFocus(); return
        }

        // Nothing changed?
        if (!hasChanges(fullName, phone, emailNew, fieldOfStudy, institution)) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        loading = true

        val (first, sur) = splitName(fullName)
        val user = auth.currentUser ?: run {
            loading = false
            Toast.makeText(this, "No authenticated user", Toast.LENGTH_SHORT).show()
            return
        }

        // DB updates only for actually changed fields
        val updates = hashMapOf<String, Any>()
        if (originalFullName != fullName) {
            updates["firstName"] = first
            updates["surname"] = sur
        }
        if (originalPhone != phone) {
            updates["phoneNumber"] = phone
        }
        if (!originalEmail.equals(emailNew, ignoreCase = true)) {
            updates["email"] = emailNew
        }
        if (originalFieldOfStudy != fieldOfStudy) {
            updates["fieldOfStudy"] = fieldOfStudy
        }
        if (originalInstitution != institution) {
            updates["institution"] = institution
        }
        updates["updatedAt"] = System.currentTimeMillis()

        val pushDbUpdate: () -> Unit = {
            if (updates.isEmpty()) {
                loading = false
                Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            }
            usersRef.updateChildren(updates).addOnCompleteListener { done ->
                loading = false
                if (done.isSuccessful) {
                    // Update originals
                    originalFullName = fullName
                    originalPhone = phone
                    originalEmail = emailNew
                    originalFieldOfStudy = fieldOfStudy
                    originalInstitution = institution
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        done.exception?.localizedMessage ?: "Failed to save profile",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // If email changed, update Auth first
        if (!originalEmail.equals(emailNew, ignoreCase = true)) {
            user.updateEmail(emailNew).addOnCompleteListener { res ->
                if (res.isSuccessful) {
                    pushDbUpdate()
                } else {
                    loading = false
                    val msg = res.exception?.localizedMessage
                        ?: "Failed to update email. You may need to re-authenticate."
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            pushDbUpdate()
        }
    }

    // -------------------- Helpers --------------------
    private fun canSave(): Boolean {
        val full = etFullName.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val emailOk = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val changed = hasChanges(
            full,
            etPhone.text?.toString()?.trim().orEmpty(),
            email,
            etFieldOfStudy.text?.toString()?.trim().orEmpty(),
            etInstitution.text?.toString()?.trim().orEmpty()
        )
        return !loading && full.isNotBlank() && emailOk && changed
    }

    private fun hasChanges(
        full: String,
        phone: String,
        email: String,
        fieldOfStudy: String,
        institution: String
    ): Boolean {
        val emailChanged = !originalEmail.equals(email, ignoreCase = true)
        return emailChanged ||
                originalFullName != full ||
                originalPhone != phone ||
                originalFieldOfStudy != fieldOfStudy ||
                originalInstitution != institution
    }

    private fun splitName(full: String): Pair<String, String> {
        val tokens = full.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return when {
            tokens.isEmpty() -> "" to ""
            tokens.size == 1 -> tokens[0] to ""
            else -> tokens.first() to tokens.drop(1).joinToString(" ")
        }
    }

    private fun buildFullName(first: String, sur: String): String =
        listOf(first, sur).filter { it.isNotBlank() }.joinToString(" ")

    private class SimpleTextWatcher(val onChange: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange() }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
