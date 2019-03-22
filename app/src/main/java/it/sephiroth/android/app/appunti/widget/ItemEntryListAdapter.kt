package it.sephiroth.android.app.appunti.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.circularreveal.cardview.CircularRevealCardView
import io.reactivex.schedulers.Schedulers
import isTablet
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.getSummary
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import it.sephiroth.android.library.kotlin_extensions.content.res.getColorStateList
import it.sephiroth.android.library.kotlin_extensions.content.res.isPortrait
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnScheduler
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import kotlinx.android.synthetic.main.appunti_recycler_main_entry_item.view.*
import org.threeten.bp.Instant
import timber.log.Timber

class ItemEntryListAdapter(
    private val context: Activity,
    private var values: MutableList<EntryItem>,
    private var contextMarginTop: Int,
    private var contextMarginBottom: Int,
    private val selectionCallback: ((BaseViewHolder, Int) -> (Boolean))?
) :
    RecyclerView.Adapter<ItemEntryListAdapter.BaseViewHolder>() {

    private var cardBackgroundColorDefault: ColorStateList? = null
    private var textColorDefault: ColorStateList? = null
    private var textColorInverse: ColorStateList? = null
    private val categoryColors = ResourceUtils.getCategoryColors(context)

    private var cardForegroundStroke: Drawable
    private var cardForegroundNoStroke: Drawable

    var spanCount: Int = 1
        set(value) {
            field = value
            entryFullSpan =
                (!context.resources.isTablet && value > 1) || (context.resources.isTablet && context.resources.isPortrait)
        }

    private var entryFullSpan =
        (!context.resources.isTablet && spanCount > 1) || (context.resources.isTablet && context.resources.isPortrait)

    companion object {
        var NOW: Instant = Instant.now()

        val TYPE_EMPTY_START = EntryItem.ItemType.EMPTY_START.ordinal
        val TYPE_EMPTY_END = EntryItem.ItemType.EMPTY_END.ordinal
        val TYPE_ENTRY = EntryItem.ItemType.ENTRY.ordinal
        val TYPE_PINNED = EntryItem.ItemType.PINNED.ordinal
        val TYPE_ARCHIVED = EntryItem.ItemType.ARCHIVED.ordinal
        val TYPE_DELETED = EntryItem.ItemType.DELETED.ordinal
        val TYPE_OTHERS = EntryItem.ItemType.OTHERS.ordinal

    }

    var itemClickListener: ((BaseViewHolder) -> (Unit))? = null
    var itemLongClickListener: ((ItemEntryListAdapter, BaseViewHolder) -> (Boolean))? = null

    init {
        val isLightTheme = !SettingsManager.getInstance(context).darkTheme
        textColorInverse =
            context.theme.getColorStateList(
                context,
                if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary
            )

        cardForegroundNoStroke = context.getDrawable(
            R.drawable.appunti_card_selectable_item_background_no_stroke
        )!!
        cardForegroundStroke = context.getDrawable(
            R.drawable.appunti_card_selectable_item_background_with_stroke
        )!!

        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return item.type.ordinal
    }

    @SuppressLint("PrivateResource")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view: View
        val inflater = LayoutInflater.from(context)

        return when (viewType) {
            TYPE_EMPTY_START -> {
                view = inflater.inflate(R.layout.item_list_empty, parent, false)
                val searchViewHeight = context.resources.getDimensionPixelSize(
                    R.dimen.search_height_view
                )
                val searchViewTopMargin = context.resources.getDimensionPixelSize(
                    R.dimen.appunti_main_search_view_margin_top
                )

                val marginTop =
                    searchViewHeight + (searchViewTopMargin * 2) + contextMarginTop + context.resources.getDimensionPixelOffset(
                        R.dimen.appunti_main_recycler_margins_vertical
                    )

                val params =
                    StaggeredGridLayoutManager
                        .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, marginTop).also { it.isFullSpan = true }
                BaseViewHolder(view, params)
            }

            TYPE_EMPTY_END -> {
                view = inflater.inflate(R.layout.item_list_empty, parent, false)
                val marginBottom = if (contextMarginBottom > 0) contextMarginBottom else 0

                val params =
                    StaggeredGridLayoutManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        marginBottom + context.resources.getDimensionPixelOffset(R.dimen.appunti_main_recycler_margins_vertical)
                    ).also { it.isFullSpan = true }
                BaseViewHolder(view, params)
            }

            TYPE_ENTRY -> {
                view = inflater.inflate(R.layout.appunti_recycler_main_entry_item, parent, false)
                EntryViewHolder(view)
            }

            else -> {
                view = inflater.inflate(R.layout.appunti_recycler_main_label_entry_item, parent, false)
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

                BaseViewHolder(view, params)
            }
        }
    }


    override fun onBindViewHolder(baseHolder: BaseViewHolder, position: Int) {
        val item = getItem(position)

        if (baseHolder.itemViewType == TYPE_ENTRY) {
            Timber.v("onBindViewHolder($position)")
            val holder = baseHolder as EntryViewHolder
            val entryItem = item.entry!!
            var color = 0

            if (entryFullSpan) {
                if (position > 0) {
                    val prevItemType = getItemViewType(position - 1)
                    if (prevItemType == TYPE_PINNED || prevItemType == TYPE_OTHERS || prevItemType == TYPE_ARCHIVED || prevItemType == TYPE_DELETED) {
                        baseHolder.isFullSpan = getItemViewType(position + 1) != baseHolder.itemViewType
                    } else {
                        baseHolder.isFullSpan = false
                    }
                } else {
                    baseHolder.isFullSpan = false
                }
            }

            holder.bind(entryItem, searchText, selectionCallback?.invoke(holder, position) ?: false)

            if (null == cardBackgroundColorDefault) {
                cardBackgroundColorDefault = holder.cardView.cardBackgroundColor
                textColorDefault = holder.titleTextView.textColors
            }

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
                    Timber.v("onClick!!")

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
            EntryItem.ItemType.ENTRY -> item.entry!!.entryID
            EntryItem.ItemType.EMPTY_START -> -1
            EntryItem.ItemType.EMPTY_END -> -2
            EntryItem.ItemType.PINNED -> -3
            EntryItem.ItemType.ARCHIVED -> -4
            EntryItem.ItemType.DELETED -> -5
            EntryItem.ItemType.OTHERS -> -6
        }
    }

    private fun getItem(position: Int): EntryItem {
        return values[position]
    }

    private var searchText: String? = null

    data class SearchIndex(val index: Int, val type: EntryItem.ItemType)

    fun update(newData: List<Entry>?, searchQuery: String? = null) {
        Timber.i("[${currentThread()}] update: ${newData?.size}")

        doOnScheduler(Schedulers.single()) {
            Timber.v("doOnScheduler[${currentThread()}]")
            NOW = Instant.now()

            val finalData =
                (newData?.map { EntryItem(it, EntryItem.ItemType.ENTRY) }?.toMutableList() ?: mutableListOf()).apply {
                    add(0, EntryItem(null, EntryItem.ItemType.EMPTY_START))
                }

            val firstPinned = SearchIndex(finalData.indexOfFirst {
                it.entry?.entryPinned == 1 && it.entry.entryDeleted == 0 && it.entry.entryArchived == 0
            }, EntryItem.ItemType.PINNED)

            val firstArchived = SearchIndex(
                finalData.indexOfFirst { it.entry?.entryArchived == 1 && it.entry.entryDeleted == 0 },
                EntryItem.ItemType.ARCHIVED
            )

            val firstDeleted = SearchIndex(
                finalData.indexOfFirst { it.entry?.entryDeleted == 1 }, EntryItem.ItemType.DELETED
            )

            val arrayIndex = arrayOf(firstPinned, firstArchived, firstDeleted).apply {
                sortBy { searchIndex -> searchIndex.index }
            }

            Timber.v("arrayIndex: $arrayIndex")

            var addedCount = 0

            for (item in arrayIndex) {
                val index = item.index
                val type = item.type

                Timber.v("index=$index, type=$type")

                if (index > -1) {
                    finalData.add(index + addedCount, EntryItem(null, type))
                    addedCount++
                }
            }

            Timber.v("firstPinned=$firstPinned, firstArchived=$firstArchived, firstDeleted=$firstDeleted")
            Timber.v("added count: $addedCount")

            if (addedCount > 0) {
                val firstIndex = arrayIndex.last().index + addedCount
                Timber.v("firstIndex=$firstIndex")

                val subList = finalData.subList(firstIndex, finalData.size)

                val firstNonPinned = when (arrayIndex.last().type) {
                    EntryItem.ItemType.PINNED -> subList.indexOfFirst { it.entry?.entryPinned == 0 }
                    EntryItem.ItemType.ARCHIVED -> subList.indexOfFirst { it.entry?.entryArchived == 0 }
                    EntryItem.ItemType.DELETED -> subList.indexOfFirst { it.entry?.entryDeleted == 0 }
                    else -> -1
                }

                Timber.v("firstNonPinned=$firstNonPinned, final=${firstNonPinned + addedCount}")

                if (firstNonPinned > -1) {
                    finalData.add(firstNonPinned + firstIndex, EntryItem(null, EntryItem.ItemType.OTHERS))
                }
            }

            finalData.add(EntryItem(null, EntryItem.ItemType.EMPTY_END))

            // val callback = EntriesDiffCallback(values, finalData)
            // val result = DiffUtil.calculateDiff(callback, true)

            values = finalData

            Timber.v("[${currentThread()}] updated completed in (${Instant.now().minusMillis(NOW.toEpochMilli()).toEpochMilli()})")

            doOnMainThread {
                if (searchText != searchQuery) {
                    searchText = searchQuery
                    notifyDataSetChanged()
                } else {

                    // https://stackoverflow.com/a/37110764
                    // result.dispatchUpdatesTo(this)
                    notifyDataSetChanged()
                }
            }

        }
    }

    open class BaseViewHolder(
        view: View, params: StaggeredGridLayoutManager.LayoutParams? = null
    ) : RecyclerView.ViewHolder(view) {

        init {
            params?.let { view.layoutParams = params }
        }

        private fun getLayoutParams() = itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams

        var isFullSpan: Boolean
            get() = getLayoutParams().isFullSpan
            set(value) {
                if (value != getLayoutParams().isFullSpan) {
                    val params = getLayoutParams()
                    params.isFullSpan = value
                    itemView.layoutParams = params
                }
            }
    }

    class EntryViewHolder(view: View) : BaseViewHolder(view, null) {
        internal var entry: Entry? = null

        val titleTextView: TextView by lazy { view.id_title }
        val contentTextView: TextView by lazy { view.id_content }
        val categoryTextView: AppCompatTextView by lazy { view.entryCategory }
        val cardView: CircularRevealCardView by lazy { view.id_card }
        private val alarmView: ImageView by lazy { view.id_alarm }
        private val attachmentView: ImageView by lazy { view.id_attachment }

        private val maxLines: Int by lazy { view.context.resources.getInteger(R.integer.list_items_max_lines_display) }
        private val maxChars: Int by lazy { view.context.resources.getInteger(R.integer.list_items_max_chars_display) }

        init {
            val context = categoryTextView.context
            categoryTextView.background = MaterialBackgroundUtils.categoryChip(context)
        }

        fun bind(entry: Entry, searchText: String?, isActivated: Boolean = false) {
            this.entry = entry
            itemView.isActivated = isActivated

            // TODO(maybe execute asyc)
            val entryTitle = SpannableStringBuilder.valueOf(entry.entryTitle)

            if (!searchText.isNullOrEmpty()) {
                val index = entry.entryTitle.toLowerCase().indexOf(searchText)

                if (index > -1) {
                    entryTitle[index, index + searchText.length] = BackgroundColorSpan(Color.YELLOW)
                    entryTitle[index, index + searchText.length] = ForegroundColorSpan(Color.BLACK)
                }
            }

            titleTextView.text = entryTitle.toSpannable()

            contentTextView.text = entry.getSummary(itemView.context, contentTextView.textSize, maxChars, maxLines)
            categoryTextView.text = entry.category?.categoryTitle

            alarmView.visibility = if (!entry.isReminderExpired(ItemEntryListAdapter.NOW)) View.VISIBLE else View.GONE
            attachmentView.visibility = if (entry.hasAttachments()) View.VISIBLE else View.GONE
        }
    }

    class EntryItem(val entry: Entry?, val type: ItemType) {
        enum class ItemType { ENTRY, EMPTY_START, PINNED, ARCHIVED, DELETED, OTHERS, EMPTY_END }

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

        override fun hashCode(): Int {
            var result = entry?.hashCode() ?: 0
            result = 31 * result + type.hashCode()
            return result
        }
    }
}