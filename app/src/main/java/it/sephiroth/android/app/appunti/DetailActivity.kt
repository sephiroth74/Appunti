package it.sephiroth.android.app.appunti

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.location.Address
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.text.TextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.core.transition.doOnEnd
import androidx.core.transition.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.emoji.widget.SpannableBuilder
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
import com.crashlytics.android.answers.ShareEvent
import com.dbflow5.isNotNullOrEmpty
import com.dbflow5.structure.delete
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.hunter.library.debug.HunterDebug
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.sellmair.disposer.disposeBy
import io.sellmair.disposer.onDestroy
import it.sephiroth.android.app.appunti.adapters.DetailAttachmentsListAdapter
import it.sephiroth.android.app.appunti.adapters.DetailRemoteUrlListAdapter
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.RemoteUrl
import it.sephiroth.android.app.appunti.events.RxBus
import it.sephiroth.android.app.appunti.events.TaskRemovedEvent
import it.sephiroth.android.app.appunti.events.impl.AttachmentOnClickEvent
import it.sephiroth.android.app.appunti.events.impl.AttachmentOnDeleteEvent
import it.sephiroth.android.app.appunti.events.impl.AttachmentOnShareEvent
import it.sephiroth.android.app.appunti.ext.*
import it.sephiroth.android.app.appunti.io.RelativePath
import it.sephiroth.android.app.appunti.models.DetailViewModel
import it.sephiroth.android.app.appunti.models.EntryListJsonModel
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.services.LocationRepository
import it.sephiroth.android.app.appunti.utils.Constants
import it.sephiroth.android.app.appunti.utils.Constants.ActivityRequestCodes.CATEGORY_PICK_REQUEST_CODE
import it.sephiroth.android.app.appunti.utils.Constants.ActivityRequestCodes.IMAGE_CAPTURE_REQUEST_CODE
import it.sephiroth.android.app.appunti.utils.Constants.ActivityRequestCodes.OPEN_FILE_REQUEST_CODE
import it.sephiroth.android.app.appunti.utils.Constants.ActivityRequestCodes.REQUEST_LOCATION_PERMISSION_CODE
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.app.appunti.utils.IntentUtils
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.addTo
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnScheduler
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.rxTimer
import it.sephiroth.android.library.kotlin_extensions.kotlin.hasBits
import it.sephiroth.android.library.kotlin_extensions.lang.currentThread
import it.sephiroth.android.library.kotlin_extensions.view.hideSoftInput
import it.sephiroth.android.library.kotlin_extensions.view.showSoftInput
import it.sephiroth.android.library.kotlin_extensions.widget.addTextWatcherListener
import it.sephiroth.android.library.kotlin_extensions.widget.doOnAfterTextChanged
import it.sephiroth.android.library.kotlin_extensions.widget.doOnTextChanged
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.activity_detail.view.*
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.FormatStyle
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


@Suppress("NAME_SHADOWING", "PrivatePropertyName")
class DetailActivity : AudioRecordActivity() {
    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_detail

    private var audioIntent: Intent? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var model: DetailViewModel

    private var disablePostponedTransitions: Boolean = false
    private var currentEntryID: Long? = null

    private var shouldRemoveAlarm: Boolean = false

    // temporary file used for pictures taken with camera
    private var mCurrentPhotoPath: RelativePath? = null

    private var detailListAdapter: DetailListAdapter? = null
    private var remoteUrlListAdapter: DetailRemoteUrlListAdapter? = null
    private var attachmentsListAdapter: DetailAttachmentsListAdapter? = null

    private var tickTimer: Disposable? = null

    private var progressDialog: ProgressDialog? = null

    private var pauseListeners = false

    private val linkLongClickListener: BetterLinkMovementMethod.OnLinkLongClickListener =
        BetterLinkMovementMethod.OnLinkLongClickListener { textView, url -> true }

    private val linkClickListener: BetterLinkMovementMethod.OnLinkClickListener =
        BetterLinkMovementMethod.OnLinkClickListener { textView: TextView, url: String ->
            answers.logCustom(CustomEvent("detail.linkClick"))

            SettingsManager.getInstance(this).openLinksOnClick?.let { openLinks ->
                return@OnLinkClickListener !openLinks
            } ?: run {
                AlertDialog.Builder(this)
                    .setItems(
                        arrayOf(
                            getString(R.string.open_link),
                            getString(R.string.keep_editing)
                        )
                    ) { _, which ->
                        when (which) {
                            0 -> URLSpan(url).onClick(textView)
                        }
                    }
                    .create().show()

                true

            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Timber.i("onRequestPermissionsResult($permissions)")

        if (requestCode == REQUEST_LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.v("permission granted!")
                answers.logCustom(CustomEvent("permissions.location.granted"))
                insertCurrentAddress()
            } else {
                Timber.w("permission denied")
                answers.logCustom(CustomEvent("permissions.location.deined"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.sharedElementReturnTransition = null

        startService(Intent(this, DummyService::class.java))

        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        model = ViewModelProviders.of(this).get(DetailViewModel::class.java)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)

        setupNavigationView()
        setupBottomAppBar()
        setupSharedElementsTransition()
        closeBottomSheet()

        textSwitcher.setFactory {
            AppCompatTextView(
                ContextThemeWrapper(this, R.style.Widget_Appunti_Text_TextSwitcher),
                null,
                0
            )
        }

        nestedScrollView.requestFocusFromTouch()

        // UI elements listeners
        entryCategory.setOnClickListener { dispatchPickCategoryIntent() }
        entryTitle.doOnTextChanged { s, start, count, after ->
            if (!pauseListeners) {
                updateEntryTitle(s, start, count, after)
            }
        }
        entryText.doOnTextChanged { s, start, count, after ->
            if (!pauseListeners) {
                updateEntryText(s, start, count, after)
            }
        }
        entryText.doOnAfterTextChanged { e ->
            Timber.v("entryText.doOnAfterTextChanged(${e.isBlank()})")

            if (e.isBlank() || e.isEmpty()) {
                entryText.movementMethod = ArrowKeyMovementMethod.getInstance()
            } else {
                BetterLinkMovementMethod.linkify(Linkify.ALL, entryText)
                    .setOnLinkClickListener(linkClickListener)
                    .setOnLinkLongClickListener(linkLongClickListener)
            }
        }
        entryText.setOnClickListener {
            entryText.requestFocus()
            entryText.setOnClickListener(null)
        }

        model.entry.observe(this, Observer
        { entry ->
            onEntryChanged(entry)
        })

        if (model.entry.value == null) {
            handleIntent(intent, savedInstanceState)
        }


//        coordinator.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
//            Timber.i("onFocusChanged($oldFocus ==> $newFocus)")
//        }
    }

    override fun onStart() {
        super.onStart()

        RxBus.listen(AttachmentOnShareEvent::class.java)
            .subscribe { shareEntryAttachment(it.attachment) }.disposeBy(onStop)
        RxBus.listen(AttachmentOnDeleteEvent::class.java)
            .subscribe { askToDeleteAttachment(it.attachment) }.disposeBy(onStop)
        RxBus.listen(AttachmentOnClickEvent::class.java)
            .subscribe { viewEntryAttachment(it.attachment) }.disposeBy(onStop)
        RxBus.listen(TaskRemovedEvent::class.java)
            .subscribe { onTaskRemoved() }.disposeBy(onDestroy)
    }

    @HunterDebug
    private fun saveOnDestroy(deleteIfEmpty: Boolean) {
        if (deleteIfEmpty && model.isNew && model.entry.value?.isEmpty() == true) {
            Timber.v("new entry is empty. delete it")
            answers.logCustom(CustomEvent("detail.deleteNewItem"))
            model.entry.value?.delete()
        } else {
            if (model.isModified) {
                model.save()
            }
        }
    }

    private fun onTaskRemoved() {
        Timber.i("onTaskRemoved")
        answers.logCustom(CustomEvent("detail.onTaskRemoved"))
        saveOnDestroy(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.apply {
            currentEntryID?.let {
                this.putLong(IntentUtils.KEY_ENTRY_ID, it)
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        Timber.i("onDestroy")
        setProgressVisible(false)

        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)

        tickTimer?.dispose()

        saveOnDestroy(true)

        super.onDestroy()
    }

    private fun handleIntent(intent: Intent, savedInstanceState: Bundle?) {
        Timber.i("handleIntent($intent, $savedInstanceState)")
        var entryID: Long? = null
        var newEntry: Entry? = null
        var isNewDocument = false

        savedInstanceState?.let { bundle ->
            if (bundle.containsKey(IntentUtils.KEY_ENTRY_ID)) {
                entryID = bundle.getLong(IntentUtils.KEY_ENTRY_ID)
                disablePostponedTransitions = false
            }
        }

        val event = CustomEvent("detail.init")
        intent.action?.let { event.putCustomAttribute("action", it) }

        if (null == entryID) {
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    isNewDocument = true
                    disablePostponedTransitions = true
                    newEntry = Entry()

                    if (intent.hasExtra(IntentUtils.KEY_CATEGORY_ID)) {
                        val categoryId = intent.getLongExtra(IntentUtils.KEY_CATEGORY_ID, 0)
                        DatabaseHelper.getCategoryByID(categoryId)?.let {
                            newEntry?.category = it
                        }
                    }

                    if (intent.hasExtra(IntentUtils.KEY_ENTRY_TYPE)) {
                        val type = Entry.EntryType.values()[intent.getIntExtra(
                            IntentUtils.KEY_ENTRY_TYPE,
                            0
                        )]
                        event.putCustomAttribute("type", type.name)
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
                                if (intent.hasExtra(Intent.EXTRA_SUBJECT)) intent.getStringExtra(
                                    Intent.EXTRA_SUBJECT
                                ) else ""
                        }.also { entry ->
                            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                                entry.entryStream = streamUri
                                event.putCustomAttribute("hasStream", 1)
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

        if (intent.hasExtra(IntentUtils.KEY_AUDIO_BUNDLE)) {
            intent.getParcelableExtra<Intent>(IntentUtils.KEY_AUDIO_BUNDLE)
                ?.let { audioIntent = it }
            intent.removeExtra(IntentUtils.KEY_AUDIO_BUNDLE)
        }

        answers.logCustom(event)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.appunti_detail_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
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
            R.id.menu_action_share -> dispatchShareEntryIntent()
            android.R.id.home -> onBackPressed()
        }
        return true
    }

    @HunterDebug
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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

    override fun onBackPressed() {
        progressDialog?.let { dialog ->
            if (dialog.isShowing) {
                dismissProgressDialog()
                return
            }
        }

        if (isBottomSheetOpened()) {
            closeBottomSheet()
            return
        }

        super.onBackPressed()
    }

    /**
     * Result from [IMAGE_CAPTURE_REQUEST_CODE]
     */
    private fun onImageCaptured(dstFile: RelativePath?) {
        Timber.i("onImageCaptured(${dstFile?.absolutePath})")
        answers.logCustom(CustomEvent("detail.attachCamera.result"))

        dstFile?.let { dstFile ->
            model.addAttachment(dstFile) { success, throwable ->
                doOnMainThread {
                    throwable?.let {
                        showToastMessage(it.localizedMessage)
                    } ?: run {
                        showToastMessage(getString(R.string.image_added))
                    }
                    invalidate(UPDATE_ATTACHMENTS)
                }
            }
        }
    }

    override fun onAudioPermissionsGranted() {
        dispatchVoiceRecordingIntent()
    }

    @HunterDebug
    override fun onAudioPermissionsDenied(shouldShowRequestPermissionRationale: Boolean) {
        if (shouldShowRequestPermissionRationale) {
            IntentUtils.showPermissionsDeniedDialog(
                this,
                R.string.permissions_audio_required_dialog_body
            )
        } else {
            showToastMessage(getString(R.string.permissions_required))
        }
    }

    override fun onAudioCaptured(audioUri: Uri?, result: String?) {
        if (result.isNotNullOrEmpty()) {
            appendText(result!!)
        } else {
            showToastMessage(getString(R.string.no_transcription_found))
        }
        audioUri?.let { uri ->
            val timeStamp =
                SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT)
                    .format(Date())
            addAttachmentToEntry(uri, timeStamp)
        }
    }

    private fun changeEntryCategory(categoryID: Long) {
        answers.logCustom(CustomEvent("detail.pickCategory.result"))

        Timber.i("changeEntryCategory($categoryID)")
        if (model.setEntryCategory(categoryID)) {
            invalidate(UPDATE_CATEGORY)
        }
    }

    private fun addAttachmentToEntry(uri: Uri, displayName: String? = null) {
        Timber.i("addAttachmentToEntry($uri, $displayName)")
        answers.logCustom(CustomEvent("detail.attachImage.result"))
        setProgressVisible(true)
        showToastMessage(getString(R.string.adding_file))

        model.addAttachment(uri, displayName) { success, throwable ->
            Timber.v("addAttachment result=$success")
            doOnMainThread {
                throwable?.let {
                    showToastMessage(it.localizedMessage ?: getString(R.string.oh_snap))
                } ?: run {
                    showToastMessage(getString(R.string.file_added))
                }
                invalidate(UPDATE_ATTACHMENTS)
            }
            setProgressVisible(false)
        }
    }

    // ENTRY TEXT LISTENERS

    @Suppress("UNUSED_PARAMETER")
    private fun updateEntryText(forceUpdate: Boolean = false) {
        updateEntryText(entryText.text, 0, 0, 0, forceUpdate)
    }

    private fun updateEntryText(
        text: CharSequence?,
        start: Int,
        count: Int,
        after: Int,
        forceUpdate: Boolean = false
    ) {
        if (forceUpdate || currentFocus == entryText) {
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
            entryCategory.visibility =
                if (model.entry.value?.category == null) View.INVISIBLE else View.VISIBLE
        }
        sharedElementEnterTransition.doOnStart {
            entryCategory.visibility =
                if (model.entry.value?.category == null) View.INVISIBLE else View.VISIBLE
        }
    }

    // BOTTOM APP BAR

    private fun setNavigationMenuPicker(attachmentMenu: Boolean) {
        val speechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        with(navigationView.menu) {
            findItem(R.id.menu_action_camera).isVisible = attachmentMenu
            findItem(R.id.menu_action_image).isVisible = attachmentMenu
            findItem(R.id.menu_action_file).isVisible = attachmentMenu
            findItem(R.id.menu_action_address).isVisible = attachmentMenu
            findItem(R.id.menu_action_voice).isVisible = attachmentMenu && speechRecognitionAvailable
            findItem(R.id.menu_action_category).isVisible = !attachmentMenu
            findItem(R.id.menu_action_delete).isVisible = !attachmentMenu
            findItem(R.id.menu_action_list).isVisible = !attachmentMenu && model.entry.value?.entryType == Entry.EntryType.TEXT
            findItem(R.id.menu_action_text).isVisible = !attachmentMenu && model.entry.value?.entryType == Entry.EntryType.LIST
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
                (navigationView.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    it.height
            }
        }
    }

    // NAVIGATION VIEW

    private fun setupNavigationView() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_action_category -> dispatchPickCategoryIntent()

                R.id.menu_action_delete -> toggleDelete()

                R.id.menu_action_image -> dispatchOpenImageIntent()

                R.id.menu_action_file -> dispatchOpenFileIntent()

                R.id.menu_action_camera -> dispatchTakePictureIntent()

                R.id.menu_action_list -> convertEntryToList()

                R.id.menu_action_text -> convertEntryToText()

                R.id.menu_action_address -> insertCurrentAddress()

                R.id.menu_action_voice -> askForAudioPermission()
            }
            closeBottomSheet()
            true
        }
    }

    // External Intents

    private fun dispatchPickCategoryIntent() {
        answers.logCustom(CustomEvent("detail.pickCategory"))

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
        answers.logCustom(CustomEvent("detail.shareEntry"))

        model.entry.whenNotNull { entry ->
            IntentUtils.createShareEntryIntent(this, entry).also {
                startActivity(Intent.createChooser(it, resources.getString(R.string.share)))
            }
            answers.logShare(ShareEvent())
        }
    }

    private fun dispatchOpenFileIntent() {
        answers.logCustom(CustomEvent("detail.attachImage").putCustomAttribute("type", "file"))
        IntentUtils.createPickDocumentIntent(this).also {
            startActivityForResult(it, OPEN_FILE_REQUEST_CODE)
        }
    }

    private fun dispatchOpenImageIntent() {
        answers.logCustom(CustomEvent("detail.attachImage").putCustomAttribute("type", "image"))
        IntentUtils.createPickImageIntent(this).also {
            startActivityForResult(it, OPEN_FILE_REQUEST_CODE)
        }
    }

    private fun dispatchTakePictureIntent() {
        answers.logCustom(CustomEvent("detail.attachCamera"))
        model.entry.whenNotNull { entry ->
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    val photoFile = try {
                        FileSystemUtils.createImageFile(this, entry)
                    } catch (ex: IOException) {
                        Crashlytics.logException(ex)
                        showToastMessage(ex.localizedMessage)
                        ex.printStackTrace()
                        null
                    }

                    photoFile?.also { file ->
                        Timber.v("photoFile = $file")
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

    val bottomSheetCallback = object :
        BottomSheetBehavior.BottomSheetCallback() {
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
    }

    private fun closeBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun openBottomSheet() {
        answers.logCustom(CustomEvent("detail.bottomSheet.open"))
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

    private val UPDATE_TITLE = 1 shl 0
    private val UPDATE_TYPE_AND_TEXT = 1 shl 1
    private val UPDATE_CATEGORY = 1 shl 2
    private val UPDATE_ATTACHMENTS = 1 shl 3
    private val UPDATE_REMOTE_URLS = 1 shl 10

    private val UPDATE_CREATION_DATE = 1 shl 4
    private val UPDATE_MODIFIED_DATE = 1 shl 5

    private val UPDATE_PINNED = 1 shl 6
    private val UPDATE_ARCHIVED = 1 shl 7
    private val UPDATE_DELETED = 1 shl 8
    private val UPDATE_REMINDER = 1 shl 9

    private val UPDATE_MENU = UPDATE_PINNED or UPDATE_ARCHIVED or UPDATE_DELETED or UPDATE_REMINDER

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
        Timber.i("onEntryChanged(${entry.entryType})")

        if (currentEntryID == entry.entryID) {
            Timber.w("same entry id, skipping updates...")
            return
        }

        currentEntryID = entry.entryID

        pauseListeners = true

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

        audioIntent?.let {
            audioIntent = null
            onAudioCaptured(it)
        }

        pauseListeners = false
    }


    /**
     * update entry type (list/text) and content
     */
    private fun updateEntryTypeAndText(entry: Entry) {
        entryText.visibility =
            if (entry.entryType == Entry.EntryType.TEXT) View.VISIBLE else View.GONE
        detailRecycler.visibility =
            if (entry.entryType == Entry.EntryType.LIST) View.VISIBLE else View.GONE

        if (entry.entryType == Entry.EntryType.TEXT) {
            clearListRecyclerView()
            entryText.setText(entry.entryText)

        } else if (entry.entryType == Entry.EntryType.LIST) {
            setupListRecyclerView(entry)
        }

        // clear current focus on touch outside of the recycler view
        nestedScrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    clearCurrentFocus()
                    false
                }
                else -> false
            }
        }
    }

    /**
     * Reset the list recycler and adapter
     */
    private fun clearListRecyclerView() {
        detailRecycler.adapter = null

        detailListAdapter?.let { adapter ->
            adapter.saveAction = null
            adapter.deleteAction = null
            adapter.linkClickListener = null
            adapter.linkLongClickListener = null
        }

        detailListAdapter = null
        nestedScrollView.setOnTouchListener(null)
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
                        entry.entryCreationDate.atZone().formatDiff(
                            this,
                            FormatStyle.MEDIUM,
                            FormatStyle.SHORT
                        )
                    )
                    textSwitcher.setText(lastModifiedString)
                    tickNext(TICKER_STEP_MODIFIED)
                }

                TICKER_STEP_MODIFIED -> {
                    val lastModifiedString = resources.getString(
                        R.string.last_modified,
                        entry.entryModifiedDate.atZone().formatDiff(
                            this,
                            FormatStyle.MEDIUM,
                            FormatStyle.SHORT
                        )
                    )
                    textSwitcher.setText(lastModifiedString)
                    tickNext(TICKER_STEP_ALARM)
                }

                TICKER_STEP_ALARM -> {
                    if (entry.hasReminder()) {
                        val reminderDate = entry.entryAlarm!!.atZone()
                        textSwitcher.setText(
                            reminderDate.formatDiff(
                                this,
                                FormatStyle.MEDIUM,
                                FormatStyle.SHORT
                            )
                        )
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

        val remoteUrls = entry.getRemoteUrls()

        if (remoteUrlListAdapter == null) {
            remoteUrlListAdapter = DetailRemoteUrlListAdapter(this).also {
                it.deleteAction = { remoteUrl -> removeEntryRemoteUrl(remoteUrl) }
                it.linkClickAction = { remoteUrl ->
                    answers.logCustom(CustomEvent("detail.remoteUrl.click"))
                    try {
                        IntentUtils.createRemoteUrlViewIntent(this, remoteUrl).also { intent ->
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            remoteUrlsRecycler.adapter = remoteUrlListAdapter
        }

        remoteUrlsRecycler.visibility = if (remoteUrls.isNullOrEmpty()) View.GONE else View.VISIBLE
        remoteUrlListAdapter?.update(remoteUrls)
    }

    private fun updateEntryAttachmentsList(entry: Entry) {
        if (attachmentsListAdapter == null) {
            attachmentsListAdapter = DetailAttachmentsListAdapter(this)
            attachmentsRecycler.adapter = attachmentsListAdapter
        }

        val attachments = entry.getAttachments()
        attachmentsRecycler.visibility =
            if (attachments.isNullOrEmpty()) View.GONE else View.VISIBLE

        attachmentsListAdapter?.cardColor = entry.getAttachmentColor(this)
        attachmentsListAdapter?.update(attachments)
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
        attachmentsListAdapter?.cardColor = entry.getAttachmentColor(this)
    }

    private fun updateMenu(menu: Menu?, entry: Entry) {
        Timber.i("updateMenu($entry)")
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
                setIcon(R.drawable.appunti_sharp_delete_24_outline_selector)
                setTitle(R.string.delete)
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
        answers.logCustom(CustomEvent("detail.remoteUrl.delete"))
        model.hideRemoteUrl(remoteUrl) { result, throwable ->
            doOnMainThread {
                throwable?.let {
                    showToastMessage(throwable.localizedMessage ?: getString(R.string.oh_snap))
                } ?: run {
                    invalidate(UPDATE_REMOTE_URLS)
                }
            }
        }
    }

    private fun viewEntryAttachment(attachment: Attachment) {
        answers.logCustom(CustomEvent("detail.attachment.click"))

        Timber.v("attachment type: %s", attachment.attachmentMime)

        if (attachment.isVoice()) {
            val dialog = MediaPlayerDialogFragment()
            dialog.arguments = Bundle().apply {
                putLong(Constants.KEY_ID, attachment.attachmentID)
                putBoolean(Constants.KEY_AUTOPLAY, true)
            }
            dialog.show(supportFragmentManager, MediaPlayerDialogFragment::class.java.name)
            return
        }

        try {
            IntentUtils.createAttachmentViewIntent(this, attachment).also {
                startActivity(it)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            Crashlytics.logException(e)
        }
    }

    private fun shareEntryAttachment(attachment: Attachment) {
        answers.logCustom(CustomEvent("detail.attachment.share"))
        try {
            IntentUtils.createAttachmentShareIntent(this, attachment).also {
                startActivity(Intent.createChooser(it, resources.getString(R.string.share)))
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            Crashlytics.logException(e)
        }
    }

    /**
     * Ask the use to confirm to remove the current attachment
     * [removeEntryAttachment] will be called then
     */
    private fun askToDeleteAttachment(attachment: Attachment) {
        AlertDialog
            .Builder(this)
            .setCancelable(true)
            .setTitle(getString(R.string.confirm))
            .setMessage(getString(R.string.confirm_attachment_delete_body))
            .setPositiveButton(android.R.string.yes) { dialog, _ ->
                dialog.dismiss()
                removeEntryAttachment(attachment)
            }
            .setNegativeButton(android.R.string.no) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun removeEntryAttachment(attachment: Attachment) {
        answers.logCustom(CustomEvent("detail.attachment.delete"))
        model.removeAttachment(attachment) { result, throwable ->
            doOnMainThread {
                throwable?.let { throwable ->
                    showToastMessage(throwable.localizedMessage ?: getString(R.string.oh_snap))
                } ?: run {
                    Timber.v("[${currentThread()}] success = $result")
                    showToastMessage("File has been removed")
                }
                invalidate(UPDATE_ATTACHMENTS)
            }
        }
    }

    private fun convertEntryToList() {
        answers.logCustom(CustomEvent("detail.convertEntryToList"))
        if (model.convertEntryToList()) {
            invalidate(UPDATE_TYPE_AND_TEXT)
        }
    }

    private fun convertEntryToText() {
        answers.logCustom(CustomEvent("detail.convertEntryToText"))
        if (model.convertEntryToText(detailListAdapter.toString())) {
            invalidate(UPDATE_TYPE_AND_TEXT)
        }
    }

    private fun togglePin() {
        answers.logCustom(CustomEvent("detail.togglePin"))
        val currentValue = model.entry.value?.isPinned()
        if (model.setEntryPinned(currentValue == false)) {
            showToastMessage(
                resources.getQuantityString(
                    if (currentValue == true)
                        R.plurals.entries_unpinned_title else R.plurals.entries_pinned_title, 1, 1
                )
            )
            invalidate(UPDATE_PINNED)
        }
    }

    private fun toggleDelete() {
        answers.logCustom(CustomEvent("detail.toggleDelete"))
        model.entry.whenNotNull { askToDeleteEntry() }
    }

    private fun toggleArchive() {
        answers.logCustom(CustomEvent("detail.toggleArchive"))
        model.entry.whenNotNull { entry ->
            val currentValue = entry.isArchived()
            if (model.setEntryArchived(!currentValue)) {
                showToastMessage(
                    resources.getQuantityString(
                        if (currentValue)
                            R.plurals.entries_unarchived_title else R.plurals.entries_archived_title,
                        1,
                        1
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
        answers.logCustom(CustomEvent("detail.toggleReminder"))
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
                                showToastMessage(getString(R.string.reminder_set))
                            }
                        }
                    }
                    .setNeutralButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton(getString(R.string.remove)) { dialog, _ ->
                        dialog.dismiss()
                        if (model.removeReminder()) {
                            showToastMessage(getString(R.string.reminder_removed))
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
                        showToastMessage(getString(R.string.reminder_set))
                        invalidate(UPDATE_REMINDER)
                    }
                }
            }
        }
    }

    private fun askToDeleteEntry() {
        Timber.i("askToDeleteEntries()")
        AlertDialog
            .Builder(this)
            .setTitle(R.string.confirm)
            .setMessage(resources.getQuantityString(R.plurals.entries_delete_action_question, 1, 1))
            .setPositiveButton(R.string.yes) { dialog, _ ->
                dialog.dismiss()
                deleteEntry()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create().show()
    }

    @SuppressLint("CheckResult")
    private fun deleteEntry() {
        Timber.i("deleteEntry()")

        if (model.deleteEntry()) {
            Toast.makeText(
                this,
                resources.getQuantityString(R.plurals.entries_deleted_title, 1, 1),
                Toast.LENGTH_SHORT
            ).show()
            onBackPressed()
        } else {
            Toast.makeText(this, getString(R.string.oh_snap), Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertCurrentAddress() {
        Timber.i("insertCurrentAddress")
        answers.logCustom(CustomEvent("detail.address.insert"))

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("permission required")
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                IntentUtils.showPermissionsDeniedDialog(
                    this,
                    R.string.permissions_required_dialog_body
                )
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION_CODE
                )
            }
        } else {
            Timber.v("permission granted!")
            waitForCurrentAddress()
        }
    }

    private fun waitForCurrentAddress() {
        Timber.i("waitForCurrentAddress")
        progressDialog = ProgressDialog(this).apply {
            this.setMessage(getString(R.string.retrieving_current_location))
            this.isIndeterminate = true
        }

        val disposable = LocationRepository.getInstance(this)
            .getCurrentLocation(10, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .take(1)
            .subscribeBy(  // named arguments for lambda Subscribers
                onNext = { address ->
                    insertAddress(address)
                },
                onError = {
                    dismissProgressDialog()
                    Toast.makeText(this, R.string.oh_snap, Toast.LENGTH_SHORT).show()
                },
                onComplete = {
                    dismissProgressDialog()
                }
            ).addTo(autoDisposable)

        progressDialog?.setOnCancelListener {
            Timber.v("User cancelled progress dialog")
            answers.logCustom(CustomEvent("detail.address.progress.cancelled"))
            if (!disposable.isDisposed) {
                disposable.dispose()
            }
        }
        progressDialog?.show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun insertAddress(address: Address) {
        Timber.i("insertAddress($address)")

        if (model.entry.value?.entryType == Entry.EntryType.LIST) {
            insertAddressIntoList(address)
        } else {
            insertAddressAsPlainText(address)
            insertAddressAsPlainText(address)
        }
    }

    private fun insertAddressIntoList(address: Address) {
        for (i in 0..address.maxAddressLineIndex) {
            detailListAdapter?.addItem(address.getAddressLine(i))
        }

        address.url?.let {
            detailListAdapter?.addItem(address.url)
        } ?: run {
            if (address.hasLatitude() && address.hasLongitude()) {
                val geoUrl =
                    "http://maps.google.com/maps?z=22&q=${address.latitude},${address.longitude}"
                detailListAdapter?.addItem(geoUrl)
            }
        }
    }

    private fun insertAddressAsPlainText(address: Address) {
        entryText.append("\n\n")
        for (i in 0..address.maxAddressLineIndex) {
            entryText.append(address.getAddressLine(i))
            entryText.append("\n")
        }

        address.url?.let {
            entryText.append("\n")
            entryText.append(address.url)
            entryText.append("\n")
        }

        if (address.hasLatitude() && address.hasLongitude()) {
            val geoUrl =
                "http://maps.google.com/maps?z=22&q=${address.latitude},${address.longitude}"
            entryText.append(geoUrl)
            entryText.append("\n")
        }

        entryText.append("\n")

        entryText.setSelection(entryText.length())
        entryText.requestFocus()

        updateEntryText(true)
    }

    @HunterDebug
    private fun appendText(text: String) {
        if (model.entry.value?.entryType == Entry.EntryType.TEXT) {
            appendTextAsPlain(text)
        } else {
            appendTextAsList(text)
        }
    }

    @SuppressLint("SetTextI18n")
    @HunterDebug
    private fun appendTextAsPlain(text: String) {
        val isBlank = entryText.text!!.isBlank()

        if (!isBlank) {
            entryText.append("\n")
        }
        entryText.append(text)
        entryText.requestFocus()
        updateEntryText(true)
    }

    @HunterDebug
    private fun appendTextAsList(text: String) {
        detailListAdapter?.addItem(text)
    }

    private fun showToastMessage(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun setProgressVisible(visible: Boolean) {
        toolbarProgress.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

// DETAIL LIST METHODS

    internal fun clearCurrentFocus() {
        if (currentFocus is TextView) {
            Timber.i("clearCurrentFocus($currentFocus")
            currentFocus?.hideSoftInput()
        }
        linearLayout.requestFocus()
    }

    /**
     * Save action listener
     */
    private val saveEntryListAction: ((DetailListAdapter, String) -> Unit) = { adapter, text ->
        model.setEntryText(text)
    }

    private val deleteEntryListItemAction: ((DetailListAdapter, DetailListAdapter.DetailEntryViewHolder, EntryListJsonModel.EntryJson) -> Boolean) =
        { adapter, holder, entry ->
            var result = false
            Timber.i("deleteEntryListItemAction(entry=$entry, selection=${holder.text.selectionStart}, ${holder.text.selectionEnd})")

            if (!holder.text.hasSelection() && holder.text.selectionStart == 0 && holder.text.selectionEnd == 0) {
                if (adapter.isFirstEntry(entry)) {
                    // do nothing
                    result = false
                } else {
                    result = true
                    adapter.mergeWithPrevious(entry)
                }
            }

            result
        }

    private fun setupListRecyclerView(entry: Entry) {
        detailListAdapter = DetailListAdapter(this).also { adapter ->
            adapter.setData(entry)
            adapter.saveAction = saveEntryListAction
            adapter.deleteAction = deleteEntryListItemAction
            adapter.linkClickListener = linkClickListener
            adapter.linkLongClickListener = linkLongClickListener
        }

        val animator = detailRecycler.itemAnimator
        animator?.apply {
            addDuration = 0
            moveDuration = 0
            removeDuration = 0
            changeDuration = 0
        }

        detailRecycler.adapter = detailListAdapter

        detailRecycler.itemAnimator = SlideInUpAnimator().apply {
            addDuration = 100
            removeDuration = 100
            changeDuration = 100
            moveDuration = 100
        }
    }

// END DETAIL LIST METHODS


    companion object {
        // text switcher consts
        private const val TICKER_STEP_CREATED = 1
        private const val TICKER_STEP_MODIFIED = 2
        private const val TICKER_STEP_ALARM = 3
    }
}


data class InsertedItem(
    val index: Int,
    val selectionStart: Int? = null
)

class DetailListAdapter(private var activity: DetailActivity) :
    RecyclerView.Adapter<DetailListAdapter.DetailViewHolder>() {
    private var dataHolder = EntryListJsonModel()
    private var inflater = LayoutInflater.from(activity)
    private val answers: Answers by lazy { Answers.getInstance() }

    var saveAction: ((DetailListAdapter, String) -> (Unit))? = null
    var deleteAction: ((DetailListAdapter, DetailEntryViewHolder, EntryListJsonModel.EntryJson) -> Boolean)? =
        null
    var linkClickListener: BetterLinkMovementMethod.OnLinkClickListener? = null
    var linkLongClickListener: BetterLinkMovementMethod.OnLinkLongClickListener? = null

    init {
        setHasStableIds(true)
    }

    private fun postSave() {
        Timber.i("postSave")
        doOnScheduler(Schedulers.computation()) {
            saveAction?.invoke(this, dataHolder.toJson())
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

    internal fun getItem(position: Int): EntryListJsonModel.EntryJson {
        return dataHolder.getItem(position)
    }

    override fun getItemViewType(position: Int): Int {
        return dataHolder.getItemType(position)
    }

    private var insertedItem: InsertedItem? = null

    private fun addItem() {
        Timber.v("addItem")
        answers.logCustom(CustomEvent("detail.list.addItem"))

        doOnMainThread {
            dataHolder.addItem(checked = false).also { index ->
                insertedItem = InsertedItem(index)
                Timber.v("insertedItem = $insertedItem")
                notifyItemInserted(index)
                postSave()
            }
        }
    }

    internal fun addItem(text: String) {
        Timber.v("addItem($text)")
        answers.logCustom(CustomEvent("detail.list.addItem"))

        doOnMainThread {
            dataHolder.addItem(text = text, checked = false).also { index ->
                insertedItem = InsertedItem(index)
                Timber.v("insertedItem = $insertedItem")
                notifyItemInserted(index)
                postSave()
            }
        }
    }

    private fun insertItemAfter(entry: EntryListJsonModel.EntryJson, text: String?) {
        answers.logCustom(CustomEvent("detail.list.addItemAfter"))

        Timber.i("insertItemAfter($entry)")
        val id = entry.id
        doOnMainThread {
            dataHolder.insertItem(id, text).also { nextIndex ->
                Timber.v("nextIndex = $nextIndex")

                if (nextIndex > -1) {
                    insertedItem = InsertedItem(nextIndex, 0)
                }

                notifyDataSetChanged()
                postSave()
            }
        }
    }

    /**
     * Delete the passed [Entry] and remove the current focus
     */
    internal fun deleteItem(entry: EntryListJsonModel.EntryJson) {
        answers.logCustom(CustomEvent("detail.list.deleteItem"))

        Timber.i("deleteItem($entry)")
        doOnMainThread {
            activity.clearCurrentFocus()
            dataHolder.deleteItem(entry)?.let { index ->
                notifyItemRemoved(index)
                postSave()
            }
        }
    }

    internal fun mergeWithPrevious(entry: EntryListJsonModel.EntryJson) {
        answers.logCustom(CustomEvent("detail.list.mergeWithPrevious"))

        Timber.i("mergeWithPrevious($entry)")
        doOnMainThread {
            getPreviousEntryIndex(entry).let { previousIndex ->
                if (previousIndex > -1) {
                    val previousEntry = getItem(previousIndex)
                    val previousTextLength = previousEntry.text.length

                    dataHolder.deleteItem(entry)?.let {
                        previousEntry.text += entry.text
                        insertedItem = InsertedItem(previousIndex, previousTextLength)

                        notifyDataSetChanged()
                        postSave()
                    }
                }
            }
        }
    }

    private fun toggleItem(entry: EntryListJsonModel.EntryJson) {
        answers.logCustom(CustomEvent("detail.list.toggleItem"))

        doOnMainThread {
            dataHolder.toggle(entry)?.also { result ->
                notifyItemMoved(result.first, result.second)
                postSave()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        return if (viewType == EntryListJsonModel.TYPE_NEW_ENTRY) {
            val view = inflater.inflate(R.layout.appunti_detail_new_entry_list_item, parent, false)

            view.setOnClickListener {
                Timber.v("buttonAdd onClick")
                addItem()
            }

            DetailNewEntryViewHolder(view).apply {
                buttonAdd.background = MaterialBackgroundUtils.newEntryListItem(activity)
            }

        } else {
            val view =
                inflater.inflate(R.layout.appunti_detail_entry_list_item_checkable, parent, false)
            DetailEntryViewHolder(view).also { holder ->

                // toggle the delete button visibility based on the current focus
                holder.text.setOnFocusChangeListener { _, hasFocus ->
                    holder.deleteButton.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                }

                holder.text.setOnKeyListener { _, keyCode, event ->
                    var returnType = false
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (keyCode == KeyEvent.KEYCODE_DEL) {
                            Timber.v("KEYCODE_DEL")
                            returnType =
                                deleteAction?.invoke(this, holder, getItem(holder.adapterPosition))
                                    ?: true
                        }
                    }
                    returnType
                }

                // delete the current entry item
                holder.deleteButton.setOnClickListener {
                    deleteItem(getItem(holder.adapterPosition))
                }

                holder.checkedActionListener = { _, _ ->
                    activity.clearCurrentFocus()
                    toggleItem(
                        getItem(holder.adapterPosition)
                    )
                }
            }
        }
    }

    @Suppress("ControlFlowWithEmptyBody")
    override fun onBindViewHolder(baseHolder: DetailViewHolder, position: Int) {
        if (baseHolder.itemViewType == EntryListJsonModel.TYPE_NEW_ENTRY) {
            // empty on purpose
        } else {
            val holder = baseHolder as DetailEntryViewHolder
            val entry = getItem(position)

            holder.text.text = entry.text
            holder.isChecked = holder.itemViewType == EntryListJsonModel.TYPE_CHECKED

            holder.text.removeTextChangedListener(holder.textListener)

            holder.textListener = holder.text.addTextWatcherListener {
                onTextChanged { textView, s, start, before, count ->
                    if (activity.currentFocus == textView) {
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
                            Timber.v("final text = '$s'")
                            entry.text = s.toString()
                            postSave()
                        }
                    }
                }

                afterTextChanged { textView, s ->
                    if (s.isNullOrBlank() || s.isNullOrEmpty()) {
                        textView.movementMethod = ArrowKeyMovementMethod.getInstance()
                    } else {
                        BetterLinkMovementMethod.linkify(Linkify.ALL, textView)
                            .setOnLinkClickListener(linkClickListener)
                            .setOnLinkLongClickListener(linkLongClickListener)
                    }
                }
            }


            holder.text.setOnEditorActionListener { _, actionId, _ ->
                Timber.i("setOnEditorActionListener = $actionId")
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    activity.clearCurrentFocus()
                    postSave()
                    true
                } else {
                    false
                }
            }

            // new item requested. Give it focus and show the keyboard
            if (insertedItem != null && insertedItem?.index == position) {
                Timber.v("insertedItem found = $insertedItem")

                val selectionStart = insertedItem?.selectionStart

                val action = {
                    holder.text.showSoftInput()
                    with(holder.text as EditText) {
                        selectionStart?.let {
                            this.setSelection(it)
                        }
                    }
                    Unit
                }

                if (activity.currentFocus is TextView) {
                    action.invoke()
                } else {
                    holder.post(action)
                }

                insertedItem = null
            }
        }
    }

    /**
     * Return true if the given entry is the first visible
     */
    fun isFirstEntry(entry: EntryListJsonModel.EntryJson) = dataHolder.isFirstEntry(entry)

    private fun getPreviousEntryIndex(entry: EntryListJsonModel.EntryJson) =
        dataHolder.getPreviousItemIndex(entry)

    open class DetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(android.R.id.text1)

        @Suppress("unused")
        inline fun postDelayed(delayInMillis: Long, crossinline action: () -> Unit): Runnable {
            val runnable = Runnable { action() }
            itemView.postDelayed(runnable, delayInMillis)
            return runnable
        }

        inline fun post(crossinline action: () -> Unit): Runnable {
            val runnable = Runnable { action() }
            itemView.post(runnable)
            return runnable
        }
    }

    open class DetailNewEntryViewHolder(itemView: View) : DetailViewHolder(itemView) {
        val buttonAdd: View = itemView.findViewById(R.id.buttonAdd)
    }

    class DetailEntryViewHolder(itemView: View) : DetailViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val deleteButton: View = itemView.findViewById(R.id.deleteButton)
        var textListener: TextWatcher? = null

        var checkedActionListener: ((DetailEntryViewHolder, Boolean) -> Unit)? = null

        private val checkedChangeListener =
            CompoundButton.OnCheckedChangeListener { view, checked ->
                checkedActionListener?.invoke(this, checked)
            }

        var isChecked: Boolean
            get() = itemViewType == EntryListJsonModel.TYPE_CHECKED
            set(value) {
                // remove the checked listener before setting at runtime
                checkbox.setOnCheckedChangeListener(null)

                if (value) {
                    checkbox.isChecked = true
                    text.paintFlags = text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    checkbox.isChecked = false
                    text.paintFlags = text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                // restore the checked listener
                checkbox.setOnCheckedChangeListener(checkedChangeListener)
            }
    }
}
