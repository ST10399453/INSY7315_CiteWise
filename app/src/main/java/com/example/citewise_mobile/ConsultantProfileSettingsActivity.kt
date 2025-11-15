package com.example.citewise_mobile

import android.content.Intent
import android.graphics.*
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
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository
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

class ConsultantProfileSettingsActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance() }
    private val repo by lazy { ServiceReviewsRepository(RetrofitInstance.api) }

    // Toggle & sections
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnOverview: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var sectionOverview: View
    private lateinit var sectionSettings: View

    // Overview header
    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView

    // Overview stats
    private lateinit var tvStatRequests: TextView
    private lateinit var tvStatCompleted: TextView
    private lateinit var tvStatInProgress: TextView
    private lateinit var tvStatPending: TextView

    // Overview value rows (item_row_value)
    private lateinit var rowOverviewAcademic: View     // tvLabel, tvValue  (Specialisation)
    private lateinit var rowOverviewInstitution: View  // tvLabel, tvValue
    private lateinit var rowOverviewRole: View         // tvLabel, tvValue
    private lateinit var rowOverviewLanguage: View     // tvLabel, tvValue

    // Settings rows
    private lateinit var rowEditProfile: View          // item_row_nav: rowLabel
    private lateinit var rowChangePassword: View       // item_row_nav: rowLabel
    private lateinit var rowLanguage: View             // item_row_value: tvLabel, tvValue
    private lateinit var rowPushNotifications: View    // item_row_switch: rowLabel, switchView
    private lateinit var rowAppNotifications: View     // item_row_switch: rowLabel, switchView
    private lateinit var rowEmailNotifications: View   // item_row_switch: rowLabel, switchView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Base shell + page
        setContentView(R.layout.activity_base)
        val base = findViewById<ViewGroup>(R.id.baseContent)
        layoutInflater.inflate(R.layout.activity_consultant_profile_settings, base, true)

        setupBottomNav(findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_profile)

        bindViews()
        prepareOverviewLabelsAndDefaults()
        setupToggle()
        setupSettingsActions()
        initSettingsRows()
        loadOverview()
    }

    private fun bindViews() {
        // Toggle & sections
        toggleGroup      = findViewById(R.id.toggleSettings)
        btnOverview      = findViewById(R.id.btnOverview)
        btnSettings      = findViewById(R.id.btnProfileSettings)
        sectionOverview  = findViewById(R.id.sectionOverview)
        sectionSettings  = findViewById(R.id.sectionSettings)

        // Header
        ivAvatar         = findViewById(R.id.ivAvatar)
        tvName           = findViewById(R.id.tvOverviewName)
        tvEmail          = findViewById(R.id.tvOverviewEmail)

        // Stats
        tvStatRequests   = findViewById(R.id.tvStatRequests)
        tvStatCompleted  = findViewById(R.id.tvStatCompleted)
        tvStatInProgress = findViewById(R.id.tvStatInProgress)
        tvStatPending    = findViewById(R.id.tvStatPending)

        // Overview rows
        rowOverviewAcademic    = findViewById(R.id.rowOverviewAcademic)
        rowOverviewInstitution = findViewById(R.id.rowOverviewInstitution)
        rowOverviewRole        = findViewById(R.id.rowOverviewRole)
        rowOverviewLanguage    = findViewById(R.id.rowOverviewLanguage)

        // Settings rows
        rowEditProfile         = findViewById(R.id.rowEditProfile)
        rowChangePassword      = findViewById(R.id.rowChangePassword)
        rowLanguage            = findViewById(R.id.rowLanguage)
        rowPushNotifications   = findViewById(R.id.rowPushNotifications)
        rowAppNotifications    = findViewById(R.id.rowAppNotifications)
        rowEmailNotifications  = findViewById(R.id.rowEmailNotifications)
        btnLogout              = findViewById(R.id.btnLogout)
    }

    private fun prepareOverviewLabelsAndDefaults() {
        // Labels (match admin look-and-feel)
        rowOverviewAcademic.findViewById<TextView>(R.id.tvLabel).text    = "Specialisation"
        rowOverviewInstitution.findViewById<TextView>(R.id.tvLabel).text = "Institution"
        rowOverviewRole.findViewById<TextView>(R.id.tvLabel).text        = "Role"
        rowOverviewLanguage.findViewById<TextView>(R.id.tvLabel).text    = "Language"

        // Defaults before data arrives
        tvName.text  = "Consultant"
        tvEmail.text = auth.currentUser?.email ?: "you@example.com"

        rowOverviewAcademic.findViewById<TextView>(R.id.tvValue).text    = "—"
        rowOverviewInstitution.findViewById<TextView>(R.id.tvValue).text = "—"
        rowOverviewRole.findViewById<TextView>(R.id.tvValue).text        = "Consultant"
        rowOverviewLanguage.findViewById<TextView>(R.id.tvValue).text    = "English"

        tvStatRequests.text   = "0"
        tvStatCompleted.text  = "0"
        tvStatInProgress.text = "0"
        tvStatPending.text    = "0"

        // If XML hid these, ensure visible
        rowOverviewRole.isVisible = true
        rowOverviewLanguage.isVisible = true
    }

    private fun setupToggle() {
        toggleGroup.post { toggleGroup.check(btnOverview.id) }
        showTab(true)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showTab(checkedId == btnOverview.id)
        }
    }

    private fun showTab(isOverview: Boolean) {
        sectionOverview.isVisible = isOverview
        sectionSettings.isVisible = !isOverview
    }

    private fun setupSettingsActions() {
        makeRowClickable(rowEditProfile)
        makeRowClickable(rowChangePassword)

        rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        rowChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun initSettingsRows() {
        // item_row_nav
        rowEditProfile.findViewById<TextView>(R.id.rowLabel).text = "Edit profile"
        rowChangePassword.findViewById<TextView>(R.id.rowLabel).text = "Change password"

        // item_row_switch labels & default states
        rowPushNotifications.findViewById<TextView>(R.id.rowLabel).text = "Push notifications"
        rowAppNotifications.findViewById<TextView>(R.id.rowLabel).text  = "App notifications"
        rowEmailNotifications.findViewById<TextView>(R.id.rowLabel).text= "Email notifications"

        rowPushNotifications.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = true
            setOnCheckedChangeListener { _, _ -> /* TODO: persist */ }
        }
        rowAppNotifications.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = false
            setOnCheckedChangeListener { _, _ -> /* TODO: persist */ }
        }
        rowEmailNotifications.findViewById<MaterialSwitch>(R.id.switchView).apply {
            isChecked = false
            setOnCheckedChangeListener { _, _ -> /* TODO: persist */ }
        }

        // Language row (item_row_value)
        rowLanguage.findViewById<TextView>(R.id.tvLabel).text = "Language"
        rowLanguage.findViewById<TextView>(R.id.tvValue).text = "English"
        rowLanguage.setOnClickListener { /* TODO: language picker */ }
    }

    private fun loadOverview() {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            // User snapshot
            val snap = runCatching { rtdb.reference.child("users").child(uid).get().await() }.getOrNull()

            val first = snap?.child("firstName")?.getValue(String::class.java).orEmpty()
            val last  = snap?.child("surname")?.getValue(String::class.java).orEmpty()
            val email = auth.currentUser?.email.orEmpty()
            val photoUrl = snap?.child("photoUrl")?.getValue(String::class.java)

            // Consultant fields
            val specialisation = snap?.child("specialisation")?.getValue(String::class.java).orEmpty()
            val institution = snap?.child("institution")?.getValue(String::class.java)
                ?: snap?.child("organisation")?.getValue(String::class.java)
            val roleRaw = snap?.child("role")?.getValue(String::class.java).orEmpty()
            val language = snap?.child("language")?.getValue(String::class.java).orEmpty()

            // Try to fetch everything in one call; if not supported, fall back to multi-calls.
            val allRequests = when (val r = repo.listGeneralRequests(consultantId = uid, status = null)) {
                is NetResult.Ok -> r.data ?: emptyList()
                else -> {
                    val s = listOf(
                        "completed", "complete", "done",
                        "in_progress", "inprogress", "assigned",
                        "pending", "submitted", "awaiting", "awaiting_review"
                    )
                    s.flatMap { st ->
                        (repo.listGeneralRequests(uid, st) as? NetResult.Ok)?.data ?: emptyList()
                    }.distinctBy { it.id } // adjust if your DTO uses a different id field
                }
            }

            // Classify statuses robustly
            var completed = 0
            var inProgress = 0
            var pending = 0
            for (t in allRequests) {
                val s = (t.status ?: "").trim().lowercase(Locale.ROOT)
                when {
                    s in setOf("completed", "complete", "done") -> completed++
                    s in setOf("in_progress", "inprogress", "assigned") -> inProgress++
                    s in setOf("pending", "submitted", "awaiting", "awaiting_review") -> pending++
                    else -> { /* ignore/other */ }
                }
            }
            val total = allRequests.size

            val name = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Consultant" }
            val role = roleRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                .ifBlank { "Consultant" }

            withContext(Dispatchers.Main) {
                // Header
                tvName.text = name
                tvEmail.text = email.ifBlank { "you@example.com" }
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this@ConsultantProfileSettingsActivity)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_user_avatar)
                        .circleCrop()
                        .into(ivAvatar)
                } else {
                    ivAvatar.setImageDrawable(createInitialsDrawable(first, last))
                }

                // Stats
                tvStatRequests.text   = total.toString()
                tvStatCompleted.text  = completed.toString()
                tvStatInProgress.text = inProgress.toString()
                tvStatPending.text    = pending.toString()

                // Value rows
                rowOverviewAcademic.findViewById<TextView>(R.id.tvValue).text =
                    specialisation.ifBlank { "—" }
                rowOverviewInstitution.findViewById<TextView>(R.id.tvValue).text =
                    institution?.ifBlank { "—" } ?: "—"
                rowOverviewRole.findViewById<TextView>(R.id.tvValue).text =
                    role
                rowOverviewLanguage.findViewById<TextView>(R.id.tvValue).text =
                    language.ifBlank { "English" }
            }
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

    private fun createInitialsDrawable(first: String, last: String, sizeDp: Int = 64): Drawable {
        val initials = listOf(first, last)
            .filter { it.isNotBlank() }
            .map { it.first().uppercaseChar() }
            .joinToString("")

        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.parseColor("#0027B2")
            isAntiAlias = true
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

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

