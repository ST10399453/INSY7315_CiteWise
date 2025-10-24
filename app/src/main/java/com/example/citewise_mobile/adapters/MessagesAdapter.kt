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
    val isMe: Boolean // true if message is from current user
)

class MessagesAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    object Diff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMe) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            val v = inflater.inflate(R.layout.item_message_sent, parent, false)
            SentVH(v)
        } else {
            val v = inflater.inflate(R.layout.item_message_received, parent, false)
            ReceivedVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val time = timeFormat.format(Date(item.timestamp))
        when (holder) {
            is SentVH -> {
                holder.tvMessage.text = item.text
                holder.tvTime.text = time
            }
            is ReceivedVH -> {
                holder.tvMessage.text = item.text
                holder.tvTime.text = time
            }
        }
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