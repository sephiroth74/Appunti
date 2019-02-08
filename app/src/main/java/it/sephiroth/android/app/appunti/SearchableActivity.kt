package it.sephiroth.android.app.appunti

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import com.lapism.searchview.Search
import kotlinx.android.synthetic.main.searchable_activity.*
import timber.log.Timber

class SearchableActivity : AppuntiActivity() {
    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.searchable_activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                Timber.i("QUERY: $query")
            }
        }

        setupSearchView()
    }

    private fun setupSearchView() {
        searchView.findViewById<View>(R.id.search_searchEditText).requestFocus()
        searchView.setOnQueryTextListener(object : Search.OnQueryTextListener {
            override fun onQueryTextSubmit(query: CharSequence?): Boolean {
                Timber.i("onQueryTextSubmit")
                return true
            }

            override fun onQueryTextChange(newText: CharSequence?) {
                Timber.i("onQueryTextChange: $newText")
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

    }
}