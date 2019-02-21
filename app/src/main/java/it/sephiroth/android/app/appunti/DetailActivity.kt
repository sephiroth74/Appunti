package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
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
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.core.text.util.LinkifyCompat
import androidx.core.transition.doOnEnd
import androidx.core.transition.doOnStart
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.emoji.widget.SpannableBuilder
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.dbflow5.structure.save
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.disposables.Disposable
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.*
import it.sephiroth.android.app.appunti.graphics.MaterialBackgroundDrawable
import it.sephiroth.android.app.appunti.graphics.MaterialShape
import it.sephiroth.android.app.appunti.graphics.MaterialShapeDrawable
import it.sephiroth.android.app.appunti.models.DetailViewModel
import it.sephiroth.android.app.appunti.utils.FileSystemUtils
import it.sephiroth.android.app.appunti.utils.IntentUtils
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.activity_detail.view.*
import kotlinx.android.synthetic.main.appunti_detail_attachment_item.view.*
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.FormatStyle
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


@Suppress("NAME_SHADOWING")
class DetailActivity : AppuntiActivity() {
    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_detail

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var model: DetailViewModel
    private var currentEntry: Entry? = null
    private var changeTimer: Disposable? = null
    private var shouldRemoveAlarm: Boolean = false
    private var isNewDocument: Boolean = false

    // temporary file used for pictures taken with camera
    private var mCurrentPhotoPath: File? = null

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
        model.entry.observe(this, Observer { entry ->
            Timber.i("Model Entry Changed = $entry")
            onEntryChanged(entry)
        })

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        setupBottomSheet()
        setupNavigationView()
        setupBottomAppBar()
        setupSharedElementsTransition()

        closeBottomSheet()

        // UI elements listeners
        entryCategory.setOnClickListener { dispatchPickCategoryIntent() }
        entryTitle.doOnTextChanged { s, start, count, after -> onEntryTitleChanged(s, start, count, after) }
        entryText.doOnTextChanged { s, start, count, after -> onEntryTextChanged(s, start, count, after) }

        entryCategory.background = MaterialBackgroundUtils.categoryChipClickable(this)


        // TODO(Create a custom movement method)
        // entryText.movementMethod = LinkMovementMethod.getInstance()
        entryText.doOnAfterTextChanged { e -> LinkifyCompat.addLinks(e, Linkify.ALL) }

        // handle the current listener
        // TODO(manage intent when activity is destroyed and recreated)
        onNewIntent(intent)
    }

    override fun onDestroy() {
        setProgressVisible(false)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Timber.i("onNewIntent($intent)")
        var entryID = 0L

        intent?.let { intent ->
            Timber.v("action=${intent.action}")
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    isNewDocument = true
                    entryID = 0
                }

                Intent.ACTION_EDIT -> {
                    entryID = intent.getLongExtra(IntentUtils.KEY_ENTRY_ID, 0)
                    shouldRemoveAlarm = intent.getBooleanExtra(IntentUtils.KEY_REMOVE_ALARM, false)
                    isNewDocument = false
                }
            }
        }

        // don't delay the transition if it's a new document
        if (!isNewDocument) {
            postponeEnterTransition()
            model.entryID = entryID
        } else {
            model.createNewEntry()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Timber.i("onActivityResult(requestCode=$requestCode, resultCode=$resultCode, data=$data)")

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CATEGORY_PICK_REQUEST_CODE -> {
                    data?.let { data ->
                        val categoryID = data.getLongExtra(IntentUtils.KEY_CATEGORY_ID, -1)
                        DatabaseHelper.getCategoryByID(categoryID)?.let { category ->
                            model.setEntryCategory(category)
                        }
                    }
                }

                OPEN_FILE_REQUEST_CODE -> {
                    data?.data?.also { uri ->
                        model.entry.value?.let { entry ->
                            onAddAttachment(uri, entry)
                        }
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

    private fun onAddAttachment(uri: Uri, entry: Entry) {
        setProgressVisible(true)

        DatabaseHelper.addAttachmentFromUri(this, Entry(entry), uri) { success, throwable ->
            doOnMainThread {
                throwable?.let {
                    showConfirmation(it.localizedMessage)
                } ?: run {
                    showConfirmation("File added")
                }
            }
            setProgressVisible(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.appunti_detail_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        Timber.i("onPrepareOptionsMenu")
        updateMenu(menu)
        updateMenu(navigationView.menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_action_pin -> onTogglePin()
            R.id.menu_action_archive -> onToggleArchive()
            R.id.menu_action_alarm -> onToggleReminder()
            android.R.id.home -> onBackPressed()
        }
        return true
    }

    // ENTRY TEXT LISTENERS

    @Suppress("UNUSED_PARAMETER")
    private fun onEntryTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        if (currentFocus == entryText) {

            Timber.i("onEntryTextChanged(start=$start, count=$count, after=$after)")

            // TODO(usare timer differenti per titolo e testo)
            changeTimer = rxTimer(changeTimer, 2, TimeUnit.SECONDS) {
                currentEntry?.apply {
                    entryText = text?.toString() ?: ""
                    touch()
                    save()
                }
            }
        }
    }

    // ENTRY TITLE LISTENERS

    @Suppress("UNUSED_PARAMETER")
    private fun onEntryTitleChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        if (currentFocus == entryTitle) {
            changeTimer = rxTimer(changeTimer, 2, TimeUnit.SECONDS) {
                currentEntry?.apply {
                    entryTitle = text?.toString() ?: ""
                    touch()
                    save()
                }
            }
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

                R.id.menu_action_delete -> onToggleDelete()

                R.id.menu_action_share -> dispatchShareEntryIntent()

                R.id.menu_action_image -> dispatchOpenImageIntent()

                R.id.menu_action_file -> dispatchOpenFileIntent()

                R.id.menu_action_camera -> dispatchTakePictureIntent()
            }
            closeBottomSheet()
            true
        }
    }

    // External Intents

    private fun dispatchPickCategoryIntent() {
        model.entry.value?.let { entry ->

            IntentUtils.Categories
                .Builder(this)
                .pickCategory()
                .selectedCategory(entry.category?.categoryID ?: -1).build().also {
                    startActivityForResult(it, CATEGORY_PICK_REQUEST_CODE, null)
                }
        }
    }

    private fun dispatchShareEntryIntent() {
        model.entry.value?.let { entry ->
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
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    FileSystemUtils.createImageFile(this, model.entry.value!!)
                } catch (ex: IOException) {
                    ex.printStackTrace()
                    null
                }

                photoFile?.also { file ->
                    Timber.v("photoFile = ${file.absolutePath}")
                    mCurrentPhotoPath = file
                    val photoURI = FileSystemUtils.getFileUri(this, file)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, IMAGE_CAPTURE_REQUEST_CODE)
                }
            }
        }
    }

    /**
     * Result from [IMAGE_CAPTURE_REQUEST_CODE]
     */
    private fun onImageCaptured(dstFile: File?) {
        Timber.i("onImageCaptured(${dstFile?.absolutePath})")

        dstFile?.let { dstFile ->
            model.entry.value?.let { entry ->

                DatabaseHelper.addAttachment(
                    this,
                    Entry(entry),
                    dstFile,
                    FileSystemUtils.JPEG_MIME_TYPE
                ) { success, throwable ->
                    doOnMainThread {
                        throwable?.let {
                            showConfirmation(it.localizedMessage)
                        } ?: run {
                            showConfirmation("File added")
                        }
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

    private fun onEntryChanged(entry: Entry) {
        Timber.i("onEntryChanged()")

        val diff = EntryDiff.calculateDiff(currentEntry, entry)
        Timber.v("diff=$diff")

        val currentEntryIsNull = currentEntry == null

        currentEntry = Entry(entry)

        if (diff.titleChanged) entryTitle.setText(entry.entryTitle)
        if (diff.textChanged) entryText.setText(entry.entryText)

        if (diff.categoryChanged) {
            entryCategory.text = entry.category?.categoryTitle
            entryCategory.visibility = if (entry.category == null) View.INVISIBLE else View.VISIBLE
            applyEntryTheme(entry)
        }

        if (diff.attachmentsChanged) {
            updateAttachmentsList(entry.attachments)
        }

        invalidateOptionsMenu()

        if (shouldRemoveAlarm) {
            model.removeReminder()
            shouldRemoveAlarm = false
        }

        if (currentEntryIsNull && !isNewDocument) {
            Timber.v("startPostponedEnterTransition")
            entryTitle.doOnPreDraw {
                startPostponedEnterTransition()
                entryTitle.transitionName = null
                entryText.transitionName = null
                entryCategory.transitionName = null
            }
        }
    }

    private fun updateAttachmentsList(attachments: List<Attachment>?) {
        attachmentsContainer.removeAllViews()

        model.entry.value?.let { entry ->

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
                        onRemoveAttachment(attachment)
                    }

                    attachmentsContainer.addView(view)
                }
            }
        }
    }

    private fun applyEntryTheme(entry: Entry) {
        var color = entry.getColor(this)
        Timber.i("applyEntryTheme. color=${color.toString(16)}")

//        color = ColorUtils.setAlphaComponent(color, 201)

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

    private fun onRemoveAttachment(attachment: Attachment) {
        model.entry.value?.let { entry ->
            DatabaseHelper.deleteAttachment(this, Entry(entry), Attachment(attachment)) { result, throwable ->
                throwable?.let { throwable ->
                    showConfirmation(throwable.localizedMessage)
                } ?: run {
                    Timber.v("[${currentThread()}] success = $result")
                    showConfirmation("File has been removed")
                }
            }
        }
    }

    private fun onTogglePin() {
        val currentValue = model.entry.value?.isPinned()
        val result = model.togglePin()

        if (result) {
            showConfirmation(
                resources.getQuantityString(
                    if (currentValue == true)
                        R.plurals.entries_unpinned_title else R.plurals.entries_pinned_title, 1, 1
                )
            )

        }
    }

    private fun onToggleDelete() {
        val currentValue = model.entry.value?.isDeleted()
        if (model.toggleDeleted()) {
            showConfirmation(
                resources.getQuantityString(
                    if (currentValue == true)
                        R.plurals.entries_restored_title else R.plurals.entries_deleted_title, 1, 1
                )
            )

            if (currentValue == false) {
                onBackPressed()
            }
        }
    }

    private fun onToggleArchive() {
        val currentValue = model.entry.value?.isArchived()
        if (model.toggleArchived()) {
            showConfirmation(
                resources.getQuantityString(
                    if (currentValue == true)
                        R.plurals.entries_unarchived_title else R.plurals.entries_archived_title, 1, 1
                )
            )

            if (currentValue == false) {
                onBackPressed()
            }
        }
    }

    private fun onToggleReminder() {
        model.entry.value?.let { entry ->
            if (entry.entryAlarm != null && !entry.isAlarmExpired()) {
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
                    }
                }
            }
        }
    }

    private fun updateMenu(menu: Menu?) {

        model.entry.value?.let { entry ->
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

                    if (entry.hasAlarm() && !entry.isAlarmExpired()) {
                        setIcon(R.drawable.twotone_alarm_24)
                        setTitle(R.string.remove_reminder)
                    } else {
                        setIcon(R.drawable.sharp_alarm_24)
                        setTitle(R.string.add_reminder)
                    }
                }
            }
        }
    }
//
//    @SuppressLint("CheckResult")
//    private fun onToggleLocation() {
////        ensurePermissions()
//        val location = RxLocation(this)
//        val request = LocationRequest
//            .create()
//            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
//            .setNumUpdates(1)
//            .setInterval(500)
//
//        location
//            .location()
//            .updates(request)
//            .flatMap {
//                Timber.v("updated location: $it")
//                location.geocoding().fromLocation(it).toObservable()
//            }.subscribe { address ->
//                Timber.v("address: $address")
//            }
//    }
//
//    @SuppressLint("CheckResult")
//    private fun ensurePermissions() {
//        val permissionName = Manifest.permission.ACCESS_FINE_LOCATION
//
//        if (ContextCompat.checkSelfPermission(this, permissionName) == PackageManager.PERMISSION_DENIED) {
//            Timber.w("permission is denied!")
//        } else {
//
//            val permissions = RxPermissions(this)
//
//            if (permissions.isGranted(permissionName)) {
//                Timber.v("permission is granted")
//            } else {
//
//                if (permissions.isRevoked(permissionName)) {
//                    Timber.w("permission is revoked")
//                } else {
//                    permissions
//                        .requestEach(permissionName)
//                        .subscribe { permission ->
//                            Timber.v("result = $permission, ${permission.name}")
//                            if (permission.granted) {
//                                Timber.v("granted")
//                            } else if (permission.shouldShowRequestPermissionRationale) {
//                                Timber.w("shouldShowRequestPermissionRationale")
//                            }
//                        }
//                }
//            }
//        }
//
//    }

    private fun showConfirmation(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun setProgressVisible(visible: Boolean) {
        toolbarProgress.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    companion object {
        const val CATEGORY_PICK_REQUEST_CODE = 1

        const val OPEN_FILE_REQUEST_CODE = 2

        const val IMAGE_CAPTURE_REQUEST_CODE = 3
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
            var attachmentsChanged: Boolean = true
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
                attachmentsChanged = oldValue?.attachments != newValue?.attachments
            )

        }
    }
}
