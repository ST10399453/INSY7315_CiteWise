package com.example.citewise_mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

// NEW imports for service reviews
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository

class StudentProfileSettingsActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    // NEW: service reviews repo
    private val reviewsRepo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    // Toggle & sections
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnOverview: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var sectionOverview: View
    private lateinit var sectionSettings: View

    // Settings rows (containers)
    private lateinit var rowEditProfile: View
    private lateinit var rowChangePassword: View
    private lateinit var rowLanguage: View
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val page = layoutInflater.inflate(
            R.layout.activity_student_profile_settings,
            baseContent,
            false
        )
        baseContent.addView(page)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_profile)

        toggle          = page.findViewById(R.id.toggleSettings)
        btnOverview     = page.findViewById(R.id.btnOverview)
        btnSettings     = page.findViewById(R.id.btnProfileSettings)
        sectionOverview = page.findViewById(R.id.sectionOverview)
        sectionSettings = page.findViewById(R.id.sectionSettings)

        toggle.post { toggle.check(btnOverview.id) }
        showTab("overview")
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnOverview        -> showTab("overview")
                R.id.btnProfileSettings -> showTab("settings")
            }
        }

        bindOverview(page)          // RTDB + stats
        initSettingsSection(page)
    }

    private fun showTab(which: String) {
        val isOverview = which == "overview"
        sectionOverview.isVisible = isOverview
        sectionSettings.isVisible = !isOverview
    }

    /**
     * Populates the Overview using Firebase RTDB (profile)
     * and ServiceReviewsRepository (counts for this student).
     */
    private fun bindOverview(page: View) {
        val ivAvatar       = page.findViewById<ImageView>(R.id.ivAvatar)
        val tvName         = page.findViewById<TextView>(R.id.tvOverviewName)
        val tvEmail        = page.findViewById<TextView>(R.id.tvOverviewEmail)
        val tvReq          = page.findViewById<TextView>(R.id.tvStatRequests)
        val tvCompleted    = page.findViewById<TextView>(R.id.tvStatCompleted)
        val tvInProgress   = page.findViewById<TextView>(R.id.tvStatInProgress)
        val tvPending      = page.findViewById<TextView>(R.id.tvStatPending)

        val rowAcademic     = page.findViewById<View>(R.id.rowOverviewAcademic)
        val rowInstitution  = page.findViewById<View>(R.id.rowOverviewInstitution)
        val rowRole         = page.findViewById<View>(R.id.rowOverviewRole)
        val rowLanguage     = page.findViewById<View>(R.id.rowOverviewLanguage)

        // Labels
        rowAcademic.findViewById<TextView>(R.id.tvLabel).text = "Field of Study"
        rowInstitution.findViewById<TextView>(R.id.tvLabel).text = "Institution"
        rowRole.findViewById<TextView>(R.id.tvLabel).text = "Role"
        rowLanguage.findViewById<TextView>(R.id.tvLabel).text = "Language"

        // Defaults
        tvName.text = "Student"
        tvEmail.text = "you@example.com"
        tvReq.text = "0"
        tvCompleted.text = "0"
        tvInProgress.text = "0"
        tvPending.text = "0"
        rowAcademic.findViewById<TextView>(R.id.tvValue).text = "—"
        rowInstitution.findViewById<TextView>(R.id.tvValue).text = "—"
        rowRole.findViewById<TextView>(R.id.tvValue).text = "Student"
        rowLanguage.findViewById<TextView>(R.id.tvValue).text = "English"

        val uid = auth.currentUser?.uid ?: return

        // 1) Profile from RTDB
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { db.reference.child("users").child(uid).get().await() }
                .onSuccess { snap ->
                    val firstName    = snap.child("firstName").getValue(String::class.java).orEmpty()
                    val surname      = snap.child("surname").getValue(String::class.java).orEmpty()
                    val email        = snap.child("email").getValue(String::class.java)
                        ?: auth.currentUser?.email
                    val role         = snap.child("role").getValue(String::class.java).orEmpty()
                    val fieldOfStudy = snap.child("fieldOfStudy").getValue(String::class.java).orEmpty()
                    // NEW: institution (prefers new key, falls back to legacy "organisation")
                    val institution  = (snap.child("institution").getValue(String::class.java)
                        ?: snap.child("organisation").getValue(String::class.java))
                        ?.trim()
                        .orEmpty()

                    val photoUrl = snap.child("photoUrl").getValue(String::class.java)

                    val name = listOf(firstName, surname)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { auth.currentUser?.displayName ?: "Student" }

                    withContext(Dispatchers.Main) {
                        tvName.text = name
                        tvEmail.text = email ?: "you@example.com"
                        rowAcademic.findViewById<TextView>(R.id.tvValue).text =
                            fieldOfStudy.ifBlank { "—" }
                        rowInstitution.findViewById<TextView>(R.id.tvValue).text =
                            institution.ifBlank { "—" }
                        rowRole.findViewById<TextView>(R.id.tvValue).text =
                            role.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                            }.ifBlank { "Student" }

                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(this@StudentProfileSettingsActivity)
                                .load(photoUrl)
                                .placeholder(R.drawable.ic_user_avatar)
                                .circleCrop()
                                .into(ivAvatar)
                        } else {
                            ivAvatar.setImageDrawable(createInitialsDrawable(name))
                        }

                    }
                }
        }

        // 2) STATS from Service Reviews (for this student uid)
        lifecycleScope.launch(Dispatchers.IO) {
            val all = when (val res = reviewsRepo.listGeneralRequests(userId = uid)) {
                is NetResult.Ok  -> res.data.orEmpty()
                is NetResult.Err -> emptyList()
            }

            val total = all.size
            val completed = all.count { it.status.equalsCI("completed") }
            val assigned  = all.count { it.status.equalsCI("assigned") }
            val pendingOrSubmitted = all.count {
                it.status.equalsCI("pending") || it.status.equalsCI("submitted")
            }

            withContext(Dispatchers.Main) {
                tvReq.text = total.toString()
                tvCompleted.text = completed.toString()
                tvInProgress.text = assigned.toString()
                tvPending.text = pendingOrSubmitted.toString()
            }
        }

        // Optional taps
        rowAcademic.setOnClickListener { }
        rowInstitution.setOnClickListener { }
        rowRole.setOnClickListener { }
        rowLanguage.setOnClickListener { }
    }

    private fun initSettingsSection(page: View) {
        rowEditProfile    = page.findViewById(R.id.rowEditProfile)
        rowChangePassword = page.findViewById(R.id.rowChangePassword)
        rowLanguage       = page.findViewById(R.id.rowLanguage)
        btnLogout         = page.findViewById(R.id.btnLogout)

        rowEditProfile.findViewById<TextView>(R.id.rowLabel).text = "Edit profile"
        rowChangePassword.findViewById<TextView>(R.id.rowLabel).text = "Change password"

        val rowPush = page.findViewById<View>(R.id.rowPushNotifications).apply {
            findViewById<TextView>(R.id.rowLabel).text = "Push notifications"
        }
        rowPush.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = true
            setOnCheckedChangeListener { _, _ -> }
        }

        val rowApp = page.findViewById<View>(R.id.rowAppNotifications).apply {
            findViewById<TextView>(R.id.rowLabel).text = "App notifications"
        }
        rowApp.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = false
            setOnCheckedChangeListener { _, _ -> }
        }

        val rowEmail = page.findViewById<View>(R.id.rowEmailNotifications).apply {
            findViewById<TextView>(R.id.rowLabel).text = "Email notifications"
        }
        rowEmail.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = false
            setOnCheckedChangeListener { _, _ -> }
        }

        rowLanguage.findViewById<TextView>(R.id.tvLabel).text = "Language"
        rowLanguage.findViewById<TextView>(R.id.tvValue).text = "English"
        rowLanguage.setOnClickListener { }

        makeRowClickable(rowEditProfile)
        makeRowClickable(rowChangePassword)

        rowEditProfile.safeClick {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        rowChangePassword.safeClick {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun makeRowClickable(row: View) {
        if (!row.isClickable) row.isClickable = true
        if (!row.isFocusable) row.isFocusable = true
        if (row.foreground == null) {
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = obtainStyledAttributes(attrs)
            val ripple = ta.getDrawable(0)
            ta.recycle()
            row.foreground = ripple
        }
    }

    private fun View.safeClick(intervalMs: Long = 600L, onSafeClick: (View) -> Unit) {
        var lastClick = 0L
        setOnClickListener { v ->
            val now = System.currentTimeMillis()
            if (now - lastClick > intervalMs) {
                lastClick = now
                onSafeClick(v)
            }
        }
    }

    private fun createInitialsDrawable(name: String, sizeDp: Int = 64): Drawable {
        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .map { it.firstOrNull()?.uppercaseChar() ?: "" }
            .take(2)
            .joinToString("")

        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background circle
        val paint = Paint().apply {
            color = Color.parseColor("#0027B2")
            isAntiAlias = true
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Draw initials
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = sizePx / 2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val yPos = (canvas.height / 2 - (textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(initials, sizePx / 2f, yPos, textPaint)

        return BitmapDrawable(resources, bitmap)
    }

}

/** Case-insensitive equals helper for nullable strings */
private fun String?.equalsCI(value: String): Boolean =
    this != null && this.equals(value, ignoreCase = true)

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

