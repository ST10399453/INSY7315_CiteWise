package com.example.citewise_mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.databinding.ActivityManageConsultantsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.*
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManageConsultantsActivity : BaseActivity() {

    private lateinit var binding: ActivityManageConsultantsBinding

    // Firestore
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Realtime Database for pending consultant approvals
    private val rtdb by lazy { FirebaseDatabase.getInstance() }
    private var usersRef: DatabaseReference? = null
    private var pendingConsListener: ValueEventListener? = null

    // In-memory lists
    private val pendingConsultants = mutableListOf<Consultant>()
    private val pendingAssignments = mutableListOf<Assignment>()
    private val unassignedConsultants = mutableListOf<Consultant>()

    // Adapters
    private lateinit var pendingConsAdapter: PendingConsultantAdapter
    private lateinit var assignmentAdapter: AssignmentAdapter
    private lateinit var unassignedAdapter: ConsultantRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the shared base shell (contains BottomNavigationView & a container)
        setContentView(R.layout.activity_base)
        applyInsets(R.id.main)

        // Inflate this screen's layout into the base container
        val baseContent = findViewById<ViewGroup>(R.id.baseContent)
        binding = ActivityManageConsultantsBinding.inflate(layoutInflater, baseContent, true)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        setupBottomNav(bottomNav, selectedItemId = R.id.bottomNav)

        setupLists()
        setupPriorityChips()
        setupSearch()

        // Load / listen
        fetchPendingConsultantsFromRealtimeDb()
        fetchPendingAssignmentsFromFirestoreOr()
        fetchUnassignedConsultantsFromFirestoreOr()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersRef?.let { ref -> pendingConsListener?.let { ref.removeEventListener(it) } }
    }

    // ───────────────────────── UI ─────────────────────────

    private fun setupLists() {
        // Pending consultants (approvals from RTDB)
        pendingConsAdapter = PendingConsultantAdapter(
            data = pendingConsultants,
            onApprove = { c ->
                rtdb.getReference("users/${c.uid}").child("isApproved").setValue(true)
            },
            onReject = { c ->
                rtdb.getReference("users/${c.uid}").removeValue()
            }
        )
        binding.rvPendingConsultants.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = pendingConsAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Pending assignments (from Firestore)
        assignmentAdapter = AssignmentAdapter(pendingAssignments) { /* open details if needed */ }
        binding.rvPendingAssignments.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = assignmentAdapter
        }

        // Unassigned consultants (from Firestore)
        unassignedAdapter = ConsultantRowAdapter(unassignedConsultants) { /* on click */ }
        binding.rvAssignedConsultants.apply {
            layoutManager = LinearLayoutManager(this@ManageConsultantsActivity)
            adapter = unassignedAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Initial empty-state visibility
        toggleEmptyState(
            empty = pendingConsultants.isEmpty(),
            list = binding.rvPendingConsultants,
            placeholder = binding.emptyPendingConsultants
        )
        toggleEmptyState(
            empty = pendingAssignments.isEmpty(),
            list = binding.rvPendingAssignments,
            placeholder = binding.emptyPendingAssignments
        )
        toggleEmptyState(
            empty = unassignedConsultants.isEmpty(),
            list = binding.rvAssignedConsultants,
            placeholder = binding.emptyUnassignedConsultants
        )
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

    // ─────────────── Realtime DB: pending consultants ───────────────

    private fun fetchPendingConsultantsFromRealtimeDb() {
        usersRef = rtdb.getReference("users")
        val query = usersRef!!.orderByChild("isApproved").equalTo(false)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pendingConsultants.clear()
                for (u in snapshot.children) {
                    val uid       = u.key ?: continue
                    val firstName = u.child("firstName").getValue(String::class.java) ?: "(no name)"
                    val email     = u.child("email").getValue(String::class.java).orEmpty()
                    val specialty = u.child("specialty").getValue(String::class.java).orEmpty()

                    pendingConsultants += Consultant(
                        uid = uid,
                        firstName = firstName,
                        email = email,
                        specialty = specialty
                    )
                }
                pendingConsAdapter.notifyDataSetChanged()
                toggleEmptyState(
                    empty = pendingConsultants.isEmpty(),
                    list = binding.rvPendingConsultants,
                    placeholder = binding.emptyPendingConsultants
                )
            }

            override fun onCancelled(error: DatabaseError) {
                pendingConsultants.clear()
                pendingConsAdapter.notifyDataSetChanged()
                toggleEmptyState(true, binding.rvPendingConsultants, binding.emptyPendingConsultants)
            }
        }

        pendingConsListener = listener
        query.addValueEventListener(listener)
    }

    // ─────────────── Firestore: assignments where consultantId == null OR "" ───────────────

    private fun fetchPendingAssignmentsFromFirestoreOr() {
        db.collection(COL_REVIEWS)
            .where(
                Filter.or(
                    Filter.equalTo(F_CONSULTANT_ID, null),
                    Filter.equalTo(F_CONSULTANT_ID, "")
                )
            )
            .orderBy(F_CREATED_AT, Query.Direction.DESCENDING) // may require a composite index
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val items = snap.documents.map { it.toAssignmentFirestore() }
                pendingAssignments.clear()
                pendingAssignments.addAll(items)
                assignmentAdapter.resetBase(pendingAssignments)
                toggleEmptyState(
                    empty = pendingAssignments.isEmpty(),
                    list = binding.rvPendingAssignments,
                    placeholder = binding.emptyPendingAssignments
                )
            }
            .addOnFailureListener {
                pendingAssignments.clear()
                assignmentAdapter.resetBase(pendingAssignments)
                toggleEmptyState(true, binding.rvPendingAssignments, binding.emptyPendingAssignments)
            }
    }

    // ─────────────── Firestore: UNASSIGNED consultants (no REST) ───────────────
    private fun fetchUnassignedConsultantsFromFirestoreOr() {
        db.collection(COL_CONSULTANTS)
            .where(
                Filter.or(
                    Filter.equalTo(F_IS_ASSIGNED, false),
                    Filter.equalTo(F_ASSIGNED_REQUEST_ID, null),
                    Filter.equalTo(F_ASSIGNED_REQUEST_ID, "")
                )
            )
            .orderBy(F_CREATED_AT, Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snap ->
                unassignedConsultants.clear()
                snap.documents.forEach { d ->
                    unassignedConsultants += d.toConsultant()
                }
                unassignedAdapter.reset()
                toggleEmptyState(
                    empty = unassignedConsultants.isEmpty(),
                    list = binding.rvAssignedConsultants,
                    placeholder = binding.emptyUnassignedConsultants
                )
            }
            .addOnFailureListener {
                unassignedConsultants.clear()
                unassignedAdapter.reset()
                toggleEmptyState(true, binding.rvAssignedConsultants, binding.emptyUnassignedConsultants)
            }
    }

    // ─────────────── Mapping helpers ───────────────

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

    private fun com.google.firebase.firestore.DocumentSnapshot.toConsultant(): Consultant {
        val uid        = getString("uid") ?: id
        val firstName  = getString("firstName") ?: getString("name") ?: "(no name)"
        val email      = getString("email").orEmpty()
        val specialty  = getString("specialty").orEmpty()
        return Consultant(
            uid = uid,
            firstName = firstName,
            email = email,
            specialty = specialty
        )
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

    // ─────────────── Adapters ───────────────

    /** Pending consultants row (uses item_pending_consultant.xml). */
    private class PendingConsultantAdapter(
        private val data: MutableList<Consultant>,
        private val onApprove: (Consultant) -> Unit,
        private val onReject: (Consultant) -> Unit
    ) : RecyclerView.Adapter<PendingConsultantAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvInitial: TextView  = v.findViewById(R.id.tvInitial)
            val tvUsername: TextView = v.findViewById(R.id.tvUsername) // email
            val tvName: TextView     = v.findViewById(R.id.tvName)     // first name
            val btnAccept: View      = v.findViewById(R.id.btnAccept)
            val btnReject: View      = v.findViewById(R.id.btnReject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_consultant, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val c = data[position]
            val initial = (c.firstName.trim().firstOrNull()
                ?: c.email.trim().firstOrNull() ?: '?').uppercaseChar().toString()
            h.tvInitial.text = initial
            h.tvUsername.text = c.email
            h.tvName.text = if (c.firstName.isBlank()) "(no name)" else c.firstName

            h.btnAccept.setOnClickListener { onApprove(c) }
            h.btnReject.setOnClickListener { onReject(c) }
        }

        override fun getItemCount(): Int = data.size

        fun reset(newItems: List<Consultant>) {
            data.clear()
            data.addAll(newItems)
            notifyDataSetChanged()
        }
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
                    all.filter {
                        it.firstName.lowercase().contains(f) || it.email.lowercase().contains(f)
                    }
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
            h.tvInitials.text = c.firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            h.tvName.text = c.firstName
            h.tvEmail.text = c.email
            h.tvSpecialty.text = if (c.specialty.isBlank()) "General" else c.specialty
            h.itemView.setOnClickListener { onClick(c) }
        }

        override fun getItemCount(): Int = visible.size
    }

    companion object {
        // Firestore: ServiceReviews (assignments)
        private const val COL_REVIEWS = "ServiceReviews"
        private const val F_CONSULTANT_ID      = "consultantId"
        private const val F_CREATED_AT         = "createdAt"
        private const val F_DEADLINE           = "deadline"
        private const val F_PRIORITY           = "priority"
        private const val F_SERVICE_TYPE       = "serviceType"
        private const val F_CUSTOM_NAME        = "customName"
        private const val F_ORIGINAL_FILE_NAME = "originalFileName"
        private const val F_DESCRIPTION        = "description"
        private const val F_STATUS             = "status"

        // Firestore: Consultants (unassigned list)
        private const val COL_CONSULTANTS = "Consultants"
        private const val F_IS_ASSIGNED = "isAssigned"
        private const val F_ASSIGNED_REQUEST_ID = "assignedRequestId"

        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }
}
