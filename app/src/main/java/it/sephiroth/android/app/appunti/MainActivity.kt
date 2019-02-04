package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.dbflow5.structure.save
import com.lapism.searchview.Search
import com.lapism.searchview.Search.SPEECH_REQUEST_CODE
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.isLightTheme
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.utils.EntriesDiffCallback
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.main_item_list_entry.view.*
import timber.log.Timber
import java.util.concurrent.TimeUnit


class MainActivity : AppuntiActivity() {

    lateinit var adapter: ItemEntryListAdapter

    private lateinit var model: MainViewModel
    private lateinit var layoutManager: StaggeredGridLayoutManager
    private lateinit var tracker: SelectionTracker<Entry>
    private var mActionMode: ActionMode? = null

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(MainViewModel::class.java)
        adapter = ItemEntryListAdapter(this, arrayListOf())

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)

        layoutManager = itemsRecycler.layoutManager as StaggeredGridLayoutManager

        tracker = SelectionTracker
                .Builder<Entry>(
                        "mySelection",
                        itemsRecycler,
                        adapter.KeyProvider(),
                        EntryItemDetailsLookup(itemsRecycler),
                        StorageStrategy.createParcelableStorage<Entry>(Entry::class.java))
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build()

        tracker.addObserver(mSelectionObserver)

        model.entries.observe(this, Observer {
            Timber.i("[${currentThread()}] entries changed")
            adapter.update(it)
        })

        setupSearchView()
        seupNavigationView()

        model.displayAsList.observe(this, Observer {
            Timber.i("displayAsList -> $it")
            if (it) {
                layoutManager.spanCount = resources.getInteger(R.integer.list_items_columns_list)
            } else {
                layoutManager.spanCount = resources.getInteger(R.integer.list_items_columns_grid)
            }

            bottomAppBar.setDisplayAsList(it)
        })

        bottomAppBar.doOnDisplayAsListChanged { value ->
            model.setDisplayAsList(value)
        }
    }

    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.main_activity

    private fun seupNavigationView() {
        navigationView.model = model
        navigationView.setNavigationCategorySelectedListener { category ->
            model.currentCategory = category
            closeDrawerIfOpened()
        }

        navigationView.setNavigationItemSelectedListener { id ->
            when (id) {
                R.id.newLabel -> startCategoriesEditActivity(true)
                R.id.editLabels -> startCategoriesEditActivity(false)
                R.id.settings -> {
                    startActivity(Intent(this, PreferencesActivity::class.java))
                }
            }
        }
    }

    private fun setupSearchView() {
        searchView.setOnMicClickListener {
            Search.setVoiceSearch(this, "")
        }

        searchView.setOnLogoClickListener { toggleDrawer() }
        searchView.setOnQueryTextListener(object : Search.OnQueryTextListener {

            var timer: Disposable? = null

            override fun onQueryTextSubmit(query: CharSequence?): Boolean {
                Timber.i("onQueryTextSubmit($query)")
                searchView.close()
                return true
            }

            override fun onQueryTextChange(newText: CharSequence?) {
                Timber.i("onQueryTextChange: $newText")

                timer?.dispose()
                timer = Observable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            adapter.filter(newText.toString())
                        }
            }

        })

        searchView.setOnOpenCloseListener(object : Search.OnOpenCloseListener {
            override fun onOpen() {
                Timber.i("onOpen")
            }

            override fun onClose() {
                Timber.i("onClose")
            }

        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                if (data?.hasExtra(RecognizerIntent.EXTRA_RESULTS) == true) {
                    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    Timber.v("results: $results")
                    searchView.setText(results[0])
                }
            }
        }
    }


    private fun startCategoriesEditActivity(newCategory: Boolean = false) {
        val intent = Intent(this, CategoriesEditActivity::class.java)
        if (newCategory) intent.putExtra(CategoriesEditActivity.ASK_NEW_CATEGORY_STARTUP, true)
        startActivity(intent)
    }

    private fun closeDrawerIfOpened() {
        if (drawerLayout.isDrawerOpen(navigationView))
            drawerLayout.closeDrawer(navigationView)
    }

    private fun toggleDrawer() {
        if (!drawerLayout.isDrawerOpen(navigationView)) drawerLayout.openDrawer(navigationView)
        else drawerLayout.closeDrawer(navigationView)
    }

    // selection tracker

    private var mSelectionObserver = object : SelectionTracker.SelectionObserver<Long>() {
        override fun onSelectionChanged() {
            super.onSelectionChanged()

            val size = tracker.selection.size()
            if (size < 1) {
                mActionMode?.finish()
            } else {
                mActionMode?.title = "$size Selected"
            }
        }
    }

    // actionmode callback

    private var mActionModeCallback = object : ActionMode.Callback {

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            Timber.i("onActionItemClicked: ${item?.itemId}")
            return true
        }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menuInflater.inflate(R.menu.appunti_main_actionmode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            Timber.i("onDestroyActionMode")
            tracker.clearSelection()
            mActionMode = null
        }
    }


    class EntryItemDetailsLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<Entry>() {

        override fun getItemDetails(event: MotionEvent): ItemDetails<Entry>? {
            val view = recyclerView.findChildViewUnder(event.x, event.y)
            if (view != null) {
                return (recyclerView.getChildViewHolder(view) as ItemEntryListAdapter.ViewHolder).getItemDetails()
            }
            return null
        }
    }


    inner class ItemEntryListAdapter(private val context: Context,
                                     private var values: MutableList<Item>) :
            RecyclerView.Adapter<ItemEntryListAdapter.ViewHolder>() {

        private var cardBackgroundColorDefault: ColorStateList? = null
        private var textColorDefault: ColorStateList? = null
        private var textColorInverse: ColorStateList? = null
        private val categoryColors = ResourceUtils.getCategoryColors(context)

        private var cardForegroundStroke: Drawable
        private var cardForegroundNoStroke: Drawable

        private val TYPE_EMPTY = Item.ItemType.EMPTY.ordinal
        private val TYPE_ENTRY = Item.ItemType.ENTRY.ordinal
        private val TYPE_PINNED = Item.ItemType.PINNED.ordinal
        private val TYPE_NON_PINNED = Item.ItemType.NON_PINNED.ordinal

        private var valuesCopy: MutableList<Item>

        inner class KeyProvider : ItemKeyProvider<Entry>(SCOPE_MAPPED) {
            override fun getKey(position: Int): Entry? {
                Timber.i("getKey($position)")
                return getItem(position).entry
            }

            override fun getPosition(key: Entry): Int {
                Timber.i("getPosition($key)")
                return values.indexOfFirst { it.entry == key }
            }

        }

        init {
            valuesCopy = ArrayList(values)

            val isLightTheme = context.isLightTheme()
            textColorInverse =
                    context.theme.getColorStateList(context, if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary)

            cardForegroundNoStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_no_stroke)!!
            cardForegroundStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_with_stroke)!!

            setHasStableIds(true)
        }

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return item.type.ordinal
        }

        @SuppressLint("PrivateResource")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view: View

            if (viewType == TYPE_EMPTY) {
                view = LayoutInflater.from(context).inflate(R.layout.item_list_empty, parent, false)
                val searchViewHeight = context.resources.getDimensionPixelSize(R.dimen.search_height_view)
                val searchViewTopMargin = context.resources.getDimensionPixelSize(R.dimen.appunti_main_search_view_margin_top)

                val params =
                        StaggeredGridLayoutManager.LayoutParams(MATCH_PARENT, searchViewHeight + searchViewTopMargin * 2)
                params.isFullSpan = true
                view.layoutParams = params
            } else if (viewType == TYPE_PINNED) {
                view = LayoutInflater.from(context).inflate(R.layout.appunti_main_list_pinned_entry, parent, false)
                (view as TextView).text = getString(R.string.pinned)
            } else if (viewType == TYPE_NON_PINNED) {
                view = LayoutInflater.from(context).inflate(R.layout.appunti_main_list_pinned_entry, parent, false)
                (view as TextView).text = getString(R.string.others)
            } else {
                view = LayoutInflater.from(parent.context).inflate(R.layout.main_item_list_entry, parent, false)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            val itemId = getItemId(position)

            Timber.i("entry: $item")

            if (holder.itemViewType == TYPE_ENTRY) {
                val entryItem = item.entry!!

                holder.bind(entryItem, tracker.isSelected(entryItem))

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
                        Timber.d("card clicked!")

                        mActionMode?.let {
                            if (tracker.isSelected(entryItem)) {
                                tracker.deselect(entryItem)
                            } else {
                                tracker.select(entryItem)
                            }
                        } ?: run {
                            val newEntry = Entry(entryItem)
                            newEntry.entryPinned = if (newEntry.entryPinned == 1) 0 else 1
                            newEntry.save()
                        }

                    }

                    setOnLongClickListener {
                        if (mActionMode == null) {
                            mActionMode = startSupportActionMode(mActionModeCallback)
                        }
                        true
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
                Item.ItemType.ENTRY -> item.entry!!.entryID.toLong()
                Item.ItemType.EMPTY -> -1
                Item.ItemType.PINNED -> -2
                Item.ItemType.NON_PINNED -> -3
            }
        }

        private fun getItem(position: Int): Item {
            return values[position]
        }

        fun update(newData: List<Entry>?) {
            Timber.i("update: ${newData?.size}")

            val finalData = mutableListOf(Item(null, Item.ItemType.EMPTY))

            newData?.let { array ->
                finalData.addAll(array.map { Item(it, Item.ItemType.ENTRY) })
            }

            var firstNonPinned: Int
            val firstPinned: Int = finalData.indexOfFirst { it.entry?.entryPinned == 1 }

            if (firstPinned > -1) {
                finalData.add(firstPinned, Item(null, Item.ItemType.PINNED))
                firstNonPinned = finalData.indexOfFirst { it.entry?.entryPinned == 0 }
                if (firstNonPinned > -1) finalData.add(firstNonPinned, Item(null, Item.ItemType.NON_PINNED))
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
                    if (value.type == Item.ItemType.ENTRY) {
                        value.entry!!.entryTitle!!.toLowerCase().indexOf(text) > -1
                    } else {
                        true
                    }
                }
                values = ArrayList(result)
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private var entry: Entry? = null

            val titleTextView: TextView by lazy { view.id_title }
            val contentTextView: TextView by lazy { view.id_content }
            val categoryTextView: AppCompatTextView by lazy { view.chip }
            val cardView: CardView by lazy { view.id_card }

            fun getItemDetails(): ItemDetailsLookup.ItemDetails<Entry> =
                    object : ItemDetailsLookup.ItemDetails<Entry>() {
                        override fun getPosition(): Int = adapterPosition
                        override fun getSelectionKey(): Entry? = entry
                    }


            fun bind(entry: Entry, isActivated: Boolean = false) {
                this.entry = entry
                itemView.isActivated = isActivated
                titleTextView.text = entry.entryTitle
                contentTextView.text = entry.entryText?.substring(0, 100)
                categoryTextView.text = entry.category?.categoryTitle
            }
        }
    }

    class Item(val entry: Entry?, val type: ItemType) {
        enum class ItemType { ENTRY, EMPTY, PINNED, NON_PINNED }

        override fun equals(other: Any?): Boolean {
            if (other is Item) {
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

