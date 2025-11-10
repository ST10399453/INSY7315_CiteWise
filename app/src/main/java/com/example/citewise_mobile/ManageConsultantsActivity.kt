package com.example.citewise_mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.UnassignedConsultantsResponse
import com.example.citewise_mobile.databinding.ActivityManageConsultantsBinding
import com.example.citewise_mobile.api.RetrofitInstance
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class ManageConsultantsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageConsultantsBinding

    // Firestore (only for pending consultant approvals)
    private val db by lazy { FirebaseFirestore.getInstance() }

    // REST API
    private val api by lazy { RetrofitInstance.api }

    // ── In-memory state
    private val pendingConsultants = mutableListOf<Consultant>()   // approvals
    private val pendingAssignments = mutableListOf<Assignment>()   // unassigned requests (from API)
    private val unassignedConsultants = mutableListOf<Consultant>()// from API

    // ── Adapters
    private lateinit var pendingConsAdapter: PendingConsultantAdapter
    private lateinit var assignmentAdapter: AssignmentAdapter
    private lateinit var unassignedAdapter: ConsultantRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityManageConsultantsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupLists()
        setupPriorityChips()
        setupSearch()

        // Load data
        fetchPendingConsultantsFromFirestore() // approvals
        fetchPendingAssignmentsFromApi()       // unassigned requests
        fetchUnassignedConsultantsFromApi()    // unassigned consultants
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI wiring
    // ─────────────────────────────────────────────────────────────────────
    private fun setupLists() {
        // Pending consultants (approvals)
        pendingConsAdapter = PendingConsultantAdapter(
            data = pendingConsultants,
            onApprove = { /* TODO: approve in Firestore */ },
            onReject  = { /* TODO: reject in Firestore  */ }
        )
        binding.rvPendingConsultants.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = pendingConsAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Pending assignments (unassigned) from API
        assignmentAdapter = AssignmentAdapter(pendingAssignments) { /* on click */ }
        binding.rvPendingAssignments.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = assignmentAdapter
        }

        // Unassigned consultants (from API) with search filter
        unassignedAdapter = ConsultantRowAdapter(unassignedConsultants) { /* on click */ }
        binding.rvAssignedConsultants.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = unassignedAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupPriorityChips() {
        binding.groupPriority.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val filter = when (checkedId) {
                binding.btnFilterUrgent.id -> PriorityFilter.HIGH
                binding.btnFilterMedium.id -> PriorityFilter.MEDIUM
                binding.btnFilterLow.id    -> PriorityFilter.LOW
                else                       -> PriorityFilter.ALL
            }
            assignmentAdapter.setPriorityFilter(filter)
        }
        if (binding.groupPriority.checkedButtonId == View.NO_ID) {
            binding.groupPriority.check(binding.btnFilterAll.id)
        }
    }

    private fun setupSearch() {
        binding.etSearchConsultants.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                unassignedAdapter.filter = s?.toString().orEmpty()
            }
        })
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firestore (only for pending consultant approvals)
    // ─────────────────────────────────────────────────────────────────────
    private fun fetchPendingConsultantsFromFirestore() {
        db.collection(COL_USERS)
            .whereIn(F_ROLE, listOf(ROLE_PENDING, ROLE_PENDING_CONSULTANT))
            .orderBy(F_NAME, Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snap ->
                pendingConsultants.clear()
                for (d in snap.documents) {
                    pendingConsultants += Consultant(
                        uid = d.getString(F_UID) ?: d.id,
                        name = d.getString(F_NAME) ?: "(no name)",
                        email = d.getString(F_EMAIL).orEmpty(),
                        specialty = d.getString(F_SPECIALTY).orEmpty()
                    )
                }
                pendingConsAdapter.notifyDataSetChanged()
                toggleEmptyState(
                    empty = pendingConsultants.isEmpty(),
                    list = binding.rvPendingConsultants,
                    placeholder = null // keep optional-safe (no placeholder view required)
                )
            }
            .addOnFailureListener {
                toggleEmptyState(empty = true, list = binding.rvPendingConsultants, placeholder = null)
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    // REST API fetches
    // ─────────────────────────────────────────────────────────────────────
    /** GET /requests/pending-assignments */
    private fun fetchPendingAssignmentsFromApi() {
        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) { api.listPendingAssignments() }
            if (resp.isSuccessful) {
                val list = resp.body().orEmpty()
                pendingAssignments.clear()
                pendingAssignments += list.map(::dtoToAssignment)
                assignmentAdapter.resetBase(pendingAssignments)
                toggleEmptyState(
                    empty = pendingAssignments.isEmpty(),
                    list = binding.rvPendingAssignments,
                    placeholder = null
                )
            } else {
                // Optional fallback to Firestore if your API is unavailable:
                fetchPendingAssignmentsFallbackFirestore()
            }
        }
    }

    // Optional fallback to the old Firestore path
    private fun fetchPendingAssignmentsFallbackFirestore() {
        db.collection(COL_REVIEWS)
            .whereEqualTo(F_CONSULTANT_ID, null)
            .orderBy(F_CREATED_AT, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                pendingAssignments.clear()
                for (d in snap.documents) pendingAssignments += d.toAssignmentFirestore()
                assignmentAdapter.resetBase(pendingAssignments)
                toggleEmptyState(
                    empty = pendingAssignments.isEmpty(),
                    list = binding.rvPendingAssignments,
                    placeholder = null
                )
            }
    }

    /** GET /consultants/unassigned */
    private fun fetchUnassignedConsultantsFromApi() {
        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) { api.listUnassignedConsultants() }
            if (resp.isSuccessful) {
                val payload: UnassignedConsultantsResponse? = resp.body()
                unassignedConsultants.clear()
                payload?.items.orEmpty().forEach { c ->
                    unassignedConsultants += Consultant(
                        uid = c.uid,
                        name = c.name,
                        email = c.email,
                        specialty = c.specialty.orEmpty()
                    )
                }
                unassignedAdapter.reset()
                toggleEmptyState(
                    empty = unassignedConsultants.isEmpty(),
                    list = binding.rvAssignedConsultants,
                    placeholder = null
                )
            } else {
                // If the API fails, just show empty (or add your own fallback)
                toggleEmptyState(
                    empty = true,
                    list = binding.rvAssignedConsultants,
                    placeholder = null
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mapping
    // ─────────────────────────────────────────────────────────────────────
    private fun dtoToAssignment(d: ServiceRequestDto): Assignment {
        val pr = when (d.priority ?: ServicePriority.MEDIUM) {
            ServicePriority.HIGH   -> Priority.HIGH
            ServicePriority.MEDIUM -> Priority.MEDIUM
            ServicePriority.LOW    -> Priority.LOW
        }
        val titleCandidate = d.customName
            ?: d.originalFileName
            ?: d.description
            ?: d.serviceType?.name
            ?: "Request"

        return Assignment(
            id = d.id.orEmpty(),
            serviceType = d.serviceType?.let { prettyCategory(it) },
            customName = d.customName,
            originalFileName = d.originalFileName,
            description = d.description,
            status = d.status,
            priority = pr,
            createdAt = d.createdAt?.epochMillis,
            deadline = d.deadline?.epochMillis
        ).copy(titleOverride = titleCandidate)
    }

    private fun prettyCategory(t: ServiceType): String = when (t) {
        ServiceType.PROOFREADING_EDITING          -> "Proofreading & Editing"
        ServiceType.FORMATTING_REFERENCING        -> "Formatting & Referencing"
        ServiceType.DATA_ANALYSIS_SUPPORT         -> "Data Analysis Support"
        ServiceType.RESEARCH_METHODOLOGY_COACHING -> "Research/Methodology Coaching"
        ServiceType.TRANSLATION                   -> "Translation"
        ServiceType.OTHER                         -> "Other"
    }

    // Old Firestore mapping for fallback only
    private fun com.google.firebase.firestore.DocumentSnapshot.toAssignmentFirestore(): Assignment {
        val pr = when ((getString(F_PRIORITY) ?: "LOW").uppercase(Locale.US)) {
            "HIGH", "URGENT" -> Priority.HIGH
            "MEDIUM"         -> Priority.MEDIUM
            else             -> Priority.LOW
        }
        val created  = (get(F_CREATED_AT) as? Number)?.toLong()
        val deadline = (get(F_DEADLINE) as? Number)?.toLong()
        val svcRaw   = getString(F_SERVICE_TYPE) ?: getString("service_type")

        val titleCandidate = getString(F_CUSTOM_NAME)
            ?: getString(F_ORIGINAL_FILE_NAME)
            ?: getString(F_DESCRIPTION)
            ?: svcRaw
            ?: "Request"

        return Assignment(
            id = id,
            serviceType      = svcRaw,
            customName       = getString(F_CUSTOM_NAME),
            originalFileName = getString(F_ORIGINAL_FILE_NAME),
            description      = getString(F_DESCRIPTION),
            status           = getString(F_STATUS),
            priority         = pr,
            createdAt        = created,
            deadline         = deadline
        ).copy(titleOverride = titleCandidate)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────
    private fun toggleEmptyState(empty: Boolean, list: View, placeholder: View?) {
        placeholder?.visibility = if (empty) View.VISIBLE else View.GONE
        list.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────
    // Models
    // ─────────────────────────────────────────────────────────────────────
    data class Consultant(
        val uid: String,
        val name: String,
        val email: String,
        val specialty: String
    )

    enum class Priority { LOW, MEDIUM, HIGH }

    data class Assignment(
        val id: String,
        val serviceType: String?,
        val customName: String?,
        val originalFileName: String?,
        val description: String?,
        val status: String?,
        val priority: Priority,
        val createdAt: Long?,
        val deadline: Long?,
        // internal: lets us keep the best title without recomputing in adapter
        val titleOverride: String? = null
    ) {
        fun title(): String =
            titleOverride ?: when {
                !customName.isNullOrBlank()       -> customName
                !originalFileName.isNullOrBlank() -> originalFileName
                !description.isNullOrBlank()      -> description
                !serviceType.isNullOrBlank()      -> serviceType
                else                              -> "Request"
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Adapters
    // ─────────────────────────────────────────────────────────────────────
    private class PendingConsultantAdapter(
        private val data: List<Consultant>,
        private val onApprove: (Consultant) -> Unit,
        private val onReject: (Consultant) -> Unit
    ) : RecyclerView.Adapter<PendingConsultantAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val initials: TextView = v.findViewById(R.id.tvInitials)
            val name: TextView     = v.findViewById(R.id.tvName)
            val email: TextView    = v.findViewById(R.id.tvEmail)
            val btnApprove: View   = v.findViewById(R.id.btnApprove)
            val btnReject: View    = v.findViewById(R.id.btnReject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_consultant, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val c = data[position]
            h.initials.text = initialsOf(c.name)
            h.name.text = c.name
            h.email.text = c.email
            h.btnApprove.setOnClickListener { onApprove(c) }
            h.btnReject.setOnClickListener { onReject(c) }
        }

        override fun getItemCount(): Int = data.size

        private fun initialsOf(name: String): String =
            name.trim()
                .split(Regex("\\s+"))
                .take(2)
                .map { it.firstOrNull()?.uppercaseChar() ?: ' ' }
                .joinToString("")
    }

    private enum class PriorityFilter { ALL, LOW, MEDIUM, HIGH }

    private class AssignmentAdapter(
        private val base: MutableList<Assignment>,
        private val onClick: (Assignment) -> Unit
    ) : RecyclerView.Adapter<AssignmentAdapter.VH>() {

        private val visible = mutableListOf<Assignment>()
        private var filter: PriorityFilter = PriorityFilter.ALL
        private val dateFmt = DATE_FMT

        init { resetBase(base) }

        fun resetBase(newItems: List<Assignment>) {
            base.clear()
            base.addAll(newItems)
            applyFilter()
        }

        fun setPriorityFilter(f: PriorityFilter) {
            filter = f
            applyFilter()
        }

        private fun applyFilter() {
            visible.clear()
            visible += when (filter) {
                PriorityFilter.ALL    -> base
                PriorityFilter.LOW    -> base.filter { it.priority == Priority.LOW }
                PriorityFilter.MEDIUM -> base.filter { it.priority == Priority.MEDIUM }
                PriorityFilter.HIGH   -> base.filter { it.priority == Priority.HIGH }
            }
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card: MaterialCardView   = v.findViewById(R.id.taskCard)
            val tvCategory: TextView     = v.findViewById(R.id.tvCategory)
            val tvPriority: TextView     = v.findViewById(R.id.tvPriority)
            val tvServiceTitle: TextView = v.findViewById(R.id.tvServiceTitle)
            val tvSubmittedDate: TextView= v.findViewById(R.id.tvSubmittedDate)
            val tvStatusLabel: TextView  = v.findViewById(R.id.tvStatusLabel)
            val tvDeadline: TextView     = v.findViewById(R.id.tvDeadline)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_service_review, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = visible[position]
            h.tvCategory.text = item.serviceType ?: "Other"
            h.tvServiceTitle.text = item.title()
            h.tvStatusLabel.text = item.status
                ?.replace('_', ' ')
                ?.lowercase()
                ?.replaceFirstChar { it.titlecase() }
                ?: "Pending"
            h.tvSubmittedDate.text = "Submitted: ${dateFmt.safe(item.createdAt)}"

            val deadlineText = dateFmt.safe(item.deadline)
            h.tvDeadline.visibility = if (deadlineText == "—") View.GONE else View.VISIBLE
            h.tvDeadline.text = "Deadline: $deadlineText"

            val colorRes = when (item.priority) {
                Priority.HIGH   -> R.color.priority_High
                Priority.MEDIUM -> R.color.priority_Medium
                Priority.LOW    -> R.color.priority_Low
            }
            h.tvPriority.setTextColor(h.itemView.context.getColor(colorRes))

            h.card.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = visible.size

        private fun SimpleDateFormat.safe(ts: Long?): String =
            ts?.let { format(Date(it)) } ?: "—"
    }

    private class ConsultantRowAdapter(
        private val all: MutableList<Consultant>,
        private val onClick: (Consultant) -> Unit
    ) : RecyclerView.Adapter<ConsultantRowAdapter.VH>() {

        private val visible = mutableListOf<Consultant>()

        var filter: String = ""
            set(value) {
                field = value
                apply()
            }

        init { reset() }

        fun reset() {
            visible.clear()
            visible.addAll(all)
            notifyDataSetChanged()
        }

        private fun apply() {
            val f = filter.trim().lowercase()
            visible.clear()
            if (f.isEmpty()) {
                visible.addAll(all)
            } else {
                visible.addAll(
                    all.filter { it.name.lowercase().contains(f) || it.email.lowercase().contains(f) }
                )
            }
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvInitials: TextView  = v.findViewById(R.id.tvAvatarInitials)
            val tvName: TextView      = v.findViewById(R.id.tvConsultantName)
            val tvEmail: TextView     = v.findViewById(R.id.tvConsultantEmail)
            val tvSpecialty: TextView = v.findViewById(R.id.tvSpecialty)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_consultant_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val c = visible[position]
            h.tvInitials.text = initialsOf(c.name)
            h.tvName.text = c.name
            h.tvEmail.text = c.email
            h.tvSpecialty.text = if (c.specialty.isBlank()) "General" else c.specialty
            h.itemView.setOnClickListener { onClick(c) }
        }

        override fun getItemCount(): Int = visible.size

        private fun initialsOf(name: String): String =
            name.trim()
                .split(Regex("\\s+"))
                .take(2)
                .map { it.firstOrNull()?.uppercaseChar() ?: ' ' }
                .joinToString("")
    }

    companion object {
        // Firestore Collections (only for approvals)
        private const val COL_USERS   = "users"
        private const val COL_REVIEWS = "ServiceReviews"

        // User fields
        private const val F_UID       = "uid"
        private const val F_NAME      = "name"
        private const val F_EMAIL     = "email"
        private const val F_ROLE      = "role"
        private const val F_SPECIALTY = "specialty"

        // Review fields (Firestore fallback)
        private const val F_CONSULTANT_ID      = "consultantId"
        private const val F_CREATED_AT         = "createdAt"
        private const val F_DEADLINE           = "deadline"
        private const val F_PRIORITY           = "priority"
        private const val F_SERVICE_TYPE       = "serviceType"
        private const val F_CUSTOM_NAME        = "customName"
        private const val F_ORIGINAL_FILE_NAME = "originalFileName"
        private const val F_DESCRIPTION        = "description"
        private const val F_STATUS             = "status"

        // Roles
        private const val ROLE_PENDING            = "pending"
        private const val ROLE_PENDING_CONSULTANT = "pending_consultant"

        // Date format
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }
}
