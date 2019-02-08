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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.dbflow5.structure.save
import com.google.android.material.snackbar.Snackbar
import com.lapism.searchview.Search
import com.lapism.searchview.Search.SPEECH_REQUEST_CODE
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.getColor
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
    private var mActionMode: ActionMode? = null
    private var tracker: MultichoiceHelper<Entry>? = null
    private lateinit var itemTouchHelper: ItemTouchHelper

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(MainViewModel::class.java)
        adapter = ItemEntryListAdapter(this, arrayListOf())

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)

        layoutManager = itemsRecycler.layoutManager as StaggeredGridLayoutManager

        drawerLayout.setStatusBarBackgroundColor(theme.getColor(this, android.R.attr.windowBackground))

        model.entries.observe(this, Observer {
            Timber.i("[${currentThread()}] entries changed")
            tracker?.clearSelection()
            adapter.update(it)
        })

        setupSearchView()
        seupNavigationView()
        setupItemTouchHelper()

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

    private fun setupItemTouchHelper() {
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {

                if (null != mActionMode) return makeMovementFlags(0, 0)

                return when (viewHolder.itemViewType) {
                    Item.ItemType.ENTRY.ordinal -> makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                    else -> makeMovementFlags(0, 0)
                }
            }

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (null != mActionMode) return

                if (viewHolder.itemViewType == Item.ItemType.ENTRY.ordinal) {
                    val entry = (viewHolder as ItemEntryListAdapter.EntryViewHolder).entry
                    entry?.let { entry ->
                        archiveEntries(listOf(entry))
                    }
                }
            }

        })
        itemTouchHelper.attachToRecyclerView(itemsRecycler)
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

        val textEdit = searchView.findViewById<TextView>(R.id.search_searchEditText)
        textEdit.isFocusable = false
        textEdit.isFocusableInTouchMode = false

        searchView.findViewById<View>(R.id.search_searchEditText).setOnClickListener {
            Timber.i("onClick!!!")
            startActivity(Intent(this, SearchableActivity::class.java))
        }
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

    private fun onEntriesDeleted(values: List<Entry>) {
        val mSnackbar =
                Snackbar
                    .make(constraintLayout, resources.getQuantityString(R.plurals.entries_deleted_title, values.size, values.size),
                            Snackbar
                                .LENGTH_LONG)
                    .setAction(getString(R.string.undo_uppercase)) { restoreDeletedEntries(values) }
                    .setActionTextColor(theme.getColorStateList(this@MainActivity, R.attr.colorError))

        mSnackbar.show()
    }

    private fun onEntriesArchived(values: List<Entry>) {
        val mSnackbar =
                Snackbar
                    .make(constraintLayout, resources.getQuantityString(R.plurals.entries_archived_title, values.size, values.size),
                            Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo_uppercase)) { DatabaseHelper.setEntriesArchived(values, false).subscribe() }
                    .setActionTextColor(theme.getColorStateList(this@MainActivity, R.attr.colorError))
        mSnackbar.show()
    }

    @SuppressLint("CheckResult")
    private fun archiveEntries(entries: List<Entry>) {
        DatabaseHelper
            .setEntriesArchived(entries, true)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { result, error ->
                error?.let {
                    Timber.e(error)
                } ?: run {
                    onEntriesArchived(entries)
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun deleteEntries(entries: List<Entry>) {
        DatabaseHelper.setEntriesDeleted(entries, true)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { success, error ->
                error?.let {
                    Timber.e("error=$error")
                } ?: run {
                    onEntriesDeleted(entries)
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun restoreDeletedEntries(entries: List<Entry>) {
        DatabaseHelper.setEntriesDeleted(entries, false)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { success, error ->
                error?.let {
                    Timber.e("error=$error")
                }
            }
    }

    // selection tracker

    class MultichoiceHelper<T>(val adapter: RecyclerView.Adapter<*>) {
        private val selectedPositions = hashMapOf<Long, T>()
        private var listener: (() -> Unit)? = null

        fun setListener(action: (() -> Unit)?) {
            listener = action
        }

        var selection: HashMap<Long, T>
            get() = selectedPositions
            private set(value) {}

        private fun notifyPosition(position: Long) {
            Timber.i("notifyPosition($position)")
            adapter.notifyItemChanged(position.toInt())
            listener?.invoke()
        }

        fun clearSelection() {
            if (selectedPositions.size > 0) {
                selectedPositions.clear()
                listener?.invoke()
                adapter.notifyDataSetChanged()
            }
        }

        fun select(position: Long, value: T) {
            if (!isSelected(position)) {
                selectedPositions[position] = value
                notifyPosition(position)
                Timber.v("select(position=$position), isSelected=${isSelected(position)}")
            }
        }

        fun deselect(position: Long): T? {
            var result: T? = null
            if (isSelected(position)) {
                result = selectedPositions.remove(position)
                notifyPosition(position)
            }
            return result
        }

        fun isSelected(position: Long): Boolean {
            return selectedPositions.containsKey(position)
        }
    }

    // actionmode callback

    private var mActionModeCallback: ActionMode.Callback = object : ActionMode.Callback {

        private fun onTrackerSelectionChanged(selection: HashMap<Long, Entry>, actionMode: ActionMode?) {
            Timber.i("onTrackerSelectionChanged()")
            actionMode?.let { actionMode ->
                if (selection.isEmpty()) {
                    actionMode.finish()
                } else {
                    actionMode.title = "${selection.size} Selected"

                    val pinned = selection.values.indexOfFirst { it.entryPinned == 1 } > -1
                    val unpinned = selection.values.indexOfFirst { it.entryPinned == 0 } > -1

                    Timber.v("pinned=$pinned, unpinned=$unpinned")
                    updatePinnedMenuItem(actionMode.menu, pinned && (pinned && !unpinned))
                }
            }
        }

        private fun updatePinnedMenuItem(menu: Menu?, checked: Boolean) {
            menu?.let { menu ->
                val menuItem = menu.findItem(R.id.menu_action_pin)
                if (checked) menuItem.setIcon(R.drawable.appunti_sharp_favourite_24_checked_selector)
                else menuItem.setIcon(R.drawable.appunti_sharp_favourite_24_unchecked_selector)
            }
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            Timber.i("onActionItemClicked: ${item.itemId}")

            when (item.itemId) {
                R.id.menu_action_pin -> {
                    tracker?.let { tracker ->
                        val pinned = tracker.selection.values.indexOfFirst { it.entryPinned == 1 } > -1
                        val unpinned = tracker.selection.values.indexOfFirst { it.entryPinned == 0 } > -1
                        DatabaseHelper.setEntriesPinned(tracker.selection.values.toList(), !(pinned && (pinned && !unpinned)))
                            .subscribe()
                    }
                }

                R.id.menu_action_delete -> {
                    tracker?.let { tracker ->
                        val entries = tracker.selection.values.toList()
                        deleteEntries(entries)
                    }
                }

                R.id.menu_action_archive -> {
                    tracker?.let { tracker ->
                        val entries = tracker.selection.values.toList()
                        archiveEntries(entries)
                    }

                }
            }

            return true
        }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            Timber.i("onCreateActionMode")
            menuInflater.inflate(R.menu.appunti_main_actionmode_menu, menu)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            toolbar.animate().alpha(0f).start()

            tracker?.let { tracker ->
                tracker.setListener {
                    onTrackerSelectionChanged(tracker.selection, mode)
                }
            }

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            onTrackerSelectionChanged(tracker?.selection ?: hashMapOf(), mode)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            Timber.i("onDestroyActionMode")
            tracker?.clearSelection()
            tracker = null

            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            toolbar.animate().alpha(1f).start()
            mActionMode = null
        }
    }


    inner class ItemEntryListAdapter(private val context: Context,
                                     private var values: MutableList<Item>) :
            RecyclerView.Adapter<ItemEntryListAdapter.BaseViewHolder>() {

        private var cardBackgroundColorDefault: ColorStateList? = null
        private var textColorDefault: ColorStateList? = null
        private var textColorInverse: ColorStateList? = null
        private val categoryColors = ResourceUtils.getCategoryColors(context)

        private var cardForegroundStroke: Drawable
        private var cardForegroundNoStroke: Drawable

        internal val TYPE_EMPTY = Item.ItemType.EMPTY.ordinal
        internal val TYPE_ENTRY = Item.ItemType.ENTRY.ordinal
        internal val TYPE_PINNED = Item.ItemType.PINNED.ordinal
        internal val TYPE_NON_PINNED = Item.ItemType.NON_PINNED.ordinal

        private var valuesCopy: MutableList<Item>

        init {
            valuesCopy = ArrayList(values)

            val isLightTheme = context.isLightTheme()
            textColorInverse =
                    context.theme.getColorStateList(context,
                            if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary)

            cardForegroundNoStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_no_stroke)!!
            cardForegroundStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_with_stroke)!!

            setHasStableIds(true)
        }

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return item.type.ordinal
        }

        @SuppressLint("PrivateResource")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val view: View
            val holder: BaseViewHolder

            if (viewType == TYPE_EMPTY) {
                view = LayoutInflater.from(context).inflate(R.layout.item_list_empty, parent, false)
                val searchViewHeight = context.resources.getDimensionPixelSize(R.dimen.search_height_view)
                val searchViewTopMargin = context.resources.getDimensionPixelSize(R.dimen.appunti_main_search_view_margin_top)

                val params =
                        StaggeredGridLayoutManager.LayoutParams(MATCH_PARENT, searchViewHeight + searchViewTopMargin * 2)
                params.isFullSpan = true
                view.layoutParams = params
                holder = BaseViewHolder(view)
            } else if (viewType == TYPE_PINNED || viewType == TYPE_NON_PINNED) {
                view = LayoutInflater.from(context).inflate(R.layout.appunti_main_list_pinned_entry, parent, false)
                val params = view.layoutParams as StaggeredGridLayoutManager.LayoutParams
                params.isFullSpan = true
                (view as TextView).text = if (viewType == TYPE_PINNED) getString(R.string.pinned) else getString(R.string.others)
                holder = BaseViewHolder(view)

            } else {
                view = LayoutInflater.from(parent.context).inflate(R.layout.main_item_list_entry, parent, false)
                holder = EntryViewHolder(view)
            }
            return holder
        }

        override fun onBindViewHolder(baseHolder: BaseViewHolder, position: Int) {
            val item = getItem(position)

            if (baseHolder.itemViewType == TYPE_ENTRY) {
                val holder = baseHolder as EntryViewHolder
                val entryItem = item.entry!!

                holder.bind(entryItem, tracker?.isSelected(position.toLong()) ?: false)

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
                        Timber.d("card clicked! actionMode=$mActionMode")

                        mActionMode?.let {
                            tracker?.let { tracker ->
                                if (tracker.isSelected(holder.adapterPosition.toLong())) {
                                    tracker.deselect(holder.adapterPosition.toLong())
                                } else {
                                    tracker.select(holder.adapterPosition.toLong(), entryItem)
                                }
                            }
                        } ?: run {
                            val newEntry = Entry(entryItem)
                            newEntry.entryPinned = if (newEntry.entryPinned == 1) 0 else 1
                            newEntry.save()
                        }

                    }

                    setOnLongClickListener {
                        Timber.i("onLongClick. actionMode=$mActionMode")
                        if (mActionMode == null) {
                            tracker = MultichoiceHelper(this@ItemEntryListAdapter)
                            tracker?.select(holder.adapterPosition.toLong(), entryItem)
                            mActionMode = startSupportActionMode(mActionModeCallback)
                            true
                        } else {
                            false
                        }
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

        open inner class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view)

        inner class EntryViewHolder(view: View) : BaseViewHolder(view) {
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

