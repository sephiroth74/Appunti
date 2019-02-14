package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lapism.searchview.Search
import com.lapism.searchview.Search.SPEECH_REQUEST_CODE
import io.reactivex.android.schedulers.AndroidSchedulers
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.getColor
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.widget.ItemEntryListAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.appunti_entries_recycler_view.*
import kotlinx.android.synthetic.main.appunti_main_bottomappbar.*
import kotlinx.android.synthetic.main.appunti_search_view_toolbar.*
import timber.log.Timber
import java.util.*


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

        drawerLayout.setStatusBarBackgroundColor(theme.getColor(this, android.R.attr.windowBackground))

        setupRecyclerView()
        setupSearchView()
        seupNavigationView()
        setupItemTouchHelper()

        model.entries.observe(this, Observer {
            Timber.i("[${currentThread()}] entries changed")
            tracker?.clearSelection()
            adapter.update(it)
        })

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

        bottomAppBar.doOnNewNoteClick {
            startDetailActivity()
        }

        if (bottomAppBar.background is LayerDrawable) {
            val drawable: Drawable? = (bottomAppBar.background as LayerDrawable).findDrawableByLayerId(R.id.layer_background)
            drawable?.setTint(theme.getColor(this, android.R.attr.windowBackground))
        }

        model.initialize()
    }

    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_main

    private fun setupRecyclerView() {
        adapter = ItemEntryListAdapter(this, arrayListOf()) { holder, position -> tracker?.isSelected(position.toLong()) ?: false }

        layoutManager = itemsRecycler.layoutManager as StaggeredGridLayoutManager

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)

        adapter.itemClickListener = { holder ->
            if (holder.itemViewType == ItemEntryListAdapter.TYPE_ENTRY) {
                val entryItem = (holder as ItemEntryListAdapter.EntryViewHolder).entry
                if (entryItem != null) {
                    mActionMode?.let {
                        tracker?.let { tracker ->
                            if (tracker.isSelected(holder.adapterPosition.toLong())) {
                                tracker.deselect(holder.adapterPosition.toLong())
                            } else {
                                tracker.select(holder.adapterPosition.toLong(), entryItem)
                            }
                        }
                    } ?: run {
                        startDetailActivity(holder, entryItem)
//                        val newEntry = Entry(entryItem)
//                        newEntry.entryPinned = if (newEntry.entryPinned == 1) 0 else 1
//                        newEntry.save()
                    }
                }
            }
        }

        adapter.itemLongClickListener = { adapter, holder ->
            Timber.i("itemLongClickListener($holder, ${holder.itemViewType})")

            if (holder.itemViewType == ItemEntryListAdapter.TYPE_ENTRY) {

                val entryItem = (holder as ItemEntryListAdapter.EntryViewHolder).entry
                Timber.v("entryItem: $entryItem")
                Timber.v("actionMode: $mActionMode")

                if (mActionMode == null && entryItem != null) {
                    tracker = MultichoiceHelper(adapter)
                    tracker?.select(holder.adapterPosition.toLong(), entryItem)
                    mActionMode = startSupportActionMode(mActionModeCallback)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun seupNavigationView() {
        navigationView.model = model
        navigationView.setNavigationCategorySelectedListener { category ->
            Timber.i("setNavigationCategorySelectedListener($category)")
            model.group.setCategory(category)
            closeDrawerIfOpened()
        }

        navigationView.setNavigationItemSelectedListener { id ->
            when (id) {
                R.id.newLabel -> startCategoriesEditActivity(true)
                R.id.editLabels -> startCategoriesEditActivity(false)
                R.id.entriesArchived -> {
                    model.group.setIsArchived(true)
                    closeDrawerIfOpened()
                }
                R.id.entriesDeleted -> {
                    model.group.setDeleted(true)
                    closeDrawerIfOpened()
                }
                R.id.settings -> {
                    startActivity(Intent(this, PreferencesActivity::class.java))
                }
            }
        }
    }

    private fun setupItemTouchHelper() {
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {

            private var swipeBack = false

            private fun isSwipeEnabled(viewHolder: RecyclerView.ViewHolder): Boolean {
                when (viewHolder.itemViewType) {
                    ItemEntryListAdapter.TYPE_ENTRY -> {
                        val entry = (viewHolder as ItemEntryListAdapter.EntryViewHolder).entry
                        entry?.let { entry ->
                            if (entry.entryDeleted == 1 || entry.entryArchived == 1) return false
                            return true
                        } ?: run {
                            return false
                        }
                    }
                    else -> return false
                }
            }

            private fun isValidEntry(viewHolder: RecyclerView.ViewHolder): Boolean {
                when (viewHolder.itemViewType) {
                    ItemEntryListAdapter.TYPE_ENTRY -> {
                        val entry = (viewHolder as ItemEntryListAdapter.EntryViewHolder).entry
                        entry?.let { entry ->
                            return true
                        } ?: run {
                            return false
                        }
                    }
                    else -> return false
                }
            }


            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (null != mActionMode) return makeMovementFlags(0, 0)

                return if (isValidEntry(viewHolder))
                    makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                else
                    makeMovementFlags(0, 0)
            }

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (null != mActionMode) return

                if (viewHolder.itemViewType == ItemEntryListAdapter.TYPE_ENTRY) {
                    val entry = (viewHolder as ItemEntryListAdapter.EntryViewHolder).entry
                    entry?.let { entry ->
                        archiveEntries(listOf(entry))
                    }
                }
            }


            @SuppressLint("ClickableViewAccessibility")
            override fun onChildDraw(c: Canvas,
                                     recyclerView: RecyclerView,
                                     viewHolder: RecyclerView.ViewHolder,
                                     dX: Float,
                                     dY: Float,
                                     actionState: Int,
                                     isCurrentlyActive: Boolean) {

                if (actionState == ACTION_STATE_SWIPE) {
                    setTouchListener(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @SuppressLint("ClickableViewAccessibility")
            private fun setTouchListener(c: Canvas,
                                         recyclerView: RecyclerView,
                                         viewHolder: RecyclerView.ViewHolder,
                                         dX: Float, dY: Float,
                                         actionState: Int, isCurrentlyActive: Boolean) {

                recyclerView.setOnTouchListener { v, event ->
                    swipeBack = ! isSwipeEnabled(viewHolder)
                    Timber.v("isSwipeEnabled=$swipeBack")
//                    swipeBack = event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
                    false
                }
            }

            override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
                if (swipeBack) {
                    swipeBack = false
                    return 0
                }
                return super.convertToAbsoluteDirection(flags, layoutDirection)
            }

        })
        itemTouchHelper.attachToRecyclerView(itemsRecycler)
    }

    private fun setupSearchView() {
        searchView.setOnMicClickListener {
            Search.setVoiceSearch(this, "")
        }

        searchView.setOnLogoClickListener { toggleDrawer() }

        val textEdit = searchView.findViewById<View>(R.id.search_imageView_image)
        textEdit.isFocusable = false
        textEdit.isFocusableInTouchMode = false

        textEdit.setOnClickListener {
            Timber.i("onClick!!!")

            val intent = Intent(this, SearchableActivity::class.java)
            val intentOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(this, toolbar, "toolbar")

            startActivity(intent, intentOptions.toBundle())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Timber.i("onActivityResult(requestCode=$requestCode, resultCode=$resultCode)")

        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                if (data?.hasExtra(RecognizerIntent.EXTRA_RESULTS) == true) {
                    val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                    val intent = Intent(this, SearchableActivity::class.java)
                    intent.action = Intent.ACTION_SEARCH
                    intent.putExtra("query", results[0])
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
            }
        }
    }

    private fun startDetailActivity() {
        val intent = Intent(this, DetailActivity::class.java)
        intent.action = Intent.ACTION_CREATE_DOCUMENT
        startDetailActivityFromIntent(intent, null)
    }

    private fun startDetailActivity(holder: ItemEntryListAdapter.EntryViewHolder, entry: Entry) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.action = Intent.ACTION_EDIT
        intent.putExtra("entryID", entry.entryID)

        val elementsArray = arrayListOf<Pair<View, String>>(
                Pair(holder.titleTextView, "itemTitle"),
                Pair(holder.contentTextView, "itemText"),
                Pair(bottomAppBar, "bottomAppBar"))

        if (entry.category != null) {
            elementsArray.add(Pair(holder.categoryTextView, "itemCategory"))
        }

        val intentOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(this, *(elementsArray.toTypedArray()))
//        val intentOptions = null
        startDetailActivityFromIntent(intent, intentOptions)
    }

    private fun startDetailActivityFromIntent(intent: Intent, intentOptions: ActivityOptionsCompat?) {
        startActivity(intent, intentOptions?.toBundle())
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
        if (! drawerLayout.isDrawerOpen(navigationView)) drawerLayout.openDrawer(navigationView)
        else drawerLayout.closeDrawer(navigationView)
    }

    private fun onEntriesDeleted(values: List<Entry>) {
        val mSnackbar =
                Snackbar
                    .make(constraintLayout,
                            resources.getQuantityString(R.plurals.entries_deleted_title, values.size, values.size),
                            Snackbar
                                .LENGTH_LONG)
                    .setAction(getString(R.string.undo_uppercase)) { restoreDeletedEntries(values) }
                    .setActionTextColor(theme.getColorStateList(this@MainActivity, R.attr.colorError))

        mSnackbar.show()
    }

    private fun onEntriesArchived(values: List<Entry>) {
        val mSnackbar =
                Snackbar
                    .make(constraintLayout,
                            resources.getQuantityString(R.plurals.entries_archived_title, values.size, values.size),
                            Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo_uppercase)) {
                        DatabaseHelper.setEntriesArchived(values, false).subscribe()
                    }
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
            if (! isSelected(position)) {
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

                    val pinned = selection.values.indexOfFirst { it.entryPinned == 1 } > - 1
                    val unpinned = selection.values.indexOfFirst { it.entryPinned == 0 } > - 1

                    Timber.v("pinned=$pinned, unpinned=$unpinned")
                    updatePinnedMenuItem(actionMode.menu, pinned && (pinned && ! unpinned))
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
                        val pinned = tracker.selection.values.indexOfFirst { it.entryPinned == 1 } > - 1
                        val unpinned = tracker.selection.values.indexOfFirst { it.entryPinned == 0 } > - 1
                        DatabaseHelper.setEntriesPinned(tracker.selection.values.toList(), ! (pinned && (pinned && ! unpinned)))
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


}

