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
    val peerUid: String,        // <-- added: used when launching ConversationActivity
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
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMillis }

            val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

            return if (sameDay) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
            } else {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
            }
        }
    }
}
