package it.sephiroth.android.app.appunti

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.transition.Fade
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
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
import it.sephiroth.android.app.appunti.utils.IntentUtils
import it.sephiroth.android.app.appunti.widget.ItemEntryListAdapter
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.rxSingle
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

    private val answers: Answers by lazy { Answers.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.sharedElementEnterTransition = Fade()
        window.sharedElementExitTransition = Fade()
        window.sharedElementReenterTransition = Fade()
        window.sharedElementReturnTransition = null

        super.onCreate(savedInstanceState)

        setupSearchView()
        setupRecyclerView()

        val event = CustomEvent("search.init")
        intent?.action?.let { event.putCustomAttribute("action", it) }

        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query ->
                Timber.i("QUERY: $query")
                searchView.setText(query)
            }
        }

        answers.logCustom(event)

        // add focus to the search view
        searchView.findViewById<View>(R.id.search_searchEditText).requestFocus()
    }

    override fun onBackPressed() {
        Timber.i("onBackPressed")

        if (searchViewOpened) {
            searchView.close()
            return
        }

//        supportFinishAfterTransition()
        super.onBackPressed()
    }

    private fun searchEntries(text: String): Single<MutableList<Entry>> {
        Timber.i("searchEntries('$text')")
        return rxSingle(Schedulers.io()) {
            select().from(Entry::class)
                .where(Entry_Table.entryText.like("%$text%"))
                .or(Entry_Table.entryTitle.like("%$text%"))
                .orderByAll(
                    listOf(
                        OrderBy(Entry_Table.entryArchived.nameAlias, false),
                        OrderBy(Entry_Table.entryDeleted.nameAlias, false),
                        OrderBy(Entry_Table.entryPinned.nameAlias, false),
                        OrderBy(Entry_Table.entryPriority.nameAlias, false),
                        OrderBy(Entry_Table.entryModifiedDate.nameAlias, false)
                    )
                )
                .list
        }
    }

    private var timer: Disposable? = null

    private fun performSearch(text: String?) {
        Timber.i("performSearch('$text')")

        answers.logCustom(CustomEvent("search.performSearch"))

        timer?.dispose()
        timer = Observable.timer(300, TimeUnit.MILLISECONDS, Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Timber.v("isNotNullOrEmpty = ${text.isNotNullOrEmpty()}")
                if (text.isNotNullOrEmpty()) {
                    searchEntries(text!!)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { result, error ->
                            Timber.v("result = $result")
                            adapter.update(result, text.toLowerCase(Locale.getDefault()))
                        }
                } else {
                    adapter.update(arrayListOf())
                }
            }
    }


    private fun setupRecyclerView() {
        adapter = ItemEntryListAdapter(this, arrayListOf(), 0, 0) { holder, position -> false }

        layoutManager = itemsRecycler.layoutManager as StaggeredGridLayoutManager

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)

        adapter.itemClickListener = { holder ->
            if (holder.itemViewType == ItemEntryListAdapter.TYPE_ENTRY) {
                val entryItem = (holder as ItemEntryListAdapter.EntryViewHolder).entry
                if (entryItem != null) {
                    startDetailActivity(holder, entryItem)
                }
            }
        }
    }

    private fun startDetailActivity(holder: ItemEntryListAdapter.EntryViewHolder, entry: Entry) {
        val intent = IntentUtils.createViewEntryIntent(this, entry.entryID)

        val elementsArray = arrayListOf<Pair<View, String>>(
            Pair(holder.titleTextView, "itemTitle"),
            Pair(holder.contentTextView, "itemText")
        )

        if (entry.category != null) {
            elementsArray.add(Pair(holder.categoryTextView, "itemCategory"))
        }

        val intentOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(this, *(elementsArray.toTypedArray()))
        startDetailActivityFromIntent(intent, intentOptions)
    }

    private fun startDetailActivityFromIntent(intent: Intent, intentOptions: ActivityOptionsCompat?) {
        startActivity(intent, intentOptions?.toBundle())
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

        searchView.setOnLogoClickListener({ supportFinishAfterTransition() })
        // searchView.setOnLogoClickListener { supportFinishAfterTransition() }
    }
}