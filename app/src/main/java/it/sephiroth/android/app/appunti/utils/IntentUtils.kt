package it.sephiroth.android.app.appunti.utils

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import it.sephiroth.android.app.appunti.CategoriesEditActivity
import it.sephiroth.android.app.appunti.DetailActivity
import it.sephiroth.android.app.appunti.PreferencesActivity
import it.sephiroth.android.app.appunti.SearchableActivity

object IntentUtils {

    const val KEY_ENTRY_ID = "entryID"
    const val KEY_QUERY = SearchManager.QUERY

    fun createNewEntryIntent(context: Context): Intent {
        return Intent(context, DetailActivity::class.java).apply {
            action = Intent.ACTION_CREATE_DOCUMENT
        }
    }

    fun createViewEntryIntent(context: Context, entryID: Int): Intent {
        return Intent(context, DetailActivity::class.java).apply {
            action = Intent.ACTION_EDIT
            putExtra(KEY_ENTRY_ID, entryID)
        }

    }

    fun createEditCategoriesIntent(context: Context): Intent {
        return Intent(context, CategoriesEditActivity::class.java)
//        if (newCategory) intent.putExtra(CategoriesEditActivity.ASK_NEW_CATEGORY_STARTUP, true)
    }

    fun createPerferencesIntent(context: Context): Intent {
        return Intent(context, PreferencesActivity::class.java)
    }

    fun createSearchableIntent(context: Context, query: String? = null): Intent {
        return Intent(context, SearchableActivity::class.java).apply {
            query?.let { query ->
                action = Intent.ACTION_SEARCH
                putExtra(KEY_QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

}