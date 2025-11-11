package com.example.citewise_mobile.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.offline.UserEntity
import java.util.Locale

class ContactsAdapter(
    private val onClick: (UserEntity) -> Unit
) : ListAdapter<UserEntity, ContactsAdapter.VH>(DIFF), Filterable {

    private val original = mutableListOf<UserEntity>()
    private var lastQuery: String = ""

    fun submitListSafe(items: List<UserEntity>) {
        original.clear()
        original.addAll(items)
        lastQuery = ""
        super.submitList(items.toList())
        Log.d(TAG, "submitListSafe: size=${items.size}")
    }

    fun filter(query: String) {
        lastQuery = query
        filter.filter(query)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_row, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        Log.d(TAG, "onBind @$position -> ${item.uid}  name=${item.firstName} ${item.surname}")
        holder.bind(item)
    }

    class VH(v: View, private val onClick: (UserEntity) -> Unit) : RecyclerView.ViewHolder(v) {
        private val tvInitials: TextView = v.findViewById(R.id.tvInitials)
        private val tvName: TextView = v.findViewById(R.id.tvName)
        private val tvEmail: TextView = v.findViewById(R.id.tvEmail)
        private var current: UserEntity? = null

        init { v.setOnClickListener { current?.let(onClick) } }

        fun bind(u: UserEntity) {
            current = u
            val displayName = buildDisplayName(u)
            tvName.text = displayName
            tvEmail.text = if (u.email.isNotBlank()) u.email else u.uid
            val initials = displayName.trim().split(Regex("\\s+")).take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("").ifBlank { "?" }
            tvInitials.text = initials
        }

        private fun buildDisplayName(u: UserEntity): String {
            val name = "${u.firstName} ${u.surname}".trim()
            return if (name.isNotBlank()) name else (u.email.ifBlank { u.uid })
        }
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val q = constraint?.toString()?.trim().orEmpty()
            val res = FilterResults()
            if (q.isEmpty()) {
                res.values = original.toList()
                res.count = original.size
            } else {
                val lower = q.lowercase(Locale.getDefault())
                val filtered = original.filter { u ->
                    val name = "${u.firstName} ${u.surname}".trim().lowercase(Locale.getDefault())
                    val mail = u.email.lowercase(Locale.getDefault())
                    val uid  = u.uid.lowercase(Locale.getDefault())
                    name.contains(lower) || mail.contains(lower) || uid.contains(lower)
                }
                res.values = filtered
                res.count = filtered.size
            }
            return res
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val list = results?.values as? List<UserEntity> ?: emptyList()
            submitList(list.toList())
            Log.d(TAG, "publishResults: query='$lastQuery' size=${list.size}")
        }
    }

    companion object {
        private const val TAG = "ContactsAdapter"
        private val DIFF = object : DiffUtil.ItemCallback<UserEntity>() {
            override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem.uid == newItem.uid
            override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity) = oldItem == newItem
        }
    }
}
