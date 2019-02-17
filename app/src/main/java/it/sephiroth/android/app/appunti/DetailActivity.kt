package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.text.set
import androidx.core.text.toSpannable
import androidx.core.transition.doOnEnd
import androidx.core.transition.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.emoji.widget.SpannableBuilder
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.room.Database
import com.dbflow5.config.FlowManager
import com.dbflow5.structure.save
import com.dbflow5.structure.update
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.*
import it.sephiroth.android.app.appunti.models.DetailViewModel
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.activity_detail.view.*
import kotlinx.android.synthetic.main.appunti_detail_attachment_item.view.*
import org.apache.commons.io.FileUtils
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.FormatStyle
import timber.log.Timber
import java.io.File
import java.util.*
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

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
//        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
//        window.sharedElementEnterTransition = AutoTransition()
//        window.sharedElementExitTransition = android.transition.Slide(Gravity.LEFT)
//        window.sharedElementReenterTransition = AutoTransition()
//        window.sharedElementReturnTransition = AutoTransition()

        window.sharedElementReturnTransition = null
//        window.returnTransition = android.transition.Slide(Gravity.LEFT)

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
        entryCategory.setOnClickListener { pickCategory() }
        entryTitle.doOnTextChanged { s, start, count, after -> onEntryTitleChanged(s, start, count, after) }
        entryText.doOnTextChanged { s, start, count, after -> onEntryTextChanged(s, start, count, after) }
        entryText.movementMethod = LinkMovementMethod.getInstance()

        // handle the current listener
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Timber.i("onNewIntent($intent)")
        var entryID = 0

        intent?.let { intent ->
            Timber.v("action=${intent.action}")
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    isNewDocument = true
                    entryID = 0
                }

                Intent.ACTION_EDIT -> {
                    entryID = intent.getIntExtra(KEY_ENTRY_ID, 0)
                    shouldRemoveAlarm = intent.getBooleanExtra(KEY_REMOVE_ALARM, false)
                    isNewDocument = false
                }
            }
        }

        // don't delay the transition if it's a new document
        if (!isNewDocument) postponeEnterTransition()

        Timber.v("entryID=$entryID")
        model.entryID = entryID
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CATEGORY_PICK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                data?.let { data ->
                    val categoryID = data.getIntExtra("categoryID", -1)
                    DatabaseHelper.getCategoryByID(categoryID)?.let { category ->
                        model.setEntryCategory(category)
                    }
                }
            }
        } else if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK) {
            Timber.d("uri: ${data?.data}")

            data?.data?.also { uri ->

                val entry = model.entry.value!!
                val displayName: String = uri.getDisplayName(this) ?: UUID.randomUUID().toString()
                val mimeType = uri.getMimeType(this)

                Timber.v("displayName: $displayName")
                Timber.v("mimeType: $mimeType")


                val baseDir = DatabaseHelper.getAttachmentFilesDir(this, entry)
                val dstFile = Entry.getNextFile(this, entry, baseDir, displayName)

                Timber.v("baseDir=${baseDir.absolutePath}")
                Timber.v("dstFile=$dstFile")

                val relativePath = DatabaseHelper.getFilesDir(this).toURI().relativize(dstFile.toURI())
                Timber.v("relative=$relativePath")

                val stream = contentResolver.openInputStream(uri)
                FileUtils.copyInputStreamToFile(stream, dstFile)
//
                FlowManager.getDatabase(AppDatabase::class.java).beginTransactionAsync {
                    val attachment = Attachment()
                    attachment.attachmentEntryID = entry.entryID
                    attachment.attachmentPath = relativePath.toString()
                    attachment.attachmentTitle = displayName
                    attachment.attachmentMime = mimeType
                    attachment.attachmentOriginalPath = uri.toString()
                    attachment.save()

                    entry.touch().update()
                }
                    .success { transaction, result -> Timber.v("success!") }
                    .error { transaction, throwable -> Timber.w("error = $throwable") }
                    .build()
                    .execute()
            }
        }


        super.onActivityResult(requestCode, resultCode, data)
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
            changeTimer = rxTimer(changeTimer, 1, TimeUnit.SECONDS) {
                currentEntry?.entryText = text?.toString() ?: ""
                currentEntry?.touch()
                currentEntry?.save()
            }
        }
    }

    // ENTRY TITLE LISTENERS

    @Suppress("UNUSED_PARAMETER")
    private fun onEntryTitleChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        if (currentFocus == entryTitle) {
            changeTimer = rxTimer(changeTimer, 300, TimeUnit.MILLISECONDS) {
                currentEntry?.entryTitle = text?.toString() ?: ""
                currentEntry?.touch()
                currentEntry?.save()
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

    private fun setupBottomAppBar() {
        bottomAppBar.navigationIcon.setOnClickListener {
            openOrCloseBottomsheet()
        }

        bottomAppBar.documentPicker.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }

            startActivityForResult(intent, READ_REQUEST_CODE)
        }

        bottomAppBar.imagePicker.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }

            startActivityForResult(intent, READ_REQUEST_CODE)
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
                R.id.menu_action_category -> {
                    pickCategory()
                    closeBottomSheet()
                }

                R.id.menu_action_delete -> {
                    onToggleDelete()
                    closeBottomSheet()
                }

                R.id.menu_action_share -> {
                    onShareEntry()
                    closeBottomSheet()
                }
            }
            true
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
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            closeBottomSheet()
        } else {
            openBottomSheet()
            navigationView.bringToFront()
        }
    }

    private fun pickCategory() {
        val intent = Intent(this, CategoriesEditActivity::class.java)
        intent.action = Intent.ACTION_PICK
        intent.putExtra(CategoriesEditActivity.SELECTED_CATEGORY_ID, model.entry.value?.category?.categoryID ?: -1)
        startActivityForResult(intent, CATEGORY_PICK_REQUEST_CODE, null)
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

        lastModified.text = entry.entryModifiedDate.getLocalizedDateTimeStamp(FormatStyle.MEDIUM)

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

        attachmentsContainer.visibility = if (attachments.isNullOrEmpty()) View.GONE else View.VISIBLE

        attachments?.let { attachments ->

            val outHSL = floatArrayOf(0f, 0f, 0f)
            ColorUtils.colorToHSL(model.entry.value!!.getColor(this), outHSL)
            outHSL[2] = outHSL[2] / 1.35f
            val cardColor = ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(outHSL), 201)

            for (attachment in attachments) {
                val view = LayoutInflater.from(this)
                    .inflate(R.layout.appunti_detail_attachment_item, attachmentsContainer, false) as CardView

                view.setCardBackgroundColor(cardColor)
                view.attachmentTitle.text = attachment.attachmentTitle
                view.tag = attachment

                Timber.v("$attachment")

                val finalPath = File(DatabaseHelper.getFilesDir(this), attachment.attachmentPath)

                attachment.loadThumbnail(this, view.attachmentImage)


//                if (attachment.attachmentMime?.startsWith("image", true) == true) {
//                    view.attachmentImage.visibility = View.VISIBLE
//
//                    Picasso.get()
//                        .load(finalPath)
//                        .into(view.attachmentImage)
//                } else if (attachment.attachmentMime?.startsWith("video", true) == true) {
//                    view.attachmentImage.visibility = View.VISIBLE
//
//                    rxSingle(Schedulers.io()) {
//                        ThumbnailUtils.createVideoThumbnail(
//                            finalPath.absolutePath,
//                            MediaStore.Images.Thumbnails.MINI_KIND
//                        )
//                    }.observeOn(AndroidSchedulers.mainThread())
//                        .subscribe { t1, t2 ->
//                            view.attachmentImage.setImageBitmap(t1)
//                        }
//
//                } else {
////                    view.attachmentImage.visibility = View.GONE
//                    view.attachmentImage.setImageResource(R.drawable.sharp_attach_file_24_rotated)
//                }

                view.attachmentImage.setOnClickListener {
                    try {
                        attachment.createViewIntent(this).also {
                            startActivity(it)
                        }
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                view.attachmentViewButton.setOnClickListener {
                    try {
                        attachment.createViewIntent(this).also {
                            startActivity(it)
                        }
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                view.attachmentShareButton.setOnClickListener {
                    try {
                        attachment.createShareIntent(this).also {
                            startActivity(Intent.createChooser(it, resources.getString(R.string.share)))
                        }
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                view.attachmentRemoveButton.setOnClickListener {
                    // delete attachment
                }

                attachmentsContainer.addView(view)
            }
        }
    }

    private fun applyEntryTheme(entry: Entry) {
        val color = entry.getColor(this)
        Timber.i("applyEntryTheme. color=${color.toString(16)}")

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

    private fun onShareEntry() {
        model.entry.value?.let { entry ->
            val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, entry.entryTitle)
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, entry.entryText)
            startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share)))
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

    companion object {
        const val KEY_ENTRY_ID = "entryID"
        const val KEY_REMOVE_ALARM = "removeAlarm"

        const val CATEGORY_PICK_REQUEST_CODE = 1

        const val READ_REQUEST_CODE = 2
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
