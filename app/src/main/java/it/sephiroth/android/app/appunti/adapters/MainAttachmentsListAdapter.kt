package it.sephiroth.android.app.appunti.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import timber.log.Timber

class MainAttachmentsListAdapter(private var context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_OTHERS = 1
        private const val MAX_SIZE = 2
    }

    private var moreItemsCount = 0
    private var size: Int = 0
    private val data: MutableList<Attachment> = mutableListOf()
    private val inflater: LayoutInflater by lazy { LayoutInflater.from(context) }

    override fun getItemCount(): Int {
        return size
    }

    override fun getItemViewType(position: Int): Int {
        return if (moreItemsCount > 0) {
            if (position == MAX_SIZE) TYPE_OTHERS
            else TYPE_ITEM
        } else {
            TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view: View = inflater.inflate(R.layout.appunti_main_attachment_chip, parent, false)
            AttachmentItemViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.appunti_main_attachment_others, parent, false)
            TextViewHolder(view)
        }
    }

    override fun onBindViewHolder(baseHolder: RecyclerView.ViewHolder, position: Int) {
        if (baseHolder.itemViewType == TYPE_ITEM) {
            val holder = baseHolder as AttachmentItemViewHolder
            val attachment = data[position]
            holder.bind(attachment)
        } else {
            val holder = baseHolder as TextViewHolder
            holder.textView.text =
                context.resources.getQuantityString(R.plurals.attachments_others_count, moreItemsCount, moreItemsCount)
        }
    }

    fun update(attachments: List<Attachment>?) {
        doOnMainThread {
            data.clear()
            data.addAll(attachments ?: listOf())

            if (data.size > MAX_SIZE) {
                size = MAX_SIZE + 1
                moreItemsCount = data.size - MAX_SIZE
            } else {
                size = data.size
                moreItemsCount = 0
            }

            Timber.d("size = $size")
            Timber.d("moreItemsCount = $moreItemsCount")

            notifyDataSetChanged()
        }
    }

    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView as TextView
    }

    class AttachmentItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView as TextView
        var attachment: Attachment? = null

        private val fileIconDrawable: Drawable =
            itemView.context.resources.getDrawable(
                R.drawable.filetype_levels,
                itemView.context.theme
            )

        init {
            textView.setCompoundDrawablesWithIntrinsicBounds(fileIconDrawable, null, null, null)
        }

        internal fun bind(attachment: Attachment) {
            if (this.attachment != attachment) {
                textView.text = attachment.attachmentTitle

                when {
                    attachment.isImage() -> fileIconDrawable.level = 0
                    attachment.isPdf() -> fileIconDrawable.level = 1
                    attachment.isText() -> fileIconDrawable.level = 2
                    attachment.isDoc() -> fileIconDrawable.level = 3
                    attachment.isZip() -> fileIconDrawable.level = 4
                    attachment.isVideo() -> fileIconDrawable.level = 5
                    else -> fileIconDrawable.level = 6
                }
            }

            this.attachment = attachment
        }
    }
}