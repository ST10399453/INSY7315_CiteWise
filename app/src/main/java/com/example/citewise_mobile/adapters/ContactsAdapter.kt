package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.offline.UserEntity

/**
 * Adapter for showing user contacts in bottom sheets (New Chat, Search Chats).
 */
class ContactsAdapter(
    private val onClick: (UserEntity) -> Unit
) : ListAdapter<UserEntity, ContactsAdapter.VH>(DIFF) {

    private val full = mutableListOf<UserEntity>()

    override fun submitList(items: List<UserEntity>?) {
        full.clear()
        if (items != null) full.addAll(items)
        super.submitList(items?.toList() ?: emptyList())
    }

    fun filter(q: String) {
        val query = q.trim().lowercase()
        val filtered =
            if (query.isEmpty()) full
            else full.filter {
                val name = "${it.firstName} ${it.surname}".trim().lowercase()
                name.contains(query) || it.email.lowercase().contains(query)
            }
        super.submitList(filtered)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(view: View, private val onClick: (UserEntity) -> Unit) :
        RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(android.R.id.text1)
        private val sub = view.findViewById<TextView>(android.R.id.text2)

        fun bind(u: UserEntity) {
            title.text = "${u.firstName} ${u.surname}".trim().ifEmpty { u.email }
            sub.text = u.email
            itemView.setOnClickListener { onClick(u) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<UserEntity>() {
            override fun areItemsTheSame(o: UserEntity, n: UserEntity) = o.uid == n.uid
            override fun areContentsTheSame(o: UserEntity, n: UserEntity) = o == n
        }
    }
}
