package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.transition.AutoTransition
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NavUtils
import androidx.core.transition.doOnEnd
import androidx.core.transition.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.dbflow5.structure.save
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.disposables.Disposable
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.doOnTextChanged
import it.sephiroth.android.app.appunti.ext.hideSoftInput
import it.sephiroth.android.app.appunti.ext.rxTimer
import it.sephiroth.android.app.appunti.models.DetailViewModel
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.activity_detail.view.*
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import timber.log.Timber
import java.util.concurrent.TimeUnit


@Suppress("NAME_SHADOWING")
class DetailActivity : AppuntiActivity() {
    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_detail

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var model: DetailViewModel
    private var currentEntry: Entry? = null
    private var changeTimer: Disposable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        window.sharedElementEnterTransition = AutoTransition()
        window.sharedElementExitTransition = AutoTransition()
        window.sharedElementReenterTransition = AutoTransition()
        window.sharedElementReturnTransition = AutoTransition()
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        model = ViewModelProviders.of(this).get(DetailViewModel::class.java)
        model.entry.observe(this, Observer { entry ->
            Timber.i("model.entry changed = $entry")
            onEntryChanged(entry)
        })

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        closeBottomSheet()

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

        handleIntent(intent)

        entryCategory.setOnClickListener { pickCategory() }

        entryTitle.doOnTextChanged { s, start, count, after ->
            changeTimer = rxTimer(changeTimer, 300, TimeUnit.MILLISECONDS) {
                currentEntry?.entryTitle = s?.toString() ?: ""
                currentEntry?.save()
            }
        }

        entryText.doOnTextChanged { s, start, count, after ->
            changeTimer = rxTimer(changeTimer, 1, TimeUnit.SECONDS) {
                currentEntry?.entryText = s?.toString() ?: ""
                currentEntry?.save()
            }
        }

        entryText.movementMethod = LinkMovementMethod.getInstance()


        navigationView.setNavigationItemSelectedListener { menuItem ->
            Timber.i("menuItem: $menuItem")
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

        bottomAppBar.navigationIcon.setOnClickListener {
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

        // shared elements transitions adjustments
        val sharedElementEnterTransition = window.sharedElementEnterTransition

        sharedElementEnterTransition.doOnEnd {
            entryCategory.visibility = if (model.entry.value?.category == null) View.INVISIBLE else View.VISIBLE
        }
        sharedElementEnterTransition.doOnStart {
            entryCategory.visibility = if (model.entry.value?.category == null) View.INVISIBLE else View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CATEGORY_PICK_REQUEST) {
            if (resultCode == RESULT_OK) {
                data?.let { data ->
                    val categoryID = data.getIntExtra("categoryID", -1)
                    DatabaseHelper.getCategoryByID(categoryID)?.let { category ->
                        model.setEntryCategory(category)
                    }
                }
            }
        }


        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {
        // TODO: verify this
        NavUtils.navigateUpFromSameTask(this)
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
            R.id.menu_action_pin -> {
                onTogglePin()
            }
            R.id.menu_action_archive -> {
                onToggleArchive()
            }
            R.id.menu_action_share -> {
                onShareEntry()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleIntent(intent: Intent?) {
        Timber.i("handleIntent: $intent")
        var entryID = 0

        intent?.let { intent ->
            Timber.v("action=${intent.action}")
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    entryID = 0
                }

                Intent.ACTION_EDIT -> {
                    entryID = intent.getIntExtra("entryID", 0)
                }
            }
        }

        Timber.v("entryID=$entryID")
        model.entryID = entryID
    }

    private fun pickCategory() {
        val intent = Intent(this, CategoriesEditActivity::class.java)
        intent.action = Intent.ACTION_PICK
        intent.putExtra(CategoriesEditActivity.SELECTED_CATEGORY_ID, model.entry.value?.category?.categoryID ?: -1)
        startActivityForResult(intent, CATEGORY_PICK_REQUEST, null)
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

    companion object {
        const val CATEGORY_PICK_REQUEST = 1
    }


    private fun onEntryChanged(entry: Entry) {
        Timber.i("onEntryChanged()")

        val diff = EntryDiff.calculateDiff(currentEntry, entry)
        Timber.v("diff=$diff")

        currentEntry = Entry(entry)

        if (diff.titleChanged) entryTitle.setText(entry.entryTitle)
        if (diff.textChanged) entryText.setText(entry.entryText)

        if (diff.categoryChanged) {
            entryCategory.text = entry.category?.categoryTitle
            entryCategory.visibility = if (entry.category == null) View.INVISIBLE else View.VISIBLE
            applyEntryTheme(entry)
        }

        val time = entry.entryModifiedDate.atZone(ZoneId.systemDefault())
        lastModified.text = time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

        invalidateOptionsMenu()
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
            val drawable: Drawable? = (navigationView.background as LayerDrawable).findDrawableByLayerId(R.id.layer_background)
            drawable?.setTint(color)
        }
    }

    private fun onTogglePin() {
        val currentValue = model.entry.value?.isPinned()
        val result = model.togglePin()

        if (result) {
            Toast.makeText(this,
                    resources.getQuantityString(
                            if (currentValue == true)
                                R.plurals.entries_unpinned_title else R.plurals.entries_pinned_title, 1, 1),
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun onToggleDelete() {
        val currentValue = model.entry.value?.isDeleted()
        if (model.toggleDeleted()) {
            Toast.makeText(this,
                    resources.getQuantityString(
                            if (currentValue == true)
                                R.plurals.entries_restored_title else R.plurals.entries_deleted_title, 1, 1),
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun onToggleArchive() {
        val currentValue = model.entry.value?.isArchived()
        if (model.toggleArchived()) {
            Toast.makeText(this,
                    resources.getQuantityString(
                            if (currentValue == true)
                                R.plurals.entries_unarchived_title else R.plurals.entries_archived_title, 1, 1),
                    Toast.LENGTH_SHORT).show()
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
                                R.drawable.appunti_sharp_favourite_24_unchecked_selector)

                }

                menuItem = menu.findItem(R.id.menu_action_archive)
                menuItem?.apply {
                    setIcon(
                            if (entry.isArchived())
                                R.drawable.appunti_outline_archive_24_checked_selector
                            else R.drawable.appunti_outline_archive_24_unchecked_selector)

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
            }
        }
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
                var modifiedDateChanged: Boolean = true)

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
                    modifiedDateChanged = oldValue?.entryModifiedDate != newValue?.entryModifiedDate
            )

        }
    }
}
