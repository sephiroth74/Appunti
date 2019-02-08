package it.sephiroth.android.app.appunti.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.isLightTheme
import it.sephiroth.android.app.appunti.utils.EntriesDiffCallback
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.main_item_list_entry.view.*
import timber.log.Timber
import kotlin.math.max

class ItemEntryListAdapter(private val context: Context,
                           private var values: MutableList<EntryItem>,
                           private val selectionCallback: ((BaseViewHolder, Int) -> (Boolean))?) :
        RecyclerView.Adapter<ItemEntryListAdapter.BaseViewHolder>() {

    private var cardBackgroundColorDefault: ColorStateList? = null
    private var textColorDefault: ColorStateList? = null
    private var textColorInverse: ColorStateList? = null
    private val categoryColors = ResourceUtils.getCategoryColors(context)

    private var cardForegroundStroke: Drawable
    private var cardForegroundNoStroke: Drawable

    companion object {
        val TYPE_EMPTY = EntryItem.ItemType.EMPTY.ordinal
        val TYPE_ENTRY = EntryItem.ItemType.ENTRY.ordinal
        val TYPE_PINNED = EntryItem.ItemType.PINNED.ordinal
        val TYPE_ARCHIVED = EntryItem.ItemType.ARCHIVED.ordinal
        val TYPE_DELETED = EntryItem.ItemType.DELETED.ordinal
        val TYPE_OTHERS = EntryItem.ItemType.OTHERS.ordinal

    }

    private var valuesCopy: MutableList<EntryItem>

    var itemClickListener: ((BaseViewHolder) -> (Unit))? = null
    var itemLongClickListener: ((ItemEntryListAdapter, BaseViewHolder) -> (Boolean))? = null

    init {
        valuesCopy = ArrayList(values)

        val isLightTheme = context.isLightTheme()
        textColorInverse =
                context.theme.getColorStateList(context,
                        if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary)

        cardForegroundNoStroke = context.getDrawable(
                R.drawable.appunti_card_selectable_item_background_no_stroke) !!
        cardForegroundStroke = context.getDrawable(
                R.drawable.appunti_card_selectable_item_background_with_stroke) !!

        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return item.type.ordinal
    }

    @SuppressLint("PrivateResource")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view: View

        return when (viewType) {
            TYPE_EMPTY -> {
                view = LayoutInflater.from(context)
                    .inflate(R.layout.item_list_empty, parent, false)
                val searchViewHeight = context.resources.getDimensionPixelSize(
                        R.dimen.search_height_view)
                val searchViewTopMargin = context.resources.getDimensionPixelSize(
                        R.dimen.appunti_main_search_view_margin_top)

                val params =
                        StaggeredGridLayoutManager
                            .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, searchViewHeight + searchViewTopMargin * 2)
                params.isFullSpan = true
                view.layoutParams = params
                BaseViewHolder(view)
            }

            TYPE_ENTRY -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.main_item_list_entry, parent, false)
                EntryViewHolder(view)
            }

            else -> {
                view = LayoutInflater.from(context)
                    .inflate(R.layout.appunti_main_list_pinned_entry, parent, false)
                val params = view.layoutParams as StaggeredGridLayoutManager.LayoutParams
                params.isFullSpan = true

                (view as TextView).setText(
                        when (viewType) {
                            TYPE_PINNED -> R.string.pinned
                            TYPE_DELETED -> R.string.deleted
                            TYPE_ARCHIVED -> R.string.archived
                            else -> R.string.others
                        }
                )
                BaseViewHolder(view)
            }
        }
    }


    override fun onBindViewHolder(baseHolder: BaseViewHolder, position: Int) {
        val item = getItem(position)

        if (baseHolder.itemViewType == TYPE_ENTRY) {
            val holder = baseHolder as EntryViewHolder
            val entryItem = item.entry !!

            holder.bind(entryItem, selectionCallback?.invoke(holder, position) ?: false)

            if (null == cardBackgroundColorDefault) {
                cardBackgroundColorDefault = holder.cardView.cardBackgroundColor
                textColorDefault = holder.titleTextView.textColors
            }

            var color = 0

            entryItem.category?.let {
                if (it.categoryColorIndex != 0) {
                    color = categoryColors[it.categoryColorIndex]
                }
                holder.categoryTextView.visibility = View.VISIBLE
            } ?: run {
                holder.categoryTextView.visibility = View.GONE
            }

            if (color != 0) {
                holder.cardView.setCardBackgroundColor(color)
                holder.cardView.foreground = cardForegroundNoStroke.constantState?.newDrawable()
                // val luminance = ColorUtils.calculateLuminance(color)

            } else {
                holder.cardView.setCardBackgroundColor(cardBackgroundColorDefault)
                holder.cardView.foreground = cardForegroundStroke.constantState?.newDrawable()
            }

            with(holder.itemView) {
                tag = entryItem
                setOnClickListener {
                    itemClickListener?.invoke(holder)
                }

                setOnLongClickListener {
                    itemLongClickListener?.invoke(this@ItemEntryListAdapter, holder) ?: false
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return values.size
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        return when (item.type) {
            EntryItem.ItemType.ENTRY -> item.entry !!.entryID.toLong()
            EntryItem.ItemType.EMPTY -> - 1
            EntryItem.ItemType.PINNED -> - 2
            EntryItem.ItemType.ARCHIVED -> - 3
            EntryItem.ItemType.DELETED -> - 4
            EntryItem.ItemType.OTHERS -> - 5
        }
    }

    private fun getItem(position: Int): EntryItem {
        return values[position]
    }

    fun update(newData: List<Entry>?) {
        Timber.i("[${currentThread()}] update: ${newData?.size}")

        val finalData = mutableListOf(EntryItem(null,
                EntryItem.ItemType.EMPTY))

        newData?.let { array ->
            finalData.addAll(array.map {
                EntryItem(it, EntryItem.ItemType.ENTRY)
            })
        }

        var firstNonPinned: Int
        val firstPinned: Int = finalData.indexOfFirst {
            it.entry?.entryPinned == 1 && it.entry?.entryDeleted == 0 && it.entry?.entryArchived == 0
        }

        val firstArchived: Int = finalData.indexOfFirst { it.entry?.entryArchived == 1 && it.entry?.entryDeleted == 0 }
        val firstDeleted: Int = finalData.indexOfFirst { it.entry?.entryDeleted == 1 }
        var addedCount = 0

        if (firstPinned > - 1) {
            finalData.add(firstPinned, EntryItem(null, EntryItem.ItemType.PINNED))
            addedCount ++
        }

        if (firstArchived > - 1) {
            finalData.add(firstArchived, EntryItem(null, EntryItem.ItemType.ARCHIVED))
            addedCount ++
        }

        if (firstDeleted > - 1) {
            finalData.add(firstDeleted, EntryItem(null, EntryItem.ItemType.DELETED))
            addedCount ++
        }

        Timber.v("firstPinned=$firstPinned, firstArchived=$firstArchived, firstDeleted=$firstDeleted")

        if (firstPinned > - 1 || firstArchived > - 1 || firstDeleted > - 1) {
            val firstIndex = max(firstPinned, max(firstArchived, firstDeleted))
            val subList = finalData.subList(firstIndex, finalData.size)

            firstNonPinned = if (firstPinned > firstArchived && firstPinned > firstDeleted) {
                // pinned
                subList.indexOfFirst { it.entry?.entryPinned == 0 }
            } else if (firstArchived > firstDeleted) {
                // archived
                subList.indexOfFirst { it.entry?.entryArchived == 0 }
            } else {
                // deleted
                subList.indexOfFirst { it.entry?.entryDeleted == 0 }
            }

            Timber.v("firstNonPinned=$firstNonPinned, final=${firstNonPinned + firstIndex + addedCount}")

            if (firstNonPinned > - 1) {
                finalData.add(firstNonPinned + firstIndex + addedCount, EntryItem(null, EntryItem.ItemType.OTHERS))
            }
        }

        val callback = EntriesDiffCallback(values, finalData)
        val result = DiffUtil.calculateDiff(callback, true)

        Timber.v("diffResult = $result")

        values = finalData
        valuesCopy = ArrayList(finalData)
        result.dispatchUpdatesTo(this)
    }

    fun filter(text: String?) {
        Timber.i("filter($text)")
        if (text.isNullOrEmpty()) {
            values = ArrayList(valuesCopy)
        } else {
            val result = valuesCopy.filter { value ->
                if (value.type == EntryItem.ItemType.ENTRY) {
                    value.entry !!.entryTitle !!.toLowerCase().indexOf(text) > - 1
                } else {
                    true
                }
            }
            values = ArrayList(result)
        }
        notifyDataSetChanged()
    }

    open class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class EntryViewHolder(view: View) : BaseViewHolder(view) {
        internal var entry: Entry? = null

        val titleTextView: TextView by lazy { view.id_title }
        val contentTextView: TextView by lazy { view.id_content }
        val categoryTextView: AppCompatTextView by lazy { view.chip }
        val cardView by lazy { view.id_card }

        fun bind(entry: Entry, isActivated: Boolean = false) {
            this.entry = entry
            itemView.isActivated = isActivated
            titleTextView.text = entry.entryTitle
            contentTextView.text = entry.entryText?.substring(0, 100)
            categoryTextView.text = entry.category?.categoryTitle
        }
    }

    class EntryItem(val entry: Entry?, val type: ItemType) {
        enum class ItemType { ENTRY, EMPTY, PINNED, ARCHIVED, DELETED, OTHERS }

        override fun equals(other: Any?): Boolean {
            if (other is EntryItem) {
                if (other.type != type) return false
                return when (type) {
                    ItemType.ENTRY -> entry == other.entry
                    else -> true
                }
            }
            return false
        }
    }
}