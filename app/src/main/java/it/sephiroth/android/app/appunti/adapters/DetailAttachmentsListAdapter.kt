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
import it.sephiroth.android.app.appunti.events.RxBus
import it.sephiroth.android.app.appunti.events.impl.AttachmentOnClickEvent
import it.sephiroth.android.app.appunti.events.impl.AttachmentOnDeleteEvent
import it.sephiroth.android.app.appunti.events.impl.AttachmentOnShareEvent
import it.sephiroth.android.app.appunti.ext.loadThumbnail
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import kotlinx.android.synthetic.main.appunti_detail_attachment_item.view.*

class DetailAttachmentsListAdapter(private var activity: DetailActivity) :
    RecyclerView.Adapter<DetailAttachmentsListAdapter.AttachmentItemViewHolder>() {

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
        private var removeButton: View = itemView.attachmentRemoveButton
        private var shareButton: View = itemView.attachmentShareButton

        fun bind(attachment: Attachment) {
            attachmentTitle.text = attachment.attachmentTitle
            attachment.loadThumbnail(itemView.context, attachmentImage)

            shareButton.setOnClickListener {
                RxBus.send(AttachmentOnShareEvent(attachment))
            }

            removeButton.setOnClickListener {
                RxBus.send(AttachmentOnDeleteEvent(attachment))
            }

            itemView.setOnClickListener {
                RxBus.send(AttachmentOnClickEvent(attachment))
            }
        }
    }
}