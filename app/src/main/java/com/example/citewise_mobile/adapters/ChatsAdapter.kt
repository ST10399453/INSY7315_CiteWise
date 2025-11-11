package com.example.citewise_mobile.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import java.text.SimpleDateFormat
import java.util.*

data class ChatPreview(
    val chatId: String,
    val peerUid: String,
    val displayName: String,
    val lastMessage: String,
    val lastTimestamp: Long     // epoch millis
)

class ChatsAdapter(
    private val onChatClicked: (ChatPreview) -> Unit
) : ListAdapter<ChatPreview, ChatsAdapter.ChatVH>(Diff) {

    init {
        setHasStableIds(true)
    }

    object Diff : DiffUtil.ItemCallback<ChatPreview>() {
        override fun areItemsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean =
            oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean =
            oldItem == newItem
    }

    override fun getItemId(position: Int): Long =
        getItem(position).chatId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_overview, parent, false)
        return ChatVH(view, onChatClicked)
    }

    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatVH(
        itemView: View,
        private val onChatClicked: (ChatPreview) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(item: ChatPreview) {
            val initial = item.displayName.firstOrNull()?.uppercaseChar() ?: '?'
            tvInitial.text = initial.toString()
            tvName.text = item.displayName
            tvLastMessage.text = item.lastMessage
            tvTime.text = formatTime(item.lastTimestamp)

            itemView.setOnClickListener { onChatClicked(item) }
        }

        private fun formatTime(epochMillis: Long): String {
            if (epochMillis <= 0L) return ""

            val now = System.currentTimeMillis()
            val diff = now - epochMillis

            // Convert to seconds, minutes, hours, days
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                else -> {
                    // For older messages, show date
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
                }
            }
        }
    }
}
