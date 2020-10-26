package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.CustomEvent
import com.crashlytics.android.answers.SearchEvent
import com.google.android.material.snackbar.Snackbar
import com.hunter.library.debug.HunterDebug
import com.lapism.searchview.Search
import com.lapism.searchview.Search.SPEECH_REQUEST_CODE
import com.leinardi.android.speeddial.SpeedDialActionItem
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.IntentUtils
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import it.sephiroth.android.app.appunti.widget.ItemEntryListAdapter
import it.sephiroth.android.app.appunti.widget.MultiChoiceHelper
import it.sephiroth.android.app.appunti.widget.RecyclerNavigationView
import it.sephiroth.android.app.appunti.workers.RemoteUrlParserWorker
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor
import it.sephiroth.android.library.kotlin_extensions.content.res.getColorStateList
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import it.sephiroth.android.library.kotlin_extensions.view.setAnimationListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.appunti_entries_recycler_view.*
import kotlinx.android.synthetic.main.appunti_main_activity_no_results.*
import kotlinx.android.synthetic.main.appunti_main_drawer_navigation_content.*
import kotlinx.android.synthetic.main.appunti_search_view_toolbar.*
import timber.log.Timber
import java.util.*

class MainActivity : AudioRecordActivity(true) {

    // recycler view adapter
    lateinit var adapter: ItemEntryListAdapter

    // recycler view layout manager
    private lateinit var layoutManager: StaggeredGridLayoutManager

    // recycler view multi selection tracker
    private var tracker: MultiChoiceHelper<Entry>? = null

    // main activity view model
    private lateinit var model: MainViewModel

    private var mActionMode: ActionMode? = null

    // swipe helper for the recycler view
    private lateinit var itemTouchHelper: ItemTouchHelper

    // save instance state for the main recyclerview
    private var mListState: Parcelable? = null

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(MainViewModel::class.java)

        // Override the activity's theme when in multiwindow mode.
        coordinatorLayout.fitsSystemWindows = fitSystemWindows

        if (isFullScreen) {
            navigationView.setOnApplyWindowInsetsListener { _, insets ->
                Timber.w("applyWindowInsetsListener: $insets")

                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

                navigationbarHeight = insets.systemWindowInsetBottom
                statusbarHeight = insets.systemWindowInsetTop

                Timber.v("navigationbarHeight = $navigationbarHeight")
                Timber.v("statusbarHeight = $statusbarHeight")

                navigationView.setOnApplyWindowInsetsListener(null)

                initializeUI()
                initializeModel()

                insets.consumeSystemWindowInsets()
            }
        } else {
            initializeUI()
            initializeModel()
        }

        initializeWorkers()

//        drawerLayout.setStatusBarBackgroundColor(theme.getColor(this, android.R.attr.windowBackground))
//        DatabaseHelper.copyDatabase(this, getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!)
    }

    override fun onContentChanged() {
        super.onContentChanged()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val listState = itemsRecycler.layoutManager?.onSaveInstanceState()
        outState.putParcelable("LIST_STATE_KEY", listState)
    }

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)

        if (state != null)
            mListState = state.getParcelable("LIST_STATE_KEY")

    }

    override fun onPause() {
        super.onPause()
        speedDial.close()
    }

    override fun onResume() {
        super.onResume()

        mListState?.let {
            itemsRecycler.layoutManager?.onRestoreInstanceState(it)
        }

        mListState = null

        invalidateTheme()
    }

    override fun onNewIntent(intent: Intent?) {
        Timber.i("onNewIntent($intent)")
        super.onNewIntent(intent)

        if (intent?.action == ACTION_ENTRIES_BY_CATEGORY) {
            model.group.setCategoryID(intent.getLongExtra(KEY_CATEGORY_ID, 0))
        }
    }

    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_main

    private fun initializeUI() {
        initializeSystemUI()
        setupNoResultsView()
        setupRecyclerView()
        setupSearchView()
        seupNavigationView()
        setupItemTouchHelper()
        setupFloatingActionButton()

        setDisplayAsList(SettingsManager.getInstance(this).displayAsList)
        SettingsManager.getInstance(this).setOnDisplayAsListChanged { setDisplayAsList(it) }
    }

    private fun invalidateTheme() {
        try {
            val fabOpened = theme.getColor(this, R.attr.fabBackgroundColorOpened)
            val fabOpenedNow = speedDial.mainFabOpenedBackgroundColor

            if (fabOpenedNow != fabOpened) {
                Timber.v("SpeedDial theme need to be updated")
                val fabClosed = theme.getColor(this, R.attr.fabBackgroundColorClosed)
                if (fabClosed != 0) {
                    speedDial.mainFabOpenedBackgroundColor = fabOpened
                    speedDial.mainFabClosedBackgroundColor = fabClosed
                }
            }
        } catch (e: Resources.NotFoundException) {
            Timber.w(e, "a problem getting fabBackgroundColorOpened")
            Crashlytics.logException(e)
        }
    }

    private fun setDisplayAsList(value: Boolean) {
        Timber.i("setDisplayAsList($value)")
        val spanCount =
            if (value) resources.getInteger(R.integer.list_items_columns_list) else resources.getInteger(
                R.integer.list_items_columns_grid
            )

        adapter.spanCount = spanCount
        layoutManager.spanCount = spanCount
    }

    private fun initializeModel() {
        model.entries.observe(this, Observer {
            Timber.i("[${currentThread()}] entries changed")
            tracker?.clearSelection()

            // https://stackoverflow.com/a/37110764
            itemsRecycler.recycledViewPool.clear()

            adapter.update(it)

            // do not show the no results screen in case group is not empty and it's not
            // archived or with alarm category
            val shouldDisplayNoResultScreen =
                it.isNullOrEmpty() && !model.group.isReminder() && !model.group.isArchived()

            if (shouldDisplayNoResultScreen) {
                noResultsView.isVisible = true
            } else {
                noResultsView.isGone = true
            }
        })

        // handle current intent
        if (intent?.action == ACTION_ENTRIES_BY_CATEGORY) {
            model.group.setCategoryID(intent.getLongExtra(KEY_CATEGORY_ID, 0))
            answers.logCustom(
                CustomEvent("main.init")
                    .putCustomAttribute("fromCategory", 1)
                    .putCustomAttribute("darkTheme", if (isDarkTheme) 1 else 0)
            )
        } else {
            model.group.setCategoryID(null)
            answers.logCustom(
                CustomEvent("main.init")
                    .putCustomAttribute("fromCategory", 0)
                    .putCustomAttribute("darkTheme", if (isDarkTheme) 1 else 0)
            )
        }
    }

    private fun initializeSystemUI() {
        if (isFullScreen) {
            navigationBackground.layoutParams.height = navigationbarHeight
            statusbarBackground.layoutParams.height = statusbarHeight
            navigationBackground.visibility = View.VISIBLE
            statusbarBackground.visibility = View.VISIBLE

            val color = theme.getColor(this, android.R.attr.windowBackground)
            statusbarBackground.backgroundTintList = ColorStateList.valueOf(color)

        } else {
            navigationBackground.visibility = View.INVISIBLE
            statusbarBackground.visibility = View.INVISIBLE
        }
    }

    private fun initializeWorkers() {
        RemoteUrlParserWorker.createPeriodicWorker()
    }

    private fun setupFloatingActionButton() {
        val params = speedDial.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin += navigationbarHeight

        val list = mutableListOf<SpeedDialActionItem>(
            SpeedDialActionItem.Builder(
                R.id.fab_menu_new_text_note,
                R.drawable.sharp_text_fields_24
            )
                .setLabel(getString(R.string.new_text_note))
                .create(),
            SpeedDialActionItem.Builder(
                R.id.fab_menu_new_list_note,
                R.drawable.sharp_format_list_bulleted_24
            )
                .setLabel(getString(R.string.new_list_note))
                .create()
        )

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            list.add(
                SpeedDialActionItem.Builder(
                    R.id.fab_menu_new_audio_note,
                    R.drawable.sharp_play_circle_outline_24
                )
                    .setLabel("New Audio Note")
                    .create()
            )
        }

        speedDial.addAllActionItems(list.asReversed())

        speedDial.setOnActionSelectedListener {
            when (it.id) {
                R.id.fab_menu_new_text_note -> startDetailActivity(
                    Entry.EntryType.TEXT,
                    model.group.getCategoryID()
                )
                R.id.fab_menu_new_list_note -> startDetailActivity(
                    Entry.EntryType.LIST,
                    model.group.getCategoryID()
                )
                R.id.fab_menu_new_audio_note -> askForAudioPermission()
            }
            speedDial.close(true)
            true
        }
    }

    private fun setupNoResultsView() {
        createNewNoteButton.background = MaterialBackgroundUtils.materialButton(this)
        createNewNoteButton.setOnClickListener {
            startDetailActivity(Entry.EntryType.TEXT, model.group.getCategoryID())
        }
    }

    private fun setupRecyclerView() {
        var top = 0
        var bottom = 0
        if (isFullScreen) {
            bottom = navigationbarHeight
            top = statusbarHeight
        }

        Timber.v("top=$top, bottom=$bottom")

        adapter = ItemEntryListAdapter(this, arrayListOf(), top, bottom) { _, position ->
            tracker?.isSelected(position.toLong()) ?: false
        }

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
                    tracker = MultiChoiceHelper(adapter)
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
            model.group.setCategoryID(category?.categoryID)
            closeDrawerIfOpened()
        }

        navigationView.setNavigationItemSelectedListener { id ->
            Timber.i("setNavigationItemSelectedListener($id)")
            when (id) {
                RecyclerNavigationView.TYPE_LABEL_CATEGORY_ARCHIVED -> {
                    answers
                        .logCustom(
                            CustomEvent("main.navigationItemClick").putCustomAttribute(
                                "name",
                                "archived"
                            )
                        )

                    model.group.setIsArchived(true)
                    closeDrawerIfOpened()
                }

                RecyclerNavigationView.TYPE_LABEL_CATEGORY_REMINDER -> {
                    answers.logCustom(
                        CustomEvent("main.navigationItemClick").putCustomAttribute(
                            "name",
                            "reminder"
                        )
                    )
                    model.group.setWithReminder(true)
                    closeDrawerIfOpened()
                }

                RecyclerNavigationView.TYPE_LABEL_NEW_CATEGORY -> {
                    startCategoriesEditActivity(true)
                }

                RecyclerNavigationView.TYPE_LABEL_EDIT_CATEGORY -> {
                    startCategoriesEditActivity(false)
                }

                RecyclerNavigationView.TYPE_SETTINGS -> {
                    startActivity(IntentUtils.createPerferencesIntent(this))
                }
            }
        }

        navigationViewContent.setPadding(
            navigationViewContent.paddingLeft,
            statusbarHeight,
            navigationViewContent.paddingRight,
            navigationbarHeight
        )
    }

    private fun setupItemTouchHelper() {
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {

            private var swipeBack = false

            @Suppress("NAME_SHADOWING")
            private fun isSwipeEnabled(viewHolder: RecyclerView.ViewHolder): Boolean {
                when (viewHolder.itemViewType) {
                    ItemEntryListAdapter.TYPE_ENTRY -> {
                        val entry = (viewHolder as ItemEntryListAdapter.EntryViewHolder).entry
                        entry?.let { entry ->
                            if (entry.entryArchived == 1) return false
                            return true
                        } ?: run {
                            return false
                        }
                    }
                    else -> return false
                }
            }

            private fun isValidEntry(viewHolder: RecyclerView.ViewHolder): Boolean {
                return when (viewHolder.itemViewType) {
                    ItemEntryListAdapter.TYPE_ENTRY -> {
                        ((viewHolder as ItemEntryListAdapter.EntryViewHolder).entry != null)
                    }
                    else -> false
                }
            }


            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (null != mActionMode) return makeMovementFlags(0, 0)

                return if (isValidEntry(viewHolder))
                    makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                else
                    makeMovementFlags(0, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (null != mActionMode) return

                if (viewHolder.itemViewType == ItemEntryListAdapter.TYPE_ENTRY) {
                    (viewHolder as ItemEntryListAdapter.EntryViewHolder).entry?.let { entry ->
                        answers.logCustom(CustomEvent("main.itemSwiped"))
                        setEntriesArchived(listOf(entry), true)
                    }
                }
            }


            @SuppressLint("ClickableViewAccessibility")
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {

                if (actionState == ACTION_STATE_SWIPE) {
                    setTouchListener(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }

                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }

            @Suppress("UNUSED_PARAMETER")
            @SuppressLint("ClickableViewAccessibility")
            private fun setTouchListener(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {

                recyclerView.setOnTouchListener { _, _ ->
                    swipeBack = !isSwipeEnabled(viewHolder)
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
        val params = (searchView.layoutParams as ViewGroup.MarginLayoutParams)
        params.topMargin += statusbarHeight

        searchView.setOnMicClickListener {
            Search.setVoiceSearch(this, "")
            answers.logSearch(SearchEvent().putCustomAttribute("main.voiceSearch", 1))
        }

        searchView.setOnLogoClickListener {
            answers.logCustom(CustomEvent("main.toggleDrawer").putCustomAttribute("fromLogo", 1))
            toggleDrawer()
        }

        val textEdit = searchView.findViewById<View>(R.id.search_imageView_image)
        textEdit.isFocusable = false
        textEdit.isFocusableInTouchMode = false

        textEdit.setOnClickListener {
            val intentOptions =
                ActivityOptionsCompat.makeSceneTransitionAnimation(this, toolbar, "toolbar")
            startActivity(IntentUtils.createSearchableIntent(this), intentOptions.toBundle())
            answers.logSearch(SearchEvent().putCustomAttribute("text", 1))
        }
    }

    private fun showSnackBack(snackbar: Snackbar): Snackbar {
        val params = snackbar.view.layoutParams as CoordinatorLayout.LayoutParams

        params.setMargins(
            params.leftMargin,
            params.topMargin,
            params.rightMargin,
            params.bottomMargin + navigationbarHeight
        )

        snackbar.view.layoutParams = params
        snackbar.show()
        return snackbar
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Timber.i("onActivityResult(requestCode=$requestCode, resultCode=$resultCode)")

        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                if (data?.hasExtra(RecognizerIntent.EXTRA_RESULTS) == true) {
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
                        startActivity(IntentUtils.createSearchableIntent(this, results[0]))
                    }
                }
            }
        }
    }


    @HunterDebug
    override fun onAudioPermissionsDenied(shouldShowRequestPermissionRationale: Boolean) {
        if (shouldShowRequestPermissionRationale) {
            IntentUtils.showPermissionsDeniedDialog(
                this,
                R.string.permissions_audio_required_dialog_body
            )
        } else {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_SHORT).show()
        }
    }

    @HunterDebug
    override fun onAudioPermissionsGranted() {
        dispatchVoiceRecordingIntent()
    }

    override fun onAudioCaptured(data: Intent) {
        startDetailActivity(
            Entry.EntryType.TEXT,
            model.group.getCategoryID(),
            kotlin.Pair(IntentUtils.KEY_AUDIO_BUNDLE, data)
        )
    }

    @HunterDebug
    override fun onAudioCaptured(audioUri: Uri?, result: String?) {
    }


    private fun startDetailActivity(
        type: Entry.EntryType,
        categoryId: Long? = null,
        extra: kotlin.Pair<String, Intent>? = null
    ) {
        startDetailActivityFromIntent(
            IntentUtils.createNewEntryIntent(
                this,
                type,
                categoryId,
                extra
            ), null
        )

        answers.logCustom(
            CustomEvent("main.newNote").putCustomAttribute("type", type.name)
        )
    }

    private fun startDetailActivity(holder: ItemEntryListAdapter.EntryViewHolder, entry: Entry) {
        val intent = IntentUtils.createViewEntryIntent(this, entry.entryID)

        val elementsArray = arrayListOf<Pair<View, String>>(
            Pair(holder.titleTextView, "itemTitle"),
            Pair(holder.contentTextView, "itemText")
//            Pair(bottomAppBar, "bottomAppBar")
        )

        if (entry.category != null) {
            elementsArray.add(Pair(holder.categoryTextView, "itemCategory"))
        }

        val intentOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this,
            *(elementsArray.toTypedArray())
        )
        startDetailActivityFromIntent(intent, intentOptions)
    }

    private fun startDetailActivityFromIntent(
        intent: Intent,
        intentOptions: ActivityOptionsCompat?
    ) {
        startActivity(intent, intentOptions?.toBundle())
    }

    private fun startCategoriesEditActivity(newCategory: Boolean = false) {
        val intent = IntentUtils.Categories.Builder(this).apply {
            if (newCategory) createNewCategory()
        }.build()

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

    private fun onEntriesArchived(values: List<Entry>) {
        Timber.i("onEntriesArchived($values)")
        val mSnackbar =
            Snackbar
                .make(
                    coordinatorLayout,
                    resources.getQuantityString(
                        R.plurals.entries_archived_title,
                        values.size,
                        values.size
                    ),
                    Snackbar.LENGTH_LONG
                )
                .setAction(getString(R.string.undo_uppercase)) {
                    setEntriesArchived(values, false)
                }
                .setActionTextColor(theme.getColorStateList(this@MainActivity, R.attr.colorError))
        showSnackBack(mSnackbar)
    }

    @SuppressLint("CheckResult")
    private fun setEntriesArchived(entries: List<Entry>, archived: Boolean) {
        DatabaseHelper
            .setEntriesArchived(entries, archived)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = {
                    Timber.w(it, "onError")
                    Crashlytics.logException(it)
                },
                onComplete = {
                    if (archived) {
                        onEntriesArchived(entries)
                    } else {
                        Toast.makeText(this, R.string.entries_restored, Toast.LENGTH_SHORT).show()
                    }
                }
            )
    }

    private fun askToDeleteEntries(entries: List<Entry>) {
        Timber.i("askToDeleteEntries($entries)")

        AlertDialog
            .Builder(this)
            .setTitle(R.string.confirm)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.entries_delete_action_question,
                    entries.size,
                    entries.size
                )
            )
            .setPositiveButton(R.string.yes) { dialog, _ ->
                dialog.dismiss()
                deleteEntries(entries)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create().show()
    }

    @SuppressLint("CheckResult")
    private fun deleteEntries(entries: List<Entry>) {
        Timber.i("deleteEntries($entries")

        DatabaseHelper.deleteEntries(this, entries)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = {
                    Timber.e(it, "onError")
                    Toast.makeText(this, "Oh Snap! An error occurred!", Toast.LENGTH_SHORT).show()
                    Crashlytics.logException(it)
                },
                onComplete = {
                    Toast.makeText(
                        this, resources.getQuantityString(
                            R.plurals.entries_deleted_title,
                            entries.size,
                            entries.size
                        ), Toast.LENGTH_SHORT
                    ).show()

                }
            )
    }

    // selection tracker

    // actionmode callback

    private var mActionModeCallback: ActionMode.Callback = object : ActionMode.Callback {

        var actionModeBar: View? = null

        @Suppress("NAME_SHADOWING")
        private fun onTrackerSelectionChanged(
            selection: HashMap<Long, Entry>,
            actionMode: ActionMode?
        ) {
            Timber.i("onTrackerSelectionChanged()")
            actionMode?.let { actionMode ->
                if (selection.isEmpty()) {
                    actionMode.finish()
                } else {
                    actionMode.title =
                        resources.getQuantityString(
                            R.plurals.entries_selected_count,
                            selection.size,
                            selection.size
                        )

                    val pinned = selection.values.indexOfFirst { it.entryPinned == 1 } > -1
                    val unpinned = selection.values.indexOfFirst { it.entryPinned == 0 } > -1

                    val archived = selection.values.indexOfFirst { it.entryArchived == 1 } > -1

                    updatePinnedMenuItem(actionMode.menu, pinned && (pinned && !unpinned))
                    updateArchivedMenuItem(actionMode.menu, archived)
                }
            }
        }

        @Suppress("NAME_SHADOWING")
        private fun updatePinnedMenuItem(menu: Menu?, checked: Boolean) {
            menu?.let { menu ->
                val menuItem = menu.findItem(R.id.menu_action_pin)
                if (checked) {
                    menuItem.setIcon(R.drawable.appunti_sharp_favourite_24_checked_selector_actionmode)
                    menuItem.setTitle(R.string.unpin)
                } else {
                    menuItem.setIcon(R.drawable.appunti_sharp_favourite_24_unchecked_selector_actionmode)
                    menuItem.setTitle(R.string.pin)
                }
            }
        }

        @Suppress("NAME_SHADOWING")
        private fun updateArchivedMenuItem(menu: Menu?, checked: Boolean) {
            menu?.let { menu ->
                val menuItem = menu.findItem(R.id.menu_action_archive)
                if (checked) {
                    menuItem.setIcon(R.drawable.appunti_outline_unarchive_24_selector_actionmode)
                    menuItem.setTitle(R.string.unarchive)
                } else {
                    menuItem.setIcon(R.drawable.appunti_outline_archive_24_selector_actionmode)
                    menuItem.setTitle(R.string.archive)
                }
            }
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            Timber.i("onActionItemClicked: ${item.itemId}")

            val event = CustomEvent("main.actionModeItemClick")

            when (item.itemId) {
                R.id.menu_action_pin -> {
                    tracker?.let { tracker ->
                        event.putCustomAttribute("name", "pin")
                        val pinned =
                            tracker.selection.values.indexOfFirst { it.entryPinned == 1 } > -1
                        val unpinned =
                            tracker.selection.values.indexOfFirst { it.entryPinned == 0 } > -1
                        DatabaseHelper.setEntriesPinned(
                            tracker.selection.values.toList(),
                            !(pinned && (pinned && !unpinned))
                        )
                            .subscribe()
                    }
                }

                R.id.menu_action_delete -> {
                    tracker?.let { tracker ->
                        event.putCustomAttribute("name", "delete")
                        val entries = tracker.selection.values.toList()
                        askToDeleteEntries(entries)
                    }
                }

                R.id.menu_action_archive -> {
                    tracker?.let { tracker ->
                        event.putCustomAttribute("name", "archive")
                        val hasArchived =
                            tracker.selection.values.indexOfFirst { it.entryArchived == 1 } > -1
                        val entries = tracker.selection.values.toList()
                        if (hasArchived) {
                            setEntriesArchived(entries, false)
                        } else {
                            setEntriesArchived(entries, true)
                        }
                    }

                }
            }

            answers.logCustom(event)
            return true
        }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            Timber.i("onCreateActionMode")
            answers.logCustom(CustomEvent("main.createActionMode"))

            menuInflater.inflate(R.menu.appunti_main_actionmode_menu, menu)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            toolbar.animate().alpha(0f).start()

            tracker?.let { tracker ->
                tracker.listener = { onTrackerSelectionChanged(tracker.selection, mode) }
            }

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            Timber.i("onPrepareActionMode")
            onTrackerSelectionChanged(tracker?.selection ?: hashMapOf(), mode)

            actionModeBar = window.decorView.findViewById(R.id.action_mode_bar)
            actionModeBar?.translationY = statusbarHeight.toFloat()

            if (isFullScreen) {
                actionModeBackground.visibility = View.VISIBLE
                actionModeBackground.alpha = 0f
                actionModeBackground
                    .animate()
                    .alpha(1f)
                    .setDuration(
                        resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
                    )
                    .setListener(null)
                    .start()
            } else {
                //window.statusBarColor = theme.getColor(this@MainActivity, R.attr.actionModeBackground)
            }

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            Timber.i("onDestroyActionMode")
            tracker?.clearSelection()
            tracker = null

            if (isFullScreen) {
                actionModeBackground
                    .animate()
                    .alpha(0f)
                    .setDuration(
                        resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
                    )
                    .setAnimationListener {
                        onAnimationEnd { property, _ ->
                            actionModeBackground.visibility = View.INVISIBLE
                            property.setListener(null)
                        }
                    }
                    .start()
            } else {
                //window.statusBarColor = theme.getColor(this@MainActivity, android.R.attr.statusBarColor)
            }

            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            toolbar.animate().alpha(1f).start()
            mActionMode = null
        }
    }

    companion object {
        // show entries by category
        const val ACTION_ENTRIES_BY_CATEGORY = "view_entries_by_category"

        const val KEY_CATEGORY_ID = "categoryID"


    }

}

