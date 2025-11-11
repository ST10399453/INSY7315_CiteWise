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

data class Message(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isMe: Boolean
)

sealed class MessageItem {
    data class MessageData(val message: Message) : MessageItem()
    data class DateDivider(val date: String) : MessageItem()
}

class MessagesAdapter : ListAdapter<MessageItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE_DIVIDER = 3

        private fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeStr = String.format("%02d:%02d", hour, minute)

            return timeStr
        }

        fun formatDateDivider(timestamp: Long): String {
            val now = Calendar.getInstance()
            val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }

            val daysDiff = (now.get(Calendar.DAY_OF_YEAR) - messageDate.get(Calendar.DAY_OF_YEAR))

            return when {
                daysDiff == 0 && now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) -> "Today"
                daysDiff == 1 && now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) -> "Yesterday"
                now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) -> {
                    SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(timestamp))
                }
                else -> {
                    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }

        fun insertDateDividers(messages: List<Message>): List<MessageItem> {
            if (messages.isEmpty()) return emptyList()

            val result = mutableListOf<MessageItem>()
            var lastDate: String? = null

            messages.forEach { message ->
                val currentDate = formatDateDivider(message.timestamp)
                if (currentDate != lastDate) {
                    result.add(MessageItem.DateDivider(currentDate))
                    lastDate = currentDate
                }
                result.add(MessageItem.MessageData(message))
            }

            return result
        }
    }

    object Diff : DiffUtil.ItemCallback<MessageItem>() {
        override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
            return when {
                oldItem is MessageItem.MessageData && newItem is MessageItem.MessageData ->
                    oldItem.message.id == newItem.message.id
                oldItem is MessageItem.DateDivider && newItem is MessageItem.DateDivider ->
                    oldItem.date == newItem.date
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem) = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is MessageItem.DateDivider -> TYPE_DATE_DIVIDER
            is MessageItem.MessageData -> if (item.message.isMe) TYPE_SENT else TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_DIVIDER -> {
                val v = inflater.inflate(R.layout.item_date_divider, parent, false)
                DateDividerVH(v)
            }
            TYPE_SENT -> {
                val v = inflater.inflate(R.layout.item_message_sent, parent, false)
                SentVH(v)
            }
            else -> {
                val v = inflater.inflate(R.layout.item_message_received, parent, false)
                ReceivedVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MessageItem.DateDivider -> {
                (holder as DateDividerVH).tvDate.text = item.date
            }
            is MessageItem.MessageData -> {
                val message = item.message
                val time = formatRelativeTime(message.timestamp)
                when (holder) {
                    is SentVH -> {
                        holder.tvMessage.text = message.text
                        holder.tvTime.text = time
                    }
                    is ReceivedVH -> {
                        holder.tvMessage.text = message.text
                        holder.tvTime.text = time
                    }
                }
            }
        }
    }

    class DateDividerVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }

    class SentVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    class ReceivedVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }
}
