package it.sephiroth.android.app.appunti

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.dbflow5.isNotNullOrEmpty
import com.dbflow5.query.OrderBy
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.lapism.searchview.Search
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.db.tables.Entry_Table
import it.sephiroth.android.app.appunti.ext.rxSingle
import it.sephiroth.android.app.appunti.widget.ItemEntryListAdapter
import kotlinx.android.synthetic.main.appunti_entries_recycler_view.*
import kotlinx.android.synthetic.main.appunti_search_view_toolbar_arrow_only.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class SearchableActivity : AppuntiActivity() {
    private lateinit var adapter: ItemEntryListAdapter
    private lateinit var layoutManager: StaggeredGridLayoutManager
    private var searchViewOpened = false

    override fun getToolbar(): Toolbar? = toolbar

    override fun getContentLayout(): Int = R.layout.searchable_activity


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSearchView()
        setupRecyclerView()

        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                Timber.i("QUERY: $query")
                searchView.setText(query)
            }
        }

        // add focus to the search view
        searchView.findViewById<View>(R.id.search_searchEditText).requestFocus()
    }

    override fun onBackPressed() {
        Timber.i("onBackPressed")

        if (searchViewOpened) {
            searchView.close()
            return
        }

        supportFinishAfterTransition()
//        super.onBackPressed()
    }

    private fun searchEntries(text: String): Single<MutableList<Entry>> {
        return rxSingle(Schedulers.io()) {
            select().from(Entry::class)
                    .where(Entry_Table.entryText.like("%$text%"))
                    .or(Entry_Table.entryTitle.like("%$text%"))
                    .orderByAll(listOf(
                            OrderBy(Entry_Table.entryArchived.nameAlias, false),
                            OrderBy(Entry_Table.entryDeleted.nameAlias, false),
                            OrderBy(Entry_Table.entryPinned.nameAlias, false),
                            OrderBy(Entry_Table.entryPriority.nameAlias, false),
                            OrderBy(Entry_Table.entryModifiedDate.nameAlias, false)))
                    .list
        }
    }

    private var timer: Disposable? = null

    private fun performSearch(text: String?) {

        timer?.dispose()
        timer = Observable.timer(300, TimeUnit.MILLISECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {

                    if (text.isNotNullOrEmpty()) {
                        searchEntries(text!!)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { result, error ->
                                    adapter.update(result, text.toLowerCase(Locale.getDefault()))
                                }
                    } else {
                        adapter.update(arrayListOf())
                    }
                }
    }


    private fun setupRecyclerView() {
        adapter = ItemEntryListAdapter(this, arrayListOf()) { holder, position -> false }

        layoutManager = itemsRecycler.layoutManager as StaggeredGridLayoutManager

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : Search.OnQueryTextListener {
            override fun onQueryTextSubmit(query: CharSequence?): Boolean {
                searchView.close()
                return false
            }

            override fun onQueryTextChange(newText: CharSequence?) {
                Timber.i("onQueryTextChange: $newText")
                performSearch(newText.toString())
            }

        })

        searchView.setOnOpenCloseListener(object : Search.OnOpenCloseListener {
            override fun onOpen() {
                Timber.i("onOpen")
                searchViewOpened = true
            }

            override fun onClose() {
                Timber.i("onClose")
                searchViewOpened = false
            }
        })

        searchView.setOnLogoClickListener {
            supportFinishAfterTransition()
        }

    }
}