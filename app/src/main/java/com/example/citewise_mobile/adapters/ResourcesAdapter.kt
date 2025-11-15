package com.example.citewise_mobile.adapters

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.offline.ResourceEntity

class ResourcesAdapter(
    private val onOverflow: (ResourceEntity, View) -> Unit,
    private val onOpen: (ResourceEntity) -> Unit
) : ListAdapter<ResourceEntity, ResourcesAdapter.VH>(diff) {

    companion object diff : DiffUtil.ItemCallback<ResourceEntity>() {
        override fun areItemsTheSame(a: ResourceEntity, b: ResourceEntity) =
            (a.remoteId ?: a.localId.toString()) == (b.remoteId ?: b.localId.toString())

        override fun areContentsTheSame(a: ResourceEntity, b: ResourceEntity) = a == b
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.tvDocName)
        val overflow: ImageButton = v.findViewById(R.id.btnOverflow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = getItem(pos)
        h.title.text = item.title.ifBlank { item.fileName ?: "Untitled" }
        h.itemView.setOnClickListener { onOpen(item) }
        h.overflow.setOnClickListener { onOverflow(item, it) }
    }
}
