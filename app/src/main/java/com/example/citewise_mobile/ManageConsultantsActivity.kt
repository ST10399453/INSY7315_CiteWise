package com.example.citewise_mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.databinding.ActivityManageConsultantsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class ManageConsultantsActivity : BaseActivity() {

    private lateinit var binding: ActivityManageConsultantsBinding

    // Firebase instances
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val rtdb by lazy { FirebaseDatabase.getInstance() }

    // Firebase refs
    private var usersRef: DatabaseReference? = null
    private var usersListener: ValueEventListener? = null

    // Data lists
    private val pendingConsultants = mutableListOf<Consultant>()
    private val pendingAssignments = mutableListOf<Assignment>()
    private val unassignedConsultants = mutableListOf<Consultant>()
    private val assignedConsultants = mutableListOf<Consultant>() // NEW
    private val allReviews = mutableListOf<Assignment>()
    private val allApprovedUsers = mutableMapOf<String, Consultant>()

    // Adapters
    private lateinit var pendingConsAdapter: PendingConsultantAdapter
    private lateinit var assignmentAdapter: AssignmentAdapter
    private lateinit var unassignedAdapter: ConsultantRowAdapter
    private lateinit var assignedAdapter: ConsultantRowAdapter // NEW

    private var isAdmin: Boolean = false
    private var showingUnassigned = true // NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        binding = ActivityManageConsultantsBinding.inflate(layoutInflater, baseContent, true)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, selectedItemId = R.id.nav_manage_consultants)

        setupLists()
        setupPriorityChips()
        setupSearch()
        setupConsultantToggles() // NEW

        observeServiceReviews()
        observeUsersFromRtdb()
        resolveAdminRole()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersRef?.let { ref -> usersListener?.let { ref.removeEventListener(it) } }
    }

    // ───────────────────────── UI Setup ─────────────────────────

    private fun setupLists() {
        // Pending consultants
        pendingConsAdapter = PendingConsultantAdapter(
            data = pendingConsultants,
            onApprove = { c -> rtdb.getReference("users/${c.uid}").child("isApproved").setValue(true) },
            onReject = { c -> rtdb.getReference("users/${c.uid}").removeValue() }
        )

        binding.rvPendingConsultants.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = pendingConsAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Assignments (unassigned reviews)
        assignmentAdapter = AssignmentAdapter(
            base = pendingAssignments,
            onClick = { /* optional */ },
            isAdmin = isAdmin,
            onAssign = { assignment, consultant -> assignConsultantTo(assignment, consultant) }
        )

        binding.rvPendingAssignments.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = assignmentAdapter
        }

        // Consultants lists
        unassignedAdapter = ConsultantRowAdapter(unassignedConsultants) { c ->
            showConsultantSheet(c)
        }
        assignedAdapter = ConsultantRowAdapter(assignedConsultants) { c ->
            showConsultantSheet(c)
        }

        binding.rvAssignedConsultants.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = unassignedAdapter // default show Unassigned
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Empty states
        toggleEmptyState(pendingConsultants.isEmpty(), binding.rvPendingConsultants, binding.emptyPendingConsultants)
        toggleEmptyState(pendingAssignments.isEmpty(), binding.rvPendingAssignments, binding.emptyPendingAssignments)
        toggleEmptyState(unassignedConsultants.isEmpty(), binding.rvAssignedConsultants, binding.emptyUnassignedConsultants)
    }

    private fun setupPriorityChips() {
        binding.groupPriority.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val filter = when (checkedId) {
                binding.btnFilterUrgent.id -> PriorityFilter.HIGH
                binding.btnFilterMedium.id -> PriorityFilter.MEDIUM
                binding.btnFilterLow.id -> PriorityFilter.LOW
                else -> PriorityFilter.ALL
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
                // Filter whichever list is visible
                if (showingUnassigned) {
                    unassignedAdapter.filter = s?.toString().orEmpty()
                } else {
                    assignedAdapter.filter = s?.toString().orEmpty()
                }
            }
        })
    }

    // NEW: Toggle between Unassigned / Assigned consultants
    private fun setupConsultantToggles() {
        binding.groupConsultants.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.btnShowUnassigned.id -> {
                    showingUnassigned = true
                    binding.rvAssignedConsultants.adapter = unassignedAdapter
                    toggleEmptyState(unassignedConsultants.isEmpty(), binding.rvAssignedConsultants, binding.emptyUnassignedConsultants)
                    binding.emptyAssignedConsultants.visibility = View.GONE
                }
                binding.btnShowAssigned.id -> {
                    showingUnassigned = false
                    binding.rvAssignedConsultants.adapter = assignedAdapter
                    toggleEmptyState(assignedConsultants.isEmpty(), binding.rvAssignedConsultants, binding.emptyAssignedConsultants)
                    binding.emptyUnassignedConsultants.visibility = View.GONE
                }
            }
        }

        if (binding.groupConsultants.checkedButtonId == View.NO_ID) {
            binding.groupConsultants.check(binding.btnShowUnassigned.id)
        }
    }

    // ─────────────── Firestore: Service Reviews ───────────────

    private fun observeServiceReviews() {
        db.collection(COL_REVIEWS)
            .orderBy(F_CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    allReviews.clear()
                    pendingAssignments.clear()
                    assignmentAdapter.resetBase(pendingAssignments)
                    toggleEmptyState(true, binding.rvPendingAssignments, binding.emptyPendingAssignments)
                    recomputeConsultantPools()
                    return@addSnapshotListener
                }

                allReviews.clear()
                allReviews += snap.documents.map { it.toAssignmentFirestore() }
                recomputeConsultantPools()
            }
    }

    // ─────────────── Realtime DB: Users ───────────────

    private fun observeUsersFromRtdb() {
        usersRef = rtdb.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allApprovedUsers.clear()
                snapshot.children.forEach { u ->
                    val uid = u.key ?: return@forEach
                    val isApproved = u.child("isApproved").getValue(Boolean::class.java) ?: false
                    val role = u.child("role").getValue(String::class.java) ?: "consultant"
                    if (!isApproved || role.lowercase(Locale.US) != "consultant") return@forEach

                    val firstName = u.child("firstName").getValue(String::class.java) ?: "(no name)"
                    val email = u.child("email").getValue(String::class.java).orEmpty()
                    val specialty = u.child("specialty").getValue(String::class.java).orEmpty()

                    allApprovedUsers[uid] = Consultant(uid, firstName, email, specialty)
                }

                pendingConsultants.clear()
                snapshot.children.forEach { u ->
                    val uid = u.key ?: return@forEach

                    val isApproved = u.child("isApproved").getValue(Boolean::class.java) ?: false
                    val role = u.child("role").getValue(String::class.java)?.lowercase() ?: ""


                    if (!isApproved && role == "consultant") {
                        pendingConsultants += Consultant(
                            uid = uid,
                            firstName = u.child("firstName").getValue(String::class.java) ?: "(no name)",
                            email = u.child("email").getValue(String::class.java).orEmpty(),
                            specialty = u.child("specialtisation").getValue(String::class.java).orEmpty()
                        )
                    }
                }


                pendingConsAdapter.notifyDataSetChanged()
                toggleEmptyState(pendingConsultants.isEmpty(), binding.rvPendingConsultants, binding.emptyPendingConsultants)
                recomputeConsultantPools()
            }

            override fun onCancelled(error: DatabaseError) {
                pendingConsultants.clear()
                pendingConsAdapter.notifyDataSetChanged()
                toggleEmptyState(true, binding.rvPendingConsultants, binding.emptyPendingConsultants)
                allApprovedUsers.clear()
                recomputeConsultantPools()
            }
        }

        usersListener = listener
        usersRef!!.addValueEventListener(listener)
    }

    // ─────────────── Admin Role ───────────────

    private fun resolveAdminRole() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        rtdb.reference.child("users").child(uid).child("role")
            .get()
            .addOnSuccessListener { snap ->
                val admin = snap.getValue(String::class.java)?.equals("admin", true) == true
                isAdmin = admin
                if (::assignmentAdapter.isInitialized) {
                    assignmentAdapter.setIsAdmin(admin)
                }
            }
    }

    // ─────────────── Derived Lists ───────────────

    private fun recomputeConsultantPools() {
        // Unassigned reviews (to display in assignments list)
        val unassignedReviews = allReviews.filter { it.consultantId.isNullOrBlank() }

        pendingAssignments.clear()
        pendingAssignments += unassignedReviews
        assignmentAdapter.resetBase(pendingAssignments)
        toggleEmptyState(
            pendingAssignments.isEmpty(),
            binding.rvPendingAssignments,
            binding.emptyPendingAssignments
        )

        // Who is assigned anywhere?
        val assignedIds = allReviews
            .mapNotNull { it.consultantId?.takeIf { id -> id.isNotBlank() } }
            .toSet()

        // All approved consultants (like web's GetConsultantAsync)
        val all = allApprovedUsers.values
            .sortedBy { it.firstName.lowercase(Locale.getDefault()) }

        // Split into "unassigned" vs "assigned" for the bottom list UI only
        unassignedConsultants.clear()
        assignedConsultants.clear()

        all.forEach { c ->
            if (c.uid in assignedIds) {
                assignedConsultants += c
            } else {
                unassignedConsultants += c
            }
        }

        unassignedAdapter.reset()
        assignedAdapter.reset()

        // Empty states based on the currently visible pool
        if (showingUnassigned) {
            toggleEmptyState(
                unassignedConsultants.isEmpty(),
                binding.rvAssignedConsultants,
                binding.emptyUnassignedConsultants
            )
            binding.emptyAssignedConsultants.visibility = View.GONE
        } else {
            toggleEmptyState(
                assignedConsultants.isEmpty(),
                binding.rvAssignedConsultants,
                binding.emptyAssignedConsultants
            )
            binding.emptyUnassignedConsultants.visibility = View.GONE
        }
        assignmentAdapter.setAssignableConsultants(all)
    }


    private fun assignConsultantTo(assignment: Assignment, consultant: Consultant) {
        db.collection(COL_REVIEWS).document(assignment.id)
            .update(
                F_CONSULTANT_ID, consultant.uid,
                F_STATUS, "Assigned",
                "updatedAt", FieldValue.serverTimestamp()
            )
    }

    // ─────────────── Bottom Sheet for Consultant Details ───────────────
    private fun showConsultantSheet(c: Consultant) {
        val dialog = BottomSheetDialog(this, R.style.AppBottomSheetDialog)
        val v = layoutInflater.inflate(R.layout.bottomsheet_consultant_details, null)

        val tvName   = v.findViewById<TextView>(R.id.tvName)
        val tvEmail  = v.findViewById<TextView>(R.id.tvEmail)
        val tvSpec   = v.findViewById<TextView>(R.id.tvSpecialty)
        val tvStats  = v.findViewById<TextView>(R.id.tvStats)
        val tvRole   = v.findViewById<TextView>(R.id.tvRole)

        tvName.text = c.firstName.ifBlank { "(no name)" }
        tvEmail.text = c.email
        tvSpec.text = if (c.specialty.isBlank()) "General" else c.specialty
        tvRole.text = "Consultant"

        val myReviews = allReviews.filter { it.consultantId == c.uid }
        val total = myReviews.size
        val completed = myReviews.count { it.status.equals("completed", true) || it.status.equals("done", true) }
        val assigned  = myReviews.count { it.status.equals("assigned", true) }
        val pending   = myReviews.count { it.status.equals("pending", true) || it.status.equals("submitted", true) }

        tvStats.text = "Total: $total • Completed: $completed • Assigned: $assigned • Pending: $pending"

        dialog.setContentView(v)
        dialog.show()
    }

    // ─────────────── Helpers ───────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toAssignmentFirestore(): Assignment {
        val pr = when ((getString(F_PRIORITY) ?: "LOW").uppercase(Locale.US)) {
            "HIGH", "URGENT" -> Priority.HIGH
            "MEDIUM" -> Priority.MEDIUM
            else -> Priority.LOW
        }

        val createdAtMs = toMillis(get(F_CREATED_AT))
        val deadlineMs = toMillis(get(F_DEADLINE))

        val svcRaw = getString(F_SERVICE_TYPE) ?: getString("service_type")
        val consultantId = getString(F_CONSULTANT_ID)

        val titleCandidate = getString(F_CUSTOM_NAME)
            ?: getString(F_ORIGINAL_FILE_NAME)
            ?: getString(F_DESCRIPTION)
            ?: svcRaw
            ?: "Request"

        return Assignment(
            id = id,
            serviceType = svcRaw,
            customName = getString(F_CUSTOM_NAME),
            originalFileName = getString(F_ORIGINAL_FILE_NAME),
            description = getString(F_DESCRIPTION),
            status = getString(F_STATUS),
            priority = pr,
            createdAt = createdAtMs,
            deadline = deadlineMs,
            consultantId = consultantId
        ).copy(titleOverride = titleCandidate)
    }

    private fun toMillis(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    private fun toggleEmptyState(empty: Boolean, list: View, placeholder: View) {
        placeholder.visibility = if (empty) View.VISIBLE else View.GONE
        list.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ─────────────── Models ───────────────

    data class Consultant(
        val uid: String,
        val firstName: String,
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
        val consultantId: String?,
        val titleOverride: String? = null
    ) {
        fun title(): String =
            titleOverride ?: customName ?: originalFileName ?: description ?: serviceType ?: "Request"
    }

    /** Pending consultant approval adapter */
    private class PendingConsultantAdapter(
        private val data: MutableList<Consultant>,
        private val onApprove: (Consultant) -> Unit,
        private val onReject: (Consultant) -> Unit
    ) : RecyclerView.Adapter<PendingConsultantAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvInitial: TextView = v.findViewById(R.id.tvInitial)
            val tvUsername: TextView = v.findViewById(R.id.tvUsername)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val btnAccept: View = v.findViewById(R.id.btnAccept)
            val btnReject: View = v.findViewById(R.id.btnReject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_consultant, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val c = data[position]
            val initial = (c.firstName.trim().firstOrNull() ?: c.email.trim().firstOrNull() ?: '?')
                .uppercaseChar().toString()
            h.tvInitial.text = initial
            h.tvUsername.text = c.email
            h.tvName.text = if (c.firstName.isBlank()) "(no name)" else c.firstName

            h.btnAccept.setOnClickListener { onApprove(c) }
            h.btnReject.setOnClickListener { onReject(c) }
        }

        override fun getItemCount(): Int = data.size
    }

    private enum class PriorityFilter { ALL, LOW, MEDIUM, HIGH }

    private class AssignmentAdapter(
        private val base: MutableList<Assignment>,
        private val onClick: (Assignment) -> Unit,
        private var isAdmin: Boolean,
        private val onAssign: (Assignment, Consultant) -> Unit
    ) : RecyclerView.Adapter<AssignmentAdapter.VH>() {

        private val visible = mutableListOf<Assignment>()
        private var filter: PriorityFilter = PriorityFilter.ALL
        private val dateFmt = DATE_FMT
        private var assignableConsultants: List<Consultant> = emptyList()

        init { resetBase(base) }

        fun resetBase(newItems: List<Assignment>) {
            val snapshot = newItems.toList()
            base.clear()
            base.addAll(snapshot)
            applyFilter()
        }

        fun setPriorityFilter(f: PriorityFilter) {
            filter = f
            applyFilter()
        }

        fun setAssignableConsultants(list: List<Consultant>) {
            assignableConsultants = list
            notifyDataSetChanged()
        }

        fun setIsAdmin(value: Boolean) {
            isAdmin = value
            notifyDataSetChanged()
        }

        private fun applyFilter() {
            visible.clear()
            visible += when (filter) {
                PriorityFilter.ALL -> base
                PriorityFilter.LOW -> base.filter { it.priority == Priority.LOW }
                PriorityFilter.MEDIUM -> base.filter { it.priority == Priority.MEDIUM }
                PriorityFilter.HIGH -> base.filter { it.priority == Priority.HIGH }
            }
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card: MaterialCardView = v.findViewById(R.id.taskCard)
            val tvCategory: TextView = v.findViewById(R.id.tvCategory)
            val tvPriority: TextView = v.findViewById(R.id.tvPriority)
            val tvServiceTitle: TextView = v.findViewById(R.id.tvServiceTitle)
            val tvSubmittedDate: TextView = v.findViewById(R.id.tvSubmittedDate)
            val tvStatusLabel: TextView = v.findViewById(R.id.tvStatusLabel)
            val tvDeadline: TextView = v.findViewById(R.id.tvDeadline)
            val ddConsultants: MaterialAutoCompleteTextView? = v.findViewById(R.id.ddConsultants)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_service_review, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = visible[position]
            val context = h.itemView.context

            h.tvCategory.text = item.serviceType ?: "Other"
            h.tvServiceTitle.text = item.title()

            val statusLabel = item.status
                ?.replace('_', ' ')
                ?.lowercase()
                ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                ?: "Pending"
            h.tvStatusLabel.text = statusLabel

            val statusColor = when (statusLabel.lowercase()) {
                "assigned" -> R.color.blue_400
                "completed", "done" -> R.color.green_500
                "pending", "submitted" -> R.color.priority_Medium
                else -> R.color.light_highlight
            }
            h.tvStatusLabel.setTextColor(context.getColor(statusColor))

            h.tvSubmittedDate.text = "Submitted: ${dateFmt.safe(item.createdAt)}"
            val deadlineText = dateFmt.safe(item.deadline)
            h.tvDeadline.visibility = if (deadlineText == "—") View.GONE else View.VISIBLE
            h.tvDeadline.text = "Deadline: $deadlineText"

            h.tvPriority.text = when (item.priority) {
                Priority.HIGH -> "High"
                Priority.MEDIUM -> "Medium"
                Priority.LOW -> "Low"
            }
            val priorityColor = when (item.priority) {
                Priority.HIGH -> R.color.priority_High
                Priority.MEDIUM -> R.color.priority_Medium
                Priority.LOW -> R.color.priority_Low
            }
            h.tvPriority.setTextColor(context.getColor(priorityColor))

            h.card.setOnClickListener { onClick(item) }

            val showDropdown = isAdmin && item.consultantId.isNullOrBlank() && h.ddConsultants != null
            h.ddConsultants?.visibility = if (showDropdown) View.VISIBLE else View.GONE

            if (showDropdown) {
                val dropdownAdapter = ConsultantDropdownAdapter(context, assignableConsultants)
                h.ddConsultants?.apply {
                    setAdapter(dropdownAdapter)
                    setDropDownBackgroundResource(R.drawable.bg_spinner_popup_white)

                    // open dropdown when field tapped
                    setOnClickListener { showDropDown() }
                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) (v as? MaterialAutoCompleteTextView)?.showDropDown()
                    }

                    // clear any old selection when reused
                    setText("", false)
                    tag = null

                    setOnItemClickListener { _, _, idx, _ ->
                        val chosen = assignableConsultants.getOrNull(idx) ?: return@setOnItemClickListener
                        onAssign(item, chosen)
                        // Show selected consultant in the field
                        val label = buildString {
                            append(if (chosen.firstName.isBlank()) "Unknown" else chosen.firstName)
                            append(" · ")
                            append(chosen.email)
                        }
                        setText(label, false)
                        clearFocus() // collapse dropdown
                    }
                }
            }
        }

        override fun getItemCount(): Int = visible.size

        private fun SimpleDateFormat.safe(ts: Long?): String =
            ts?.let { format(Date(it)) } ?: "—"

        private class ConsultantDropdownAdapter(
            context: android.content.Context,
            private val items: List<Consultant>
        ) : ArrayAdapter<Consultant>(context, 0, items) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // Text shown in the field after selection
                return createItemView(position, convertView, parent)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                // Rows in the dropdown list
                return createItemView(position, convertView, parent)
            }

            private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_assign_consultant_option, parent, false)

                val consultant = items[position]

                val tvAvatar = v.findViewById<TextView>(R.id.tvAvatar)
                val tvName = v.findViewById<TextView>(R.id.tvName)
                val tvEmail = v.findViewById<TextView>(R.id.tvEmail)

                val initial = (consultant.firstName.trim().firstOrNull()
                    ?: consultant.email.trim().firstOrNull()
                    ?: '?').uppercaseChar()

                tvAvatar.text = initial.toString()
                tvName.text = if (consultant.firstName.isBlank()) "Unknown" else consultant.firstName
                tvEmail.text = consultant.email

                return v
            }
        }

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
            val f = filter.trim()
            visible.clear()
            if (f.isEmpty()) {
                visible.addAll(all)
            } else {
                visible.addAll(all.filter {
                    it.firstName.contains(f, true) ||
                            it.email.contains(f, true) ||
                            it.specialty.contains(f, true)
                })
            }
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvInitials: TextView = v.findViewById(R.id.tvAvatarInitials)
            val tvName: TextView = v.findViewById(R.id.tvConsultantName)
            val tvEmail: TextView = v.findViewById(R.id.tvConsultantEmail)
            val tvSpecialty: TextView = v.findViewById(R.id.tvSpecialty)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_consultant_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val c = visible[position]
            h.tvInitials.text = c.firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            h.tvName.text = c.firstName
            h.tvEmail.text = c.email
            h.tvSpecialty.text = if (c.specialty.isBlank()) "General" else c.specialty
            h.itemView.setOnClickListener { onClick(c) }
        }

        override fun getItemCount(): Int = visible.size
    }

    companion object {
        private const val COL_REVIEWS = "ServiceReviews"
        private const val F_CONSULTANT_ID = "consultantId"
        private const val F_CREATED_AT = "createdAt"
        private const val F_DEADLINE = "deadline"
        private const val F_PRIORITY = "priority"
        private const val F_SERVICE_TYPE = "serviceType"
        private const val F_CUSTOM_NAME = "customName"
        private const val F_ORIGINAL_FILE_NAME = "originalFileName"
        private const val F_DESCRIPTION = "description"
        private const val F_STATUS = "status"

        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }
}
