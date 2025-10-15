import android.content.res.Resources
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.citewise_mobile.R
import com.example.citewise_mobile.offline.DocumentEntity

class DocumentsAdapter(
    private val onOverflow: (DocumentEntity, View) -> Unit,
    private val onOpen: (DocumentEntity) -> Unit
) : ListAdapter<DocumentEntity, DocumentsAdapter.VH>(diff) {

    object diff : DiffUtil.ItemCallback<DocumentEntity>() {
        override fun areItemsTheSame(a: DocumentEntity, b: DocumentEntity) = a.id == b.id
        override fun areContentsTheSame(a: DocumentEntity, b: DocumentEntity) = a == b
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.tvDocName)
        val overflow: ImageButton = v.findViewById(R.id.btnOverflow)
        val offlineBadge: ImageView = ImageView(v.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(24.dp, 24.dp)
            setImageResource(R.drawable.ic_offline_pin) // provide a vector drawable
            contentDescription = "Available offline"
            alpha = 0.9f
            visibility = View.GONE
        }
        init {
            if (v is ViewGroup) {
                (v as ViewGroup).addView(offlineBadge)
                (offlineBadge.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    setMargins(0, 0, 12.dp, 12.dp)
                }
                offlineBadge.translationX = (v.width - 36.dp).toFloat()
                offlineBadge.translationY = (v.height - 36.dp).toFloat()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_document_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = getItem(pos)
        h.title.text = item.fileName
        h.offlineBadge.visibility = if (item.localPath != null) View.VISIBLE else View.GONE
        h.itemView.setOnClickListener { onOpen(item) }
        h.overflow.setOnClickListener { onOverflow(item, it) }
    }
}

private val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()
