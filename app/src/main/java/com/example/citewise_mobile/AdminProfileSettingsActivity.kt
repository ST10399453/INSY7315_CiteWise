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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class AdminProfileSettingsActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Toggle & sections
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var btnOverview: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var sectionOverview: View
    private lateinit var sectionSettings: View

    // Settings rows
    private lateinit var rowEditProfile: View
    private lateinit var rowChangePassword: View
    private lateinit var rowLanguage: View
    private lateinit var btnLogout: Button

    // Overview views (for Firestore stats binding)
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvStatRequests: TextView
    private lateinit var tvStatCompleted: TextView
    private lateinit var tvStatInProgress: TextView
    private lateinit var tvStatPending: TextView
    private lateinit var rowDept: View
    private lateinit var rowOrg: View
    private lateinit var rowRole: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shell (Chats pattern)
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate this screen into the shell container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        val page = layoutInflater.inflate(R.layout.activity_admin_profile_settings, baseContent, false)
        baseContent.addView(page)

        // Bottom nav
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_profile)

        // Views from the inflated page
        toggle          = page.findViewById(R.id.toggleSettings)
        btnOverview     = page.findViewById(R.id.btnOverview)
        btnSettings     = page.findViewById(R.id.btnProfileSettings)
        sectionOverview = page.findViewById(R.id.sectionOverview)
        sectionSettings = page.findViewById(R.id.sectionSettings)

        // Cache overview subviews we’ll update
        tvName           = page.findViewById(R.id.tvOverviewName)
        tvEmail          = page.findViewById(R.id.tvOverviewEmail)
        tvStatRequests   = page.findViewById(R.id.tvStatRequests)
        tvStatCompleted  = page.findViewById(R.id.tvStatCompleted)
        tvStatInProgress = page.findViewById(R.id.tvStatInProgress)
        tvStatPending    = page.findViewById(R.id.tvStatPending)
        rowDept          = page.findViewById(R.id.rowOverviewAcademic)     // “Field of Study”
        rowOrg           = page.findViewById(R.id.rowOverviewInstitution)  // “Institution”
        rowRole          = page.findViewById(R.id.rowOverviewRole)
        rowLanguage      = page.findViewById(R.id.rowOverviewLanguage)

        // Default to Overview tab
        toggle.post { toggle.check(btnOverview.id) }
        showTab("overview")
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnOverview        -> showTab("overview")
                R.id.btnProfileSettings -> showTab("settings")
            }
        }

        // Bind UI
        prepareOverviewLabels()
        bindOverviewProfile()      // RTDB /users/{uid}
        bindServiceReviewStats()   // Firestore /ServiceReviews
        initSettingsSection(page)
    }

    private fun showTab(which: String) {
        val isOverview = which == "overview"
        sectionOverview.isVisible = isOverview
        sectionSettings.isVisible = !isOverview
    }

    private fun prepareOverviewLabels() {
        rowDept.findViewById<TextView>(R.id.tvLabel).text = "Field of Study"
        rowOrg.findViewById<TextView>(R.id.tvLabel).text  = "Institution"
        rowRole.findViewById<TextView>(R.id.tvLabel).text = "Role"
        rowLanguage.findViewById<TextView>(R.id.tvLabel).text = "Language"

        // Safe defaults before data arrives
        tvName.text  = "Admin"
        tvEmail.text = auth.currentUser?.email ?: "admin@example.com"

        rowDept.findViewById<TextView>(R.id.tvValue).text = "—"
        rowOrg.findViewById<TextView>(R.id.tvValue).text = "CiteWise"
        rowRole.findViewById<TextView>(R.id.tvValue).text = "Admin"
        rowLanguage.findViewById<TextView>(R.id.tvValue).text = "English"

        tvStatRequests.text   = "0"
        tvStatCompleted.text  = "0"
        tvStatInProgress.text = "0"
        tvStatPending.text    = "0"
    }

    /** Pulls admin profile from RTDB `/users/{uid}` and binds name/email/role/fieldOfStudy/institution. */
    private fun bindOverviewProfile() {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { rtdb.reference.child("users").child(uid).get().await() }
                .onSuccess { snap ->
                    val firstName    = snap.child("firstName").getValue(String::class.java).orEmpty()
                    val surname      = snap.child("surname").getValue(String::class.java).orEmpty()
                    val email        = snap.child("email").getValue(String::class.java)
                        ?: auth.currentUser?.email
                    val roleRaw      = snap.child("role").getValue(String::class.java).orEmpty()
                    val fieldOfStudy = snap.child("fieldOfStudy").getValue(String::class.java).orEmpty()
                    // read institution, fall back to legacy "organisation" if present
                    val institution  = snap.child("institution").getValue(String::class.java)
                        ?: snap.child("organisation").getValue(String::class.java)

                    val name = listOf(firstName, surname).filter { it.isNotBlank() }.joinToString(" ")
                        .ifBlank { auth.currentUser?.displayName ?: "Admin" }
                    val role = roleRaw.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    }.ifBlank { "Admin" }

                    withContext(Dispatchers.Main) {
                        tvName.text = name
                        tvEmail.text = email ?: "admin@example.com"
                        rowDept.findViewById<TextView>(R.id.tvValue).text = fieldOfStudy.ifBlank { "—" }
                        rowOrg.findViewById<TextView>(R.id.tvValue).text = institution?.ifBlank { "CiteWise" } ?: "CiteWise"
                        rowRole.findViewById<TextView>(R.id.tvValue).text = role

                        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
                        val photoUrl = snap.child("photoUrl").getValue(String::class.java)
                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(this@AdminProfileSettingsActivity)
                                .load(photoUrl)
                                .placeholder(R.drawable.ic_user_avatar)
                                .circleCrop()
                                .into(ivAvatar)
                        } else {
                            ivAvatar.setImageDrawable(createInitialsDrawable(firstName, surname))
                        }
                    }
                }
                .onFailure {
                    // keep defaults; no crash
                }
        }

        // Optional row taps
        rowDept.setOnClickListener { }
        rowOrg.setOnClickListener { }
        rowRole.setOnClickListener { }
        rowLanguage.setOnClickListener { }
    }

    /**
     * Pulls counts from Firestore `ServiceReviews`:
     * - total
     * - completed (status == completed)
     * - inProgress (status == assigned OR in_progress)
     * - pending (status == pending OR submitted)
     */
    private fun bindServiceReviewStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                firestore.collection("ServiceReviews").get().await()
            }.onSuccess { snap ->
                var total = 0
                var completed = 0
                var assignedOrInProgress = 0
                var pendingOrSubmitted = 0

                for (doc in snap) {
                    total += 1
                    val status = (doc.get("status") as? String)?.lowercase(Locale.ROOT) ?: ""
                    when (status) {
                        "completed", "complete", "done" -> completed += 1
                        "assigned", "in_progress", "inprogress" -> assignedOrInProgress += 1
                        "pending", "submitted", "awaiting", "awaiting_review" -> pendingOrSubmitted += 1
                        else -> { /* ignore other states */ }
                    }
                }

                withContext(Dispatchers.Main) {
                    tvStatRequests.text   = total.toString()
                    tvStatCompleted.text  = completed.toString()
                    tvStatInProgress.text = assignedOrInProgress.toString()
                    tvStatPending.text    = pendingOrSubmitted.toString()
                }
            }.onFailure {
                // Leave defaults if Firestore fails
            }
        }
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

        rowEditProfile.safeClick { startActivity(Intent(this, EditProfileActivity::class.java)) }
        rowChangePassword.safeClick { startActivity(Intent(this, ChangePasswordActivity::class.java)) }

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

    private fun createInitialsDrawable(first: String, last: String, sizeDp: Int = 64): Drawable {
        val initials = listOf(first, last)
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
