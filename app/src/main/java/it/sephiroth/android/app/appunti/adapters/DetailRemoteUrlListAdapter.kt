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
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import it.sephiroth.android.app.appunti.ext.loadThumbnail
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import kotlinx.android.synthetic.main.appunti_detail_remoteurl_item.view.*
import kotlinx.android.synthetic.main.appunti_detail_remoteurl_others.view.*
import timber.log.Timber

class DetailRemoteUrlListAdapter(private var activity: DetailActivity) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_OTHERS = 1
        private const val MAX_ITEMS_DISPLAY = 5
    }

    private var expanded: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var deleteAction: ((RemoteUrl) -> (Unit))? = null
    var linkClickAction: ((RemoteUrl) -> (Unit))? = null

    private val data: MutableList<RemoteUrl> = mutableListOf()
    private val inflater: LayoutInflater by lazy { LayoutInflater.from(activity) }
    private val cardColor: Int by lazy { activity.theme.getColor(activity, android.R.attr.windowBackground) }

    private fun hasMore(): Boolean = data.size > MAX_ITEMS_DISPLAY

    private fun getOthersItemCount(): Int {
        return if (hasMore()) data.size - MAX_ITEMS_DISPLAY
        else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view = inflater.inflate(R.layout.appunti_detail_remoteurl_item, parent, false).apply {
                (this as CardView).setCardBackgroundColor(cardColor)
            }
            ItemUrlViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.appunti_detail_remoteurl_others, parent, false)
            OthersViewHolder(view)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (hasMore()) {
            if (!expanded) {
                if (position == MAX_ITEMS_DISPLAY) TYPE_OTHERS
                else TYPE_ITEM
            } else {
                if (position == data.size) TYPE_OTHERS
                else TYPE_ITEM
            }
        } else {
            TYPE_ITEM
        }
    }

    override fun getItemCount(): Int {
        return if (hasMore()) {
            if (!expanded) (MAX_ITEMS_DISPLAY + 1)
            else data.size + 1
        } else {
            data.size
        }
    }

    override fun onBindViewHolder(baseHolder: RecyclerView.ViewHolder, position: Int) {
        if (baseHolder.itemViewType == TYPE_ITEM) {
            val holder = baseHolder as ItemUrlViewHolder
            val remoteUrl = data[position]
            holder.bind(remoteUrl)

            holder.itemView.setOnClickListener {
                linkClickAction?.invoke(remoteUrl)
            }

            holder.remoteUrlRemoveButton.setOnClickListener {
                deleteAction?.invoke(remoteUrl)
            }
        } else {
            val holder = baseHolder as OthersViewHolder
            holder.bind(expanded, getOthersItemCount())

            holder.itemView.setOnClickListener {
                expanded = !expanded
            }
        }
    }

    fun update(remoteUrls: List<RemoteUrl>?) {
        doOnMainThread {
            data.clear()
            data.addAll(remoteUrls ?: listOf())
            notifyDataSetChanged()
        }
    }

    class OthersViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.remoteUrlOtherTitle

        fun bind(expanded: Boolean, others: Int) {
            if (expanded) {
                textView.setText(R.string.remote_url_others_less)
            } else {
                textView.text = itemView.context.resources.getQuantityString(R.plurals.remote_url_others_count, others, others)
            }
        }
    }

    class ItemUrlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val remoteUrlImage: ImageView = itemView.remoteUrlImage
        internal val remoteUrlRemoveButton: View = itemView.remoteUrlRemoveButton
        private val remoteUrlTitle: TextView = itemView.remoteUrlTitle
        private val remoteUrlDescription: TextView = itemView.remoteUrlDescription
        private var remoteUrl: RemoteUrl? = null

        fun bind(remoteUrl: RemoteUrl) {
            this.remoteUrl = remoteUrl
            remoteUrlTitle.text = remoteUrl.remoteUrlTitle
            remoteUrlDescription.text = remoteUrl.remoteUrlDescription
            remoteUrl.loadThumbnail(itemView.context, remoteUrlImage)
        }
    }
}