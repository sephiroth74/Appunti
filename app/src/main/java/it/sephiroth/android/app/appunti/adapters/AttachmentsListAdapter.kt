package it.sephiroth.android.app.appunti.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import it.sephiroth.android.app.appunti.DetailActivity
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.ext.loadThumbnail
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import kotlinx.android.synthetic.main.appunti_detail_attachment_item.view.*

class AttachmentsListAdapter(private var activity: DetailActivity) :
    RecyclerView.Adapter<AttachmentsListAdapter.AttachmentItemViewHolder>() {

    companion object {}

    var deleteAction: ((Attachment) -> (Unit))? = null
    var shareAction: ((Attachment) -> (Unit))? = null
    var clickAction: ((Attachment) -> (Unit))? = null

    private val data: MutableList<Attachment> = mutableListOf()
    private val inflater: LayoutInflater by lazy { LayoutInflater.from(activity) }

    internal var cardColor: Int = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentItemViewHolder {
        val view = inflater.inflate(R.layout.appunti_detail_attachment_item, parent, false)
        return AttachmentItemViewHolder(view)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: AttachmentItemViewHolder, position: Int) {
        val attachment = data[position]

        (holder.itemView as CardView).setCardBackgroundColor(cardColor)
        holder.bind(attachment)

        holder.removeButton.setOnClickListener {
            deleteAction?.invoke(attachment)
        }

        holder.shareButton.setOnClickListener {
            shareAction?.invoke(attachment)
        }

        holder.itemView.setOnClickListener { clickAction?.invoke(attachment) }

    }

    fun update(attachments: List<Attachment>?) {
        doOnMainThread {
            data.clear()
            data.addAll(attachments ?: listOf())
            notifyDataSetChanged()
        }
    }

    class AttachmentItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val attachmentTitle: TextView = itemView.attachmentTitle
        private val attachmentImage: ImageView = itemView.attachmentImage
        var removeButton: View = itemView.attachmentRemoveButton
        var shareButton: View = itemView.attachmentShareButton

        fun bind(attachment: Attachment) {
            attachmentTitle.text = attachment.attachmentTitle
            attachment.loadThumbnail(itemView.context, attachmentImage)
        }
    }
}