package com.example.citewise_mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

class PendingApprovalActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.database

    private lateinit var tvMessage: MaterialTextView
    private lateinit var btnReturnLogin: MaterialButton

    private var approvalListener: ValueEventListener? = null
    private var approvalRef: DatabaseReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_approval)

        // Views match your latest XML
        tvMessage = findViewById(R.id.tvPendingMessage)
        btnReturnLogin = findViewById(R.id.btnReturnLogin)

        // If user taps "Return to Login": sign out and go to LoginActivity
        btnReturnLogin.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // If there is no authenticated user, go to login immediately
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
    }

    override fun onStart() {
        super.onStart()

        val uid = auth.currentUser?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        approvalRef = db.reference.child("users").child(uid).child("isApproved")

        approvalListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val approved = snapshot.getValue(Boolean::class.java) == true
                if (approved) {
                    // Approved! Route to consultant dashboard.
                    startActivity(
                        Intent(
                            this@PendingApprovalActivity,
                            ConsultantDashboardActivity::class.java
                        )
                    )
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Keep user on pending screen; optionally log error if needed
            }
        }

        approvalListener?.let { listener ->
            approvalRef?.addValueEventListener(listener)
        }
    }

    override fun onStop() {
        super.onStop()
        approvalListener?.let { listener ->
            approvalRef?.removeEventListener(listener)
        }
        approvalListener = null
        approvalRef = null
    }
}
