package com.example.citewise_mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var edFirstName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var btnGoogle: ImageView
    private lateinit var btnFacebook: ImageView
    private lateinit var btnApple: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize UI elements
        edFirstName = findViewById(R.id.etFirstName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnSignUp)
        tvLogin = findViewById(R.id.tvLoginLink)
        btnGoogle = findViewById(R.id.btnGoogle)
        btnFacebook = findViewById(R.id.btnFacebook)
        btnApple = findViewById(R.id.btnApple)

        btnRegister.setOnClickListener {
            registerUser()
        }

        // Set click listeners for social login buttons
        btnGoogle.setOnClickListener {
            // Implement Google sign-in logic
            Toast.makeText(this, "Google sign-in clicked", Toast.LENGTH_SHORT).show()
        }

        btnFacebook.setOnClickListener {
            // Implement Facebook sign-in logic
            Toast.makeText(this, "Facebook sign-in clicked", Toast.LENGTH_SHORT).show()
        }

        btnApple.setOnClickListener {
            // Implement Apple sign-in logic
            Toast.makeText(this, "Apple sign-in clicked", Toast.LENGTH_SHORT).show()
        }

        setLoginTextView()
    }
    //Reference: Based on code from Android Knowledge (2024),
    //"Login and Signup using Firebase Realtime Database in Android Studio | Kotlin"
    //"https://www.youtube.com/watch?v=MhLkezKsHbY"
    private fun registerUser() {
        val firstName = edFirstName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        val hasNumber = Regex(".*[0-9].*").containsMatchIn(password)
        val hasSpecial = Regex(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*").containsMatchIn(password)

        // Validate input
        if(firstName.isEmpty()) {
            edFirstName.error = "First Name is required"
            edFirstName.requestFocus()
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email"
            etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            etPassword.requestFocus()
            return
        }

        if (password.length < 6 || !hasNumber || !hasSpecial) {
            etPassword.error = "Password must be at least 6 characters and contain a number and special character"
            etPassword.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.error = "Confirm your password"
            etConfirmPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return
        }

        btnRegister.isEnabled = false
        btnRegister.text = "Registering..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                btnRegister.isEnabled = true
                btnRegister.text = getString(R.string.register)

                if (task.isSuccessful) {
                    val user = auth.currentUser

                    user?.let {
                        val userRef = database.reference.child("users").child(it.uid)
                        val userData = hashMapOf(
                            "email" to email,
                            "createdAt" to System.currentTimeMillis()
                        )
                        userRef.setValue(userData)
                            .addOnSuccessListener {
                                // Clear any saved credentials in shared preferences
                                val sharedPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                                sharedPrefs.edit().clear().apply()

                                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()

                                // Pass flag to LoginActivity to indicate coming from registration
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.putExtra("FROM_REGISTRATION", true)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error saving user data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    val errorMessage = when (task.exception?.message) {
                        "The email address is already in use by another account." ->
                            "This email is already registered. Try logging in."
                        else -> "Registration failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    //Reference: Based on code from Burak Selcuk (2022),
    //"Spannable String Kotlin"
    //https://www.youtube.com/watch?v=W0liX18l4Tg
    //(Google 2019):
    private fun setLoginTextView() {
        val fullText = "Already have an account? Login"
        val spannableString = SpannableString(fullText)

        val loginStart = fullText.indexOf("Login")
        val loginEnd = loginStart + "Login".length

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@RegisterActivity, R.color.purple) // Your purple color
                ds.isUnderlineText = true // Optional: remove underline
            }
        }

        spannableString.setSpan(clickableSpan, loginStart, loginEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvLogin.text = spannableString
        tvLogin.movementMethod = LinkMovementMethod.getInstance()
        tvLogin.highlightColor = Color.TRANSPARENT
    }
}
//References
//Google. 2019. “Firebase Authentication Firebase”.
// <https://firebase.google.com/docs/auth>.
// [accessed 1 May 2025]
