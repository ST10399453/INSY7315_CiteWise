package com.example.citewise_mobile.data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R

data class Message(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val timestamp: Long
)

class MessagesAdapter :
    ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2

        val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(o: Message, n: Message) = o.id == n.id
            override fun areContentsTheSame(o: Message, n: Message) = o == n
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentVH(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedVH(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is SentVH -> holder.bind(item)
            is ReceivedVH -> holder.bind(item)
        }
    }

    class SentVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMessage)
        fun bind(m: Message) { tv.text = m.text }
    }

    class ReceivedVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMessage)
        fun bind(m: Message) { tv.text = m.text }
    }
}
