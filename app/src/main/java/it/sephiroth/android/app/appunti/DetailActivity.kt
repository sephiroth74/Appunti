package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.core.text.util.LinkifyCompat
import androidx.core.transition.doOnEnd
import androidx.core.transition.doOnStart
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.view.postDelayed
import androidx.emoji.widget.SpannableBuilder
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dbflow5.structure.delete
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import it.sephiroth.android.app.appunti.ext.*
import it.sephiroth.android.app.appunti.io.RelativePath
import it.sephiroth.android.app.appunti.models.DetailViewModel
import it.sephiroth.android.app.appunti.models.EntryListJsonModel
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.app.appunti.utils.IntentUtils
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor
import it.sephiroth.android.library.kotlin_extensions.kotlin.hasBits
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import it.sephiroth.android.library.kotlin_extensions.view.hideSoftInput
import it.sephiroth.android.library.kotlin_extensions.view.showSoftInput
import it.sephiroth.android.library.kotlin_extensions.widget.addTextWatcherListener
import it.sephiroth.android.library.kotlin_extensions.widget.doOnAfterTextChanged
import it.sephiroth.android.library.kotlin_extensions.widget.doOnTextChanged
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.activity_detail.view.*
import kotlinx.android.synthetic.main.appunti_detail_attachment_item.view.*
import kotlinx.android.synthetic.main.appunti_detail_remoteurl_item.view.*
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.FormatStyle
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit


@Suppress("NAME_SHADOWING")
class DetailActivity : AppuntiActivity() {
    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_detail

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var model: DetailViewModel

    private var disablePostponedTransitions: Boolean = false
    private var currentEntryID: Long? = null

    private var shouldRemoveAlarm: Boolean = false

    // temporary file used for pictures taken with camera
    private var mCurrentPhotoPath: RelativePath? = null

    private var detailListAdapter: DetailListAdapter? = null

    private var tickTimer: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
//        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
//        window.sharedElementEnterTransition = AutoTransition()
//        window.sharedElementExitTransition = android.transition.Slide(Gravity.LEFT)
//        window.sharedElementReenterTransition = AutoTransition()
//        window.sharedElementReturnTransition = AutoTransition()

        window.sharedElementReturnTransition = null

        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        model = ViewModelProviders.of(this).get(DetailViewModel::class.java)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        setupBottomSheet()
        setupNavigationView()
        setupBottomAppBar()
        setupSharedElementsTransition()
        closeBottomSheet()

        textSwitcher.setFactory {
            AppCompatTextView(ContextThemeWrapper(this, R.style.Widget_Appunti_Text_TextSwitcher), null, 0)
        }

        nestedScrollView.requestFocusFromTouch()

        // UI elements listeners
        entryCategory.setOnClickListener { dispatchPickCategoryIntent() }
        entryTitle.doOnTextChanged { s, start, count, after -> updateEntryTitle(s, start, count, after) }
        entryText.doOnTextChanged { s, start, count, after -> updateEntryText(s, start, count, after) }
        entryText.doOnAfterTextChanged { e -> LinkifyCompat.addLinks(e, Linkify.ALL) }

        entryCategory.background = MaterialBackgroundUtils.categoryChipClickable(this)

        entryText.setOnClickListener {
            entryText.requestFocus()
            entryText.setOnClickListener(null)
        }

        entryTitle.setRawInputType(InputType.TYPE_CLASS_TEXT)

        model.entry.observe(this, Observer { entry ->
            Timber.i("Model Entry Changed = $entry")
            onEntryChanged(entry)
        })

        if (model.entry.value == null) {
            handleIntent(intent, savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.apply {
            currentEntryID?.let {
                this.putLong(IntentUtils.KEY_ENTRY_ID, it)
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        Timber.i("onDestroy")
        setProgressVisible(false)

        tickTimer?.dispose()

        if (model.isNew && model.entry.value?.isEmpty() == true) {
            Timber.v("new entry is empty. delete it")
            model.entry.value?.delete()
        } else {
            if (model.modified) {
                model.save()
            }
        }

        super.onDestroy()
    }

    private fun handleIntent(intent: Intent, savedInstanceState: Bundle?) {
        Timber.i("handleIntent($intent, $savedInstanceState)")
        var entryID: Long? = null
        var newEntry: Entry? = null
        var isNewDocument = false

        Timber.v("action=${intent.action}")

        savedInstanceState?.let { bundle ->
            if (bundle.containsKey(IntentUtils.KEY_ENTRY_ID)) {
                entryID = bundle.getLong(IntentUtils.KEY_ENTRY_ID)
                disablePostponedTransitions = false
            }
        }

        if (null == entryID) {
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    isNewDocument = true
                    disablePostponedTransitions = true
                    newEntry = Entry()

                    if (intent.hasExtra(IntentUtils.KEY_ENTRY_TYPE)) {
                        val type = Entry.EntryType.values()[intent.getIntExtra(IntentUtils.KEY_ENTRY_TYPE, 0)]
                        if (type == Entry.EntryType.LIST) {
                            newEntry.convertToList()
                        }
                    }
                }

                Intent.ACTION_EDIT -> {
                    disablePostponedTransitions = false
                    entryID = intent.getLongExtra(IntentUtils.KEY_ENTRY_ID, 0)
                    shouldRemoveAlarm = intent.getBooleanExtra(IntentUtils.KEY_REMOVE_ALARM, false)
                }

                Intent.ACTION_SEND -> {
                    isNewDocument = true
                    disablePostponedTransitions = true
                    newEntry = Entry
                        .fromString(intent.getStringExtra(Intent.EXTRA_TEXT))
                        .apply {
                            entryTitle =
                                if (intent.hasExtra(Intent.EXTRA_SUBJECT)) intent.getStringExtra(Intent.EXTRA_SUBJECT) else ""
                        }.also { entry ->
                            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                                entry.entryStream = streamUri
                            }
                        }
                }
            }
        }


        // don't delay the transition if it's a new document
        entryID?.let {
            postponeEnterTransition()
            model.entryID = it
        } ?: run {
            newEntry?.let {
                model.createNewEntry(it, isNewDocument)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.appunti_detail_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        Timber.i("onPrepareOptionsMenu")

        model.entry.whenNotNull { entry ->
            updateMenu(menu, entry)
            updateMenu(navigationView.menu, entry)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_action_pin -> togglePin()
            R.id.menu_action_archive -> toggleArchive()
            R.id.menu_action_alarm -> toggleReminder()
            android.R.id.home -> onBackPressed()
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Timber.i("onActivityResult(requestCode=$requestCode, resultCode=$resultCode, data=$data)")

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CATEGORY_PICK_REQUEST_CODE -> {
                    data?.let { data ->
                        val categoryID = data.getLongExtra(IntentUtils.KEY_CATEGORY_ID, -1)
                        changeEntryCategory(categoryID)
                    }
                }

                OPEN_FILE_REQUEST_CODE -> {
                    data?.data?.also { uri ->
                        addAttachmentToEntry(uri)
                    }
                }

                IMAGE_CAPTURE_REQUEST_CODE -> {
                    onImageCaptured(mCurrentPhotoPath)
                    mCurrentPhotoPath = null
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Result from [IMAGE_CAPTURE_REQUEST_CODE]
     */
    private fun onImageCaptured(dstFile: RelativePath?) {
        Timber.i("onImageCaptured(${dstFile?.absolutePath})")

        dstFile?.let { dstFile ->
            model.addAttachment(dstFile) { success, throwable ->
                doOnMainThread {
                    throwable?.let {
                        showConfirmation(it.localizedMessage)
                    } ?: run {
                        showConfirmation(getString(R.string.image_added))
                    }
                    invalidate(UPDATE_ATTACHMENTS)
                }
            }
        }
    }

    private fun changeEntryCategory(categoryID: Long) {
        Timber.i("changeEntryCategory($categoryID)")
        if (model.setEntryCategory(categoryID)) {
            invalidate(UPDATE_CATEGORY)
        }
    }

    private fun addAttachmentToEntry(uri: Uri) {
        Timber.i("addAttachmentToEntry($uri)")
        setProgressVisible(true)

        model.addAttachment(uri) { success, throwable ->
            Timber.v("addAttachment result=$success")
            doOnMainThread {
                throwable?.let {
                    showConfirmation(it.localizedMessage)
                } ?: run {
                    showConfirmation(getString(R.string.file_added))
                }
                invalidate(UPDATE_ATTACHMENTS)
            }
            setProgressVisible(false)
        }
    }

    // ENTRY TEXT LISTENERS

    @Suppress("UNUSED_PARAMETER")
    private fun updateEntryText(text: CharSequence?, start: Int, count: Int, after: Int) {
        if (currentFocus == entryText) {
            model.setEntryText(text)
        }
    }

    // ENTRY TITLE LISTENERS

    @Suppress("UNUSED_PARAMETER")
    private fun updateEntryTitle(text: CharSequence?, start: Int, count: Int, after: Int) {
        if (currentFocus == entryTitle) {
            model.setEntryTitle(text)
        }
    }

    // SHARED ELEMENTS TRANSITION

    private fun setupSharedElementsTransition() {
        val sharedElementEnterTransition = window.sharedElementEnterTransition

        sharedElementEnterTransition.doOnEnd {
            entryCategory.visibility = if (model.entry.value?.category == null) View.INVISIBLE else View.VISIBLE
        }
        sharedElementEnterTransition.doOnStart {
            entryCategory.visibility = if (model.entry.value?.category == null) View.INVISIBLE else View.VISIBLE
        }
    }

    // BOTTOM APP BAR

    private fun setNavigationMenuPicker(value: Boolean) {
        with(navigationView.menu) {
            findItem(R.id.menu_action_camera).isVisible = value
            findItem(R.id.menu_action_image).isVisible = value
            findItem(R.id.menu_action_file).isVisible = value
            findItem(R.id.menu_action_category).isVisible = !value
            findItem(R.id.menu_action_delete).isVisible = !value
            findItem(R.id.menu_action_list).isVisible = !value && model.entry.value?.entryType == Entry.EntryType.TEXT
            findItem(R.id.menu_action_text).isVisible = !value && model.entry.value?.entryType == Entry.EntryType.LIST
            findItem(R.id.menu_action_share).setVisible(!value)
        }
    }

    private fun setupBottomAppBar() {
        bottomAppBar.navigationIcon.setOnClickListener {
            if (!isBottomSheetClosed()) {
                setNavigationMenuPicker(false)
            }
            openOrCloseBottomsheet()
        }

        bottomAppBar.attachmentPicker.setOnClickListener {
            if (!isBottomSheetClosed()) {
                setNavigationMenuPicker(true)
            }
            openOrCloseBottomsheet()
        }


        bottomSheetModalBackground.setOnClickListener {
            closeBottomSheet()
        }

        bottomAppBar.doOnPreDraw {
            if (navigationView.layoutParams is ViewGroup.MarginLayoutParams) {
                (navigationView.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = it.height
            }
        }
    }

    // NAVIGATION VIEW

    private fun setupNavigationView() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_action_category -> dispatchPickCategoryIntent()

                R.id.menu_action_delete -> toggleDelete()

                R.id.menu_action_share -> dispatchShareEntryIntent()

                R.id.menu_action_image -> dispatchOpenImageIntent()

                R.id.menu_action_file -> dispatchOpenFileIntent()

                R.id.menu_action_camera -> dispatchTakePictureIntent()

                R.id.menu_action_list -> convertEntryToList()

                R.id.menu_action_text -> convertEntryToText()
            }
            closeBottomSheet()
            true
        }
    }

    // External Intents

    private fun dispatchPickCategoryIntent() {
        model.entry.whenNotNull { entry ->
            IntentUtils.Categories
                .Builder(this)
                .pickCategory()
                .selectedCategory(entry.category?.categoryID ?: -1).build().also {
                    startActivityForResult(it, CATEGORY_PICK_REQUEST_CODE, null)
                }
        }
    }

    private fun dispatchShareEntryIntent() {
        model.entry.whenNotNull { entry ->
            IntentUtils.createShareEntryIntent(this, entry).also {
                startActivity(Intent.createChooser(it, resources.getString(R.string.share)))
            }
        }
    }

    private fun dispatchOpenFileIntent() {
        IntentUtils.createPickDocumentIntent(this).also {
            startActivityForResult(it, OPEN_FILE_REQUEST_CODE)
        }
    }

    private fun dispatchOpenImageIntent() {
        IntentUtils.createPickImageIntent(this).also {
            startActivityForResult(it, OPEN_FILE_REQUEST_CODE)
        }
    }

    private fun dispatchTakePictureIntent() {
        model.entry.whenNotNull { entry ->
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    val photoFile = try {
                        FileSystemUtils.createImageFile(this, entry)
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                        null
                    }

                    photoFile?.also { file ->
                        Timber.v("photoFile = ${file.toString()}")
                        mCurrentPhotoPath = file
                        val photoURI = FileSystemUtils.getFileUri(this, file)
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        startActivityForResult(takePictureIntent, IMAGE_CAPTURE_REQUEST_CODE)
                    }
                }
            }
        }
    }

// BOTTOM SHEET BEHAVIORS

    private fun setupBottomSheet() {
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(p0: View, p1: Float) {
                bottomSheetModalBackground.background.alpha = (p1 * 255).toInt()
            }

            @SuppressLint("SwitchIntDef")
            override fun onStateChanged(p0: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_SETTLING,
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        bottomSheetModalBackground.visibility = View.VISIBLE
                        bottomSheetModalBackground.requestFocus()
                        bottomSheetModalBackground.hideSoftInput()
                    }

                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        bottomSheetModalBackground.visibility = View.INVISIBLE
                    }
                }
            }

        })
    }

    private fun closeBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun openOrCloseBottomsheet() {
        if (isBottomSheetOpened()) {
            closeBottomSheet()
        } else {
            openBottomSheet()
            navigationView.bringToFront()
        }
    }

    private fun isBottomSheetOpened(): Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }

    private fun isBottomSheetClosed(): Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN
    }

    val UPDATE_TITLE = 1 shl 0
    val UPDATE_TYPE_AND_TEXT = 1 shl 1
    val UPDATE_CATEGORY = 1 shl 2
    val UPDATE_ATTACHMENTS = 1 shl 3
    val UPDATE_REMOTE_URLS = 1 shl 10

    val UPDATE_CREATION_DATE = 1 shl 4
    val UPDATE_MODIFIED_DATE = 1 shl 5

    val UPDATE_PINNED = 1 shl 6
    val UPDATE_ARCHIVED = 1 shl 7
    val UPDATE_DELETED = 1 shl 8
    val UPDATE_REMINDER = 1 shl 9

    val UPDATE_MENU = UPDATE_PINNED or UPDATE_ARCHIVED or UPDATE_DELETED or UPDATE_REMINDER

    private fun invalidate(bits: Int) {
        model.entry.whenNotNull { entry ->
            Timber.i("invalidate($bits)")
            Timber.v("entry: ${model.entry.value}")

            if (UPDATE_MENU hasBits bits) {
                invalidateOptionsMenu()

                if (!(bits hasBits UPDATE_CREATION_DATE)) {
                    tickNext(TICKER_STEP_MODIFIED)
                }
            }

            if (bits hasBits UPDATE_CREATION_DATE) {
                updateTextSwitcher()
            }

            if (bits hasBits UPDATE_TITLE) {
                entryTitle.setText(entry.entryTitle)
            }

            if (bits hasBits UPDATE_CATEGORY) {
                updateEntryCategory(entry)
                tickNext(TICKER_STEP_MODIFIED)
            }

            if (bits hasBits UPDATE_ATTACHMENTS) {
                updateEntryAttachmentsList(entry)
                tickNext(TICKER_STEP_MODIFIED)
            }

            if (bits hasBits UPDATE_REMOTE_URLS) {
                updateEntryRemoteUrls(entry)
            }

            if (bits hasBits UPDATE_TYPE_AND_TEXT) {
                updateEntryTypeAndText(entry)
            }
        }
    }

    // Entry Changed/Updated

    private fun onEntryChanged(entry: Entry) {
        Timber.i("onEntryChanged()")

        if (currentEntryID == entry.entryID) {
            Timber.w("same entry id, skipping updates...")
            return
        }

        currentEntryID = entry.entryID

        invalidate(
            UPDATE_TITLE
                    or UPDATE_TYPE_AND_TEXT
                    or UPDATE_CATEGORY
                    or UPDATE_ATTACHMENTS
                    or UPDATE_REMOTE_URLS
                    or UPDATE_CREATION_DATE
                    or UPDATE_MENU
        )

        if (shouldRemoveAlarm) {
            if (model.removeReminder()) {
                invalidate(UPDATE_REMINDER)
            }
            shouldRemoveAlarm = false
        }

        // invoke the postponed transition only the first time
        // when it's not a new document
        if (!disablePostponedTransitions) {
            entryTitle.doOnPreDraw {
                startPostponedEnterTransition()
                entryTitle.transitionName = null
                entryText.transitionName = null
                entryCategory.transitionName = null
            }
        }

        disablePostponedTransitions = false

        // temporary
        // add the attachment if the original intent had the EXTRA_STREAM extra
        entry.entryStream?.let {
            entry.entryStream = null
            addAttachmentToEntry(it)
        }
    }


    /**
     * update entry type (list/text) and content
     */
    private fun updateEntryTypeAndText(entry: Entry) {
        entryText.visibility = if (entry.entryType == Entry.EntryType.TEXT) View.VISIBLE else View.GONE
        detailRecycler.visibility = if (entry.entryType == Entry.EntryType.LIST) View.VISIBLE else View.GONE

        if (entry.entryType == Entry.EntryType.TEXT) {
            detailRecycler.adapter = null
            detailListAdapter?.saveAction = null
            detailListAdapter?.deleteAction = null
            detailListAdapter = null
            entryText.setText(entry.entryText)

        } else if (entry.entryType == Entry.EntryType.LIST) {

            nestedScrollView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        Timber.v("clearing focus")

                        if (currentFocus is TextView) {
                            currentFocus?.hideSoftInput()
                            currentFocus?.clearFocus()
                        }
                        false
                    }
                    else -> false
                }
            }

            detailListAdapter = DetailListAdapter(this).apply {
                setData(entry)
                saveAction = { text ->
                    Timber.i("saveAction")
                    model.setEntryText(text)
                }

                deleteAction = { holder, entry ->
                    var result = false
                    if (!holder.text.hasSelection() && holder.text.selectionStart == 0 && holder.text.selectionEnd == 0 && holder.adapterPosition == 0) {
                        result = false
                    } else if (!holder.text.hasSelection() && holder.text.selectionStart == 0 && holder.text.selectionEnd == 0) {

                        val spareText = holder.text.text
                        removeFocusFromEditText()
                        deleteItem(entry, holder.itemViewType)

                        if (holder.adapterPosition > 0) {
                            val view =
                                (detailRecycler.layoutManager as LinearLayoutManager).findViewByPosition(holder.adapterPosition - 1)
                            view?.let { view ->
                                val previous = detailRecycler.findContainingViewHolder(view)
                                previous?.let { previous ->
                                    if (previous is DetailListAdapter.DetailEntryViewHolder) {
                                        with((previous.text as EditText)) {
                                            this.requestFocusFromTouch()
                                            this.text.append(spareText)
                                            this.setSelection(this.length() - spareText.length)
                                            this.showSoftInput()
                                        }
                                    }
                                }
                            }
                        }
                        result = true
                    }
                    result
                }
            }
            detailRecycler.adapter = detailListAdapter
        }
    }

    /**
     * Updated entry category
     */
    private fun updateEntryCategory(entry: Entry) {
        entryCategory.text = entry.category?.categoryTitle
        entryCategory.visibility = if (entry.category == null) View.INVISIBLE else View.VISIBLE
        updateThemeFromEntry(entry)
    }

    // text switcher ticker

    private fun updateTextSwitcher() {
        tickNext(TICKER_STEP_CREATED)
    }

    private fun tickNext(step: Int) {
        when (step) {
            TICKER_STEP_CREATED -> {
                tickTimer?.dispose()
                tickCurrent(step)
            }
            TICKER_STEP_MODIFIED,
            TICKER_STEP_ALARM -> tickTimer = rxTimer(
                tickTimer,
                4,
                TimeUnit.SECONDS,
                Schedulers.computation(),
                AndroidSchedulers.mainThread()
            ) { tickCurrent(step) }
        }
    }

    private fun tickCurrent(step: Int) {
        model.entry.whenNotNull { entry ->
            when (step) {
                TICKER_STEP_CREATED -> {
                    val lastModifiedString = resources.getString(
                        R.string.created_at,
                        entry.entryCreationDate.atZone().formatDiff(this, FormatStyle.MEDIUM, FormatStyle.SHORT)
                    )
                    textSwitcher.setText(lastModifiedString)
                    tickNext(TICKER_STEP_MODIFIED)
                }

                TICKER_STEP_MODIFIED -> {
                    val lastModifiedString = resources.getString(
                        R.string.last_modified,
                        entry.entryModifiedDate.atZone().formatDiff(this, FormatStyle.MEDIUM, FormatStyle.SHORT)
                    )
                    textSwitcher.setText(lastModifiedString)
                    tickNext(TICKER_STEP_ALARM)
                }

                TICKER_STEP_ALARM -> {
                    if (entry.hasReminder()) {
                        val reminderDate = entry.entryAlarm!!.atZone()
                        textSwitcher.setText(reminderDate.formatDiff(this, FormatStyle.MEDIUM, FormatStyle.SHORT))
                        val textView = textSwitcher.currentView as TextView

                        val drawable = resources.getDrawable(R.drawable.sharp_alarm_24, theme)
                        drawable.setBounds(0, 0, textSwitcher.height, textSwitcher.height)
                        textView.setCompoundDrawablesRelative(drawable, null, null, null)
                    }
                }
            }
        }
    }

    // text switcher ticker end

    private fun updateEntryRemoteUrls(entry: Entry) {
        remoteUrlsContainer.removeAllViews()
        val remoteUrls = entry.getRemoteUrls()

        remoteUrlsContainer.visibility = if (remoteUrls.isNullOrEmpty()) View.GONE else View.VISIBLE

        remoteUrls?.let { remoteUrls ->
            val cardColor = theme.getColor(this, android.R.attr.windowBackground)

            for (remoteUrl in remoteUrls) {
                val view = LayoutInflater.from(this)
                    .inflate(R.layout.appunti_detail_remoteurl_item, attachmentsContainer, false) as CardView

                view.setCardBackgroundColor(cardColor)
                view.remoteUrlTitle.text = remoteUrl.remoteUrlTitle
                view.remoteUrlDescription.text = remoteUrl.remoteUrlDescription
                view.tag = remoteUrl

                remoteUrl.loadThumbnail(this, view.remoteUrlImage)

                view.setOnClickListener {
                    try {
                        IntentUtils.createRemoteUrlViewIntent(this, remoteUrl).also {
                            startActivity(it)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                view.remoteUrlRemoveButton.setOnClickListener {
                    removeEntryRemoteUrl(remoteUrl)
                }
                remoteUrlsContainer.addView(view)
            }
        }
    }

    private fun updateEntryAttachmentsList(entry: Entry) {
        attachmentsContainer.removeAllViews()

        val attachments = entry.getAttachments()
        attachmentsContainer.visibility = if (attachments.isNullOrEmpty()) View.GONE else View.VISIBLE

        attachments?.let { attachments ->
            val cardColor = entry.getAttachmentColor(this)

            for (attachment in attachments) {
                val view = LayoutInflater.from(this)
                    .inflate(R.layout.appunti_detail_attachment_item, attachmentsContainer, false) as CardView

                view.setCardBackgroundColor(cardColor)
                view.attachmentTitle.text = attachment.attachmentTitle
                view.tag = attachment

                Timber.v("$attachment")

                // TODO(Specify the exact size here)
                attachment.loadThumbnail(this, view.attachmentImage)

                view.setOnClickListener {
                    try {
                        IntentUtils.createAttachmentViewIntent(this, attachment).also {
                            startActivity(it)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                view.attachmentShareButton.setOnClickListener {
                    try {
                        IntentUtils.createAttachmentShareIntent(this, attachment).also {
                            startActivity(Intent.createChooser(it, resources.getString(R.string.share)))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                view.attachmentRemoveButton.setOnClickListener {
                    removeEntryAttachment(attachment)
                }

                attachmentsContainer.addView(view)
            }
        }
    }

    private fun updateThemeFromEntry(entry: Entry) {
        val color = entry.getColor(this)
        // color = ColorUtils.setAlphaComponent(color, 201)

        Timber.i("updateThemeFromEntry. color=${color.toString(16)}")

        coordinator.backgroundTintList = ColorStateList.valueOf(color)

        window.statusBarColor = color
        window.navigationBarColor = color

        bottomAppBar.backgroundTintList = ColorStateList.valueOf(color)

        if (navigationView.background is ColorDrawable) {
            navigationView.backgroundTintList = ColorStateList.valueOf(color)
        } else if (navigationView.background is LayerDrawable) {
            val drawable: Drawable? =
                (navigationView.background as LayerDrawable).findDrawableByLayerId(R.id.layer_background)
            drawable?.setTint(color)
        }

        // attachments

        if (attachmentsContainer.childCount > 0) {
            val cardColor = entry.getAttachmentColor(this)
            for (view in attachmentsContainer.children) {
                (view as CardView).setCardBackgroundColor(cardColor)
            }
        }
    }

    private fun updateMenu(menu: Menu?, entry: Entry) {
        menu?.let { menu ->
            var menuItem = menu.findItem(R.id.menu_action_pin)
            menuItem?.apply {
                setIcon(
                    if (entry.isPinned())
                        R.drawable.appunti_sharp_favourite_24_checked_selector
                    else
                        R.drawable.appunti_sharp_favourite_24_unchecked_selector
                )
            }

            menuItem = menu.findItem(R.id.menu_action_archive)
            menuItem?.apply {
                setIcon(
                    if (entry.isArchived())
                        R.drawable.appunti_outline_archive_24_checked_selector
                    else R.drawable.appunti_outline_archive_24_unchecked_selector
                )

                setTitle(if (entry.isArchived()) R.string.unarchive else R.string.archive)
            }

            menuItem = menu.findItem(R.id.menu_action_delete)
            menuItem?.apply {
                setIcon(
                    if (entry.isDeleted())
                        R.drawable.appunti_sharp_restore_from_trash_24_selector
                    else R.drawable.appunti_sharp_delete_24_outline_selector
                )

                setTitle(if (entry.isDeleted()) R.string.restore else R.string.delete)
            }

            menuItem = menu.findItem(R.id.menu_action_alarm)
            menuItem?.apply {

                if (entry.hasReminder() && !entry.isReminderExpired()) {
                    setIcon(R.drawable.twotone_alarm_24)
                    setTitle(R.string.remove_reminder)
                } else {
                    setIcon(R.drawable.sharp_alarm_24)
                    setTitle(R.string.add_reminder)
                }
            }
        }
    }

    // END ENTRY UPDATE

    private fun removeEntryRemoteUrl(remoteUrl: RemoteUrl) {
        model.hideRemoteUrl(remoteUrl) { result, throwable ->
            doOnMainThread {
                throwable?.let {
                    showConfirmation(throwable.localizedMessage)
                } ?: run {
                    invalidate(UPDATE_REMOTE_URLS)
                }
            }
        }
    }

    private fun removeEntryAttachment(attachment: Attachment) {
        model.removeAttachment(attachment) { result, throwable ->
            doOnMainThread {
                throwable?.let { throwable ->
                    showConfirmation(throwable.localizedMessage)
                } ?: run {
                    Timber.v("[${currentThread()}] success = $result")
                    showConfirmation("File has been removed")
                }
                invalidate(UPDATE_ATTACHMENTS)
            }
        }
    }

    private fun convertEntryToList() {
        if (model.convertEntryToList()) {
            invalidate(UPDATE_TYPE_AND_TEXT)
        }
    }

    private fun convertEntryToText() {
        if (model.convertEntryToText(detailListAdapter.toString())) {
            invalidate(UPDATE_TYPE_AND_TEXT)
        }
    }

    private fun togglePin() {
        val currentValue = model.entry.value?.isPinned()
        if (model.setEntryPinned(currentValue == false)) {
            showConfirmation(
                resources.getQuantityString(
                    if (currentValue == true)
                        R.plurals.entries_unpinned_title else R.plurals.entries_pinned_title, 1, 1
                )
            )
            invalidate(UPDATE_PINNED)
        }
    }

    private fun toggleDelete() {
        model.entry.whenNotNull { entry ->
            val currentValue = entry.isDeleted()
            if (model.setEntryDeleted(!currentValue)) {
                showConfirmation(
                    resources.getQuantityString(
                        if (currentValue)
                            R.plurals.entries_restored_title else R.plurals.entries_deleted_title, 1, 1
                    )
                )

                if (!currentValue) {
                    onBackPressed()
                } else {
                    invalidate(UPDATE_DELETED)
                }
            }
        }
    }

    private fun toggleArchive() {
        model.entry.whenNotNull { entry ->
            val currentValue = entry.isArchived()
            if (model.setEntryArchived(!currentValue)) {
                showConfirmation(
                    resources.getQuantityString(
                        if (currentValue)
                            R.plurals.entries_unarchived_title else R.plurals.entries_archived_title, 1, 1
                    )
                )

                if (!currentValue) {
                    onBackPressed()
                } else {
                    invalidate(UPDATE_ARCHIVED)
                }
            }
        }
    }

    private fun toggleReminder() {
        model.entry.whenNotNull { entry ->
            if (entry.entryAlarm != null && !entry.isReminderExpired()) {
                val date = entry.entryAlarm!!.atZone(ZoneId.systemDefault())
                val dateFormatted = entry.entryAlarm!!.getLocalizedDateTimeStamp(FormatStyle.FULL)
                val reminderText = getString(R.string.edit_reminder_dialog_text, dateFormatted)
                val span = SpannableBuilder.valueOf(reminderText)
                val index = reminderText.indexOf(dateFormatted)

                if (index > -1) {
                    span[index, index + dateFormatted.length] = StyleSpan(Typeface.BOLD)
                }

                AlertDialog
                    .Builder(this)
                    .setCancelable(true)
                    .setTitle(getString(R.string.edit_reminder))
                    .setMessage(span.toSpannable())
                    .setPositiveButton(getString(R.string.change)) { dialog, _ ->
                        dialog.dismiss()
                        pickDateTime(date) { result ->
                            if (model.addReminder(result)) {
                                showConfirmation(getString(R.string.reminder_set))
                            }
                        }
                    }
                    .setNeutralButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton(getString(R.string.remove)) { dialog, _ ->
                        dialog.dismiss()
                        if (model.removeReminder()) {
                            showConfirmation(getString(R.string.reminder_removed))
                            invalidate(UPDATE_REMINDER)
                        }
                    }
                    .show()
            } else {
                val now = Instant.now()
                val date = now.atZone(ZoneId.systemDefault())
                pickDateTime(date) { result ->
                    Timber.v("date time picker! $result")

                    if (model.addReminder(result)) {
                        showConfirmation(getString(R.string.reminder_set))
                        invalidate(UPDATE_REMINDER)
                    }
                }
            }
        }
    }

    private fun showConfirmation(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun setProgressVisible(visible: Boolean) {
        toolbarProgress.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    companion object {
        // pick new category
        const val CATEGORY_PICK_REQUEST_CODE = 1

        // pick file
        const val OPEN_FILE_REQUEST_CODE = 2

        // take picture
        const val IMAGE_CAPTURE_REQUEST_CODE = 3

        // text switcher consts
        private const val TICKER_STEP_CREATED = 1
        private const val TICKER_STEP_MODIFIED = 2
        private const val TICKER_STEP_ALARM = 3
    }

    object EntryDiff {
        data class Result(
            var sameID: Boolean,
            var archivedChanged: Boolean = true,
            var deletedChanged: Boolean = true,
            var pinnedChanged: Boolean = true,
            var titleChanged: Boolean = true,
            var textChanged: Boolean = true,
            var categoryChanged: Boolean = true,
            var priorityChanged: Boolean = true,
            var modifiedDateChanged: Boolean = true,
            var attachmentsChanged: Boolean = true,
            var typeChanged: Boolean = true,
            var alarmChanged: Boolean = true
        )

        fun calculateDiff(oldValue: Entry?, newValue: Entry?): Result {
            return if (isSameItem(oldValue, newValue)) {
                calculateContentDiff(oldValue, newValue)
            } else {
                Result(false)
            }
        }

        private fun isSameItem(oldValue: Entry?, newValue: Entry?): Boolean {
            return oldValue?.entryID == newValue?.entryID
        }

        private fun calculateContentDiff(oldValue: Entry?, newValue: Entry?): Result {
            return Result(
                sameID = true,
                archivedChanged = oldValue?.entryArchived != newValue?.entryArchived,
                deletedChanged = oldValue?.entryDeleted != newValue?.entryDeleted,
                pinnedChanged = oldValue?.entryPinned != newValue?.entryPinned,
                titleChanged = oldValue?.entryTitle != newValue?.entryTitle,
                textChanged = oldValue?.entryText != newValue?.entryText,
                categoryChanged = oldValue?.category != newValue?.category,
                priorityChanged = oldValue?.entryPriority != newValue?.entryPriority,
                modifiedDateChanged = oldValue?.entryModifiedDate != newValue?.entryModifiedDate,
                attachmentsChanged = oldValue?.getAttachments() != newValue?.getAttachments(),
                typeChanged = oldValue?.entryType != newValue?.entryType,
                alarmChanged = oldValue?.entryAlarmEnabled != newValue?.entryAlarmEnabled || oldValue?.entryAlarm != newValue?.entryAlarm
            )

        }
    }
}


class DetailListAdapter(var context: Context) : RecyclerView.Adapter<DetailListAdapter.DetailViewHolder>() {
    private var dataHolder = EntryListJsonModel()
    private var inflater = LayoutInflater.from(context)
    private var currentEditText: TextView? = null

    var saveAction: ((String) -> (Unit))? = null
    var deleteAction: ((DetailEntryViewHolder, EntryListJsonModel.EntryJson) -> Boolean)? = null

    init {
        setHasStableIds(true)
    }

    private fun postSave() {
        Timber.i("postSave")
        doOnScheduler(Schedulers.computation()) {
            saveAction?.invoke(dataHolder.toJson())
        }
    }

    fun setData(entry: Entry) {
        dataHolder.setData(entry.asList())
        notifyDataSetChanged()
    }

    override fun toString(): String {
        return dataHolder.toString()
    }

    override fun getItemCount(): Int {
        return dataHolder.size()
    }

    override fun getItemId(position: Int): Long {
        return dataHolder.getItemId(position)
    }

    private fun getItem(position: Int): EntryListJsonModel.EntryJson {
        return dataHolder.getItem(position)
    }

    override fun getItemViewType(position: Int): Int {
        return dataHolder.getItemType(position)
    }

    private var insertedIndex: Int = -1

    private fun addItem() {
        Timber.v("addItem")
        doOnMainThread {
            dataHolder.addItem(checked = false).also { index ->
                insertedIndex = index
                Timber.v("insertedIndex = $insertedIndex")
                notifyItemInserted(index)
                postSave()
            }
        }
    }

    private fun insertItemAfter(entry: EntryListJsonModel.EntryJson, text: String?) {
        Timber.i("insertItemAfter($entry)")
        val id = entry.id
        doOnMainThread {
            insertedIndex = dataHolder.insertItem(id, text)
            Timber.v("insertedIndex = $insertedIndex")

            if (insertedIndex > -1) {
                notifyDataSetChanged()
                postSave()
            }
        }
    }

    internal fun deleteItem(entry: EntryListJsonModel.EntryJson, itemViewType: Int) {
        doOnMainThread {
            dataHolder.deleteItem(entry, itemViewType)?.let { index ->
                notifyItemRemoved(index)
                postSave()
            }
        }
    }

    private fun toggleItem(entry: EntryListJsonModel.EntryJson, itemViewType: Int) {
        doOnMainThread {
            dataHolder.toggle(
                entry,
                itemViewType
            )?.also { result ->
                notifyItemMoved(result.first, result.second)
                postSave()
            }
        }
    }

    internal fun removeFocusFromEditText() {
        Timber.i("removeFocusFromEditText")

        currentEditText?.apply {
            this.clearFocus()
            this.hideSoftInput()
        }

        currentEditText = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        return if (viewType == EntryListJsonModel.TYPE_NEW_ENTRY) {
            val view = inflater.inflate(R.layout.appunti_detail_new_entry_list_item, parent, false)

            view.setOnClickListener {
                Timber.v("buttonAdd onClick")
                //removeFocusFromEditText()
                addItem()
            }

            DetailNewEntryViewHolder(view).apply {
                buttonAdd.background = MaterialBackgroundUtils.newEntryListItem(context)
            }

        } else {
            val view = inflater.inflate(R.layout.appunti_detail_entry_list_item_checkable, parent, false)
            DetailEntryViewHolder(view)
        }
    }

    override fun onBindViewHolder(baseHolder: DetailViewHolder, position: Int) {
        if (baseHolder.itemViewType == EntryListJsonModel.TYPE_NEW_ENTRY) {
            val holder = baseHolder as DetailNewEntryViewHolder
        } else {
            val holder = baseHolder as DetailEntryViewHolder
            holder.checkbox.setOnCheckedChangeListener(null)

            val entry = getItem(position)
            holder.text.text = entry.text

            if (holder.itemViewType == EntryListJsonModel.TYPE_CHECKED) {
                holder.checkbox.isChecked = true
                holder.text.paintFlags = holder.text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.checkbox.isChecked = false
                holder.text.paintFlags = holder.text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            holder.checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                removeFocusFromEditText()
                toggleItem(
                    entry,
                    holder.itemViewType
                )
            }

            holder.deleteButton.setOnClickListener {
                removeFocusFromEditText()
                deleteItem(entry, holder.itemViewType)
            }

            holder.text.setOnFocusChangeListener { v, hasFocus ->
                Timber.v("setOnFocusChangeListener($hasFocus)")
                holder.deleteButton.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                if (hasFocus) {
                    currentEditText = holder.text
                }
            }

            holder.text.removeTextChangedListener(holder.textListener)

            holder.textListener = holder.text.addTextWatcherListener {
                afterTextChanged { textView, s ->
                    if (currentEditText == textView) {
//                        Timber.i("afterTextChanged($s, ${currentEditText?.selectionStart}, ${currentEditText?.selectionEnd})")
//                        for (i in (s!!.length - 1) downTo 0 step 1) {
//                            if (s!![i] == '\n') {
//                                s.delete(i, i + 1)
//                                Timber.v("final string = '$s'")
//                                return@afterTextChanged
//                            }
//                        }
                    }
                }

                onTextChanged { textView, s, start, before, count ->
                    if (currentEditText == textView) {
                        Timber.i("onTextChanged('$s', $start, $before, $count)")

                        s?.let { s ->
                            if (count == 1 && before == 0) {
                                if (s[start] == '\n') {
                                    Timber.w("it's a newline!!!!")
                                    Timber.v("replace with = '${s.substring(0, start)}'")
                                    entry.text = s.substring(0, start)
                                    insertItemAfter(entry, s.substring(start).trimStart())
                                    return@onTextChanged
                                }
                            }
                            Timber.v("final text = $s")
                            entry.text = s.toString()
                            postSave()
                        }
                    }
                }
            }


            holder.text.setOnKeyListener { v, keyCode, event ->
                var returnType = false
                if (event.action == KeyEvent.ACTION_UP) {
                    if (keyCode == KeyEvent.KEYCODE_DEL) {
                        returnType = deleteAction?.invoke(holder, entry) ?: true
                    }
                }
                returnType
            }

            holder.text.setOnEditorActionListener { v, actionId, event ->
                Timber.i("setOnEditorActionListener = $actionId")
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    removeFocusFromEditText()
                    postSave()
                    true
                } else {
                    false
                }
            }

            if (insertedIndex == position) {
                Timber.v("insertedIndex found!")

                removeFocusFromEditText()

                // force keyboard to open
                holder.text.postDelayed(400) {
                    Timber.v("setting the new focus")
                    holder.text.requestFocusFromTouch()
                    holder.text.showSoftInput()
                }

                insertedIndex = -1
            }

        }
    }

    open class DetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(android.R.id.text1)
    }

    open class DetailNewEntryViewHolder(itemView: View) : DetailViewHolder(itemView) {
        val buttonAdd: View = itemView.findViewById(R.id.buttonAdd)
    }

    class DetailEntryViewHolder(itemView: View) : DetailViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val deleteButton: View = itemView.findViewById(R.id.deleteButton)
        var textListener: TextWatcher? = null
    }
}