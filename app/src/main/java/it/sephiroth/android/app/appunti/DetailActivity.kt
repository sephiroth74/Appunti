package it.sephiroth.android.app.appunti

import android.os.Bundle
import android.transition.AutoTransition
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NavUtils
import com.dbflow5.query.result
import com.dbflow5.query.select
import com.dbflow5.structure.save
import it.sephiroth.android.app.appunti.db.DatabaseHelper
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.activity_detail.*
import android.content.Intent


@Suppress("NAME_SHADOWING")
class DetailActivity : AppuntiActivity() {
    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.activity_detail

    private lateinit var entry: Entry
    private lateinit var categoryColors: IntArray
    private var entryColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        window.sharedElementEnterTransition = AutoTransition()
        window.sharedElementExitTransition = AutoTransition()
        window.sharedElementReenterTransition = AutoTransition()
        window.sharedElementReturnTransition = AutoTransition()

        super.onCreate(savedInstanceState)

        categoryColors = ResourceUtils.getCategoryColors(this)
        entryColor = categoryColors[0]

//        EmojiCompat.init()

        val entryID = intent.getIntExtra("entryID", 0)

        select().from(Entry::class).where(Entry_Table.entryID.eq(entryID)).result?.let {
            entry = it
        } ?: run {
            entry = Entry()
        }

        entry.let { entry ->
            entry.category?.let { category ->
                entryColor = categoryColors[category.categoryColorIndex]
            }
            entryTitle.setText(entry.entryTitle)
            entryText.setText(entry.entryText)
        }

        window.decorView.setBackgroundColor(entryColor)
        window.navigationBarColor = entryColor

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onBackPressed() {
        NavUtils.navigateUpFromSameTask(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.appunti_detail_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        updateMenu(menu, entry)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.menu_action_pin -> {
                onTogglePin()
            }
            R.id.menu_action_delete -> {
                onToggleDelete()
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

    private fun onTogglePin() {
        DatabaseHelper.setEntryPinned(entry, entry.entryPinned == 0)
        invalidateOptionsMenu()
    }

    private fun onToggleDelete() {
        entry.entryDeleted = if (entry.entryDeleted == 1) 0 else 1
    }

    private fun onToggleArchive() {

    }

    private fun onShareEntry() {
        val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, entry.entryTitle)
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, entry.entryText)
        startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share)))
    }

    private fun updateMenu(menu: Menu?, entry: Entry) {
        menu?.let { menu ->
            with(menu.findItem(R.id.menu_action_pin)) {
                setIcon(
                        if (entry.entryPinned == 1)
                            R.drawable.appunti_sharp_favourite_24_checked_selector
                        else
                            R.drawable.appunti_sharp_favourite_24_unchecked_selector)

            }

            with(menu.findItem(R.id.menu_action_archive)) {
                setIcon(
                        if (entry.entryArchived == 1)
                            R.drawable.appunti_outline_archive_24_checked_selector
                        else R.drawable.appunti_outline_archive_24_unchecked_selector)
            }

            with(menu.findItem(R.id.menu_action_delete)) {
                setIcon(
                        if (entry.entryDeleted == 1)
                            R.drawable.appunti_sharp_restore_from_trash_24_selector
                        else R.drawable.appunti_sharp_delete_24_outline_selector)
            }

        }
    }
}
