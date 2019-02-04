package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.dbflow5.structure.save
import com.lapism.searchview.Search
import com.lapism.searchview.Search.SPEECH_REQUEST_CODE
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.applyNoActionBarTheme
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.isLightTheme
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.utils.EntriesDiffCallback
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.main_item_list_content.view.*
import timber.log.Timber
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    lateinit var model: MainViewModel
    lateinit var adapter: ItemEntryListAdapter
    lateinit var layoutManager: StaggeredGridLayoutManager

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyNoActionBarTheme(toolbar) {
            setContentView(R.layout.main_activity)
        }

        model = ViewModelProviders.of(this).get(MainViewModel::class.java)

        adapter = ItemEntryListAdapter(this, arrayListOf())

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)
        layoutManager = itemsRecycler.layoutManager as StaggeredGridLayoutManager

        model.entries.observe(this, Observer {
            Timber.i("[${currentThread()}] entries changed")
            adapter.update(it)
        })

        setupSearchView()
        seupNavigationView()

        model.displayAsList.observe(this, Observer {
            Timber.i("displayAsList -> $it")
            if (it) {
                layoutManager.spanCount = resources.getInteger(R.integer.list_items_columns_list)
            } else {
                layoutManager.spanCount = resources.getInteger(R.integer.list_items_columns_grid)
            }

            bottomAppBar.setDisplayAsList(it)
        })

        bottomAppBar.doOnDisplayAsListChanged { value ->
            model.setDisplayAsList(value)
        }
    }

    private fun seupNavigationView() {
        navigationView.model = model
        navigationView.setNavigationCategorySelectedListener { category ->
            model.currentCategory = category
            closeDrawerIfOpened()
        }

        navigationView.setNavigationItemSelectedListener { id ->
            when (id) {
                R.id.newLabel -> startCategoriesEditActivity(true)
                R.id.editLabels -> startCategoriesEditActivity(false)
                R.id.settings -> {
                    startActivity(Intent(this, PreferencesActivity::class.java))
                }
            }
        }
    }

    private fun setupSearchView() {
        searchView.setOnMicClickListener {
            Search.setVoiceSearch(this, "")
        }

        searchView.setOnLogoClickListener { toggleDrawer() }
        searchView.setOnQueryTextListener(object : Search.OnQueryTextListener {

            var timer: Disposable? = null

            override fun onQueryTextSubmit(query: CharSequence?): Boolean {
                Timber.i("onQueryTextSubmit($query)")
                searchView.close()
                return true
            }

            override fun onQueryTextChange(newText: CharSequence?) {
                Timber.i("onQueryTextChange: $newText")

                timer?.dispose()
                timer = Observable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            adapter.filter(newText.toString())
                        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                if (data?.hasExtra(RecognizerIntent.EXTRA_RESULTS) == true) {
                    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    Timber.v("results: $results")
                    searchView.setText(results[0])
                }
            }
        }
    }


    private fun startCategoriesEditActivity(newCategory: Boolean = false) {
        val intent = Intent(this, CategoriesEditActivity::class.java)
        if (newCategory) intent.putExtra(CategoriesEditActivity.ASK_NEW_CATEGORY_STARTUP, true)
        startActivity(intent)
    }

    private fun closeDrawerIfOpened() {
        if (drawerLayout.isDrawerOpen(navigationView))
            drawerLayout.closeDrawer(navigationView)
    }

    private fun toggleDrawer() {
        if (!drawerLayout.isDrawerOpen(navigationView)) drawerLayout.openDrawer(navigationView)
        else drawerLayout.closeDrawer(navigationView)
    }

    inner class ItemEntryListAdapter(private val context: Context,
                                     private var values: MutableList<Item>) :
            RecyclerView.Adapter<ItemEntryListAdapter.ViewHolder>() {

        private var cardBackgroundColorDefault: ColorStateList? = null
        private var textColorDefault: ColorStateList? = null
        private var textColorInverse: ColorStateList? = null
        private val categoryColors = ResourceUtils.getCategoryColors(context)

        private var cardForegroundStroke: Drawable
        private var cardForegroundNoStroke: Drawable

        //        companion object {
        val TYPE_EMPTY = Item.ItemType.EMPTY.ordinal
        val TYPE_ENTRY = Item.ItemType.ENTRY.ordinal
        val TYPE_PINNED = Item.ItemType.PINNED.ordinal
        val TYPE_NON_PINNED = Item.ItemType.NON_PINNED.ordinal
//        }

        private var valuesCopy: MutableList<Item>

        init {
            valuesCopy = ArrayList(values)

            val isLightTheme = context.isLightTheme()
            textColorInverse =
                    context.theme.getColorStateList(context, if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary)

            cardForegroundNoStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_no_stroke)!!
            cardForegroundStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_with_stroke)!!

            setHasStableIds(true)
        }

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return item.type.ordinal
        }

        @SuppressLint("PrivateResource")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view: View

            if (viewType == TYPE_EMPTY) {
                view = LayoutInflater.from(context).inflate(R.layout.item_list_empty, parent, false)
                val searchViewHeight = context.resources.getDimensionPixelSize(R.dimen.search_height_view)
                val searchViewTopMargin = context.resources.getDimensionPixelSize(R.dimen.appunti_main_search_view_margin_top)

                val params =
                        StaggeredGridLayoutManager.LayoutParams(MATCH_PARENT, searchViewHeight + searchViewTopMargin * 2)
                params.isFullSpan = true
                view.layoutParams = params
            } else if (viewType == TYPE_PINNED) {
                view = LayoutInflater.from(context).inflate(R.layout.appunti_main_list_pinned_entry, parent, false)
                (view as TextView).text = getString(R.string.pinned)
            } else if (viewType == TYPE_NON_PINNED) {
                view = LayoutInflater.from(context).inflate(R.layout.appunti_main_list_pinned_entry, parent, false)
                (view as TextView).text = getString(R.string.others)
            } else {
                view = LayoutInflater.from(parent.context).inflate(R.layout.main_item_list_content, parent, false)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)

            Timber.i("entry: $item")

            if (holder.itemViewType == TYPE_ENTRY) {
                val entryItem = item.entry!!

                holder.titleTextView.text = entryItem.entryTitle
                holder.contentTextView.text = entryItem.entryText?.substring(0, 100)
                holder.categoryTextView.text = entryItem.category?.categoryTitle

                if (null == cardBackgroundColorDefault) {
                    cardBackgroundColorDefault = holder.cardView.cardBackgroundColor
                    textColorDefault = holder.titleTextView.textColors
                }

                var color = 0

                entryItem.category?.let {
                    if (it.categoryColorIndex != 0) {
                        color = categoryColors[it.categoryColorIndex]
                    }
                }

                if (color != 0) {
                    holder.cardView.setCardBackgroundColor(color)
                    holder.cardView.foreground = cardForegroundNoStroke.constantState?.newDrawable()
                    holder.categoryTextView.visibility = View.VISIBLE

                    // val luminance = ColorUtils.calculateLuminance(color)

                } else {
                    holder.cardView.setCardBackgroundColor(cardBackgroundColorDefault)
                    holder.cardView.foreground = cardForegroundStroke.constantState?.newDrawable()
                    holder.categoryTextView.visibility = View.GONE
                }

                with(holder.itemView) {
                    tag = entryItem
                    setOnClickListener {
                        Timber.d("card clicked!")

                        val newEntry = Entry(entryItem)
                        newEntry.entryPinned = if (newEntry.entryPinned == 1) 0 else 1
                        newEntry.save()
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return values.size
        }

        override fun getItemId(position: Int): Long {
            val item = getItem(position)
            return when (item.type) {
                Item.ItemType.ENTRY -> item.entry!!.entryID.toLong()
                Item.ItemType.EMPTY -> -1
                Item.ItemType.PINNED -> -2
                Item.ItemType.NON_PINNED -> -3
            }
        }

        private fun getItem(position: Int): Item {
            return values[position]
        }

        fun update(newData: List<Entry>?) {
            Timber.i("update: ${newData?.size}")

            val finalData = mutableListOf(Item(null, Item.ItemType.EMPTY))

            newData?.let { array ->
                finalData.addAll(array.map { Item(it, Item.ItemType.ENTRY) })
            }

            var firstNonPinned: Int
            val firstPinned: Int = finalData.indexOfFirst { it.entry?.entryPinned == 1 }

            if (firstPinned > -1) {
                finalData.add(firstPinned, Item(null, Item.ItemType.PINNED))
                firstNonPinned = finalData.indexOfFirst { it.entry?.entryPinned == 0 }
                if (firstNonPinned > -1) finalData.add(firstNonPinned, Item(null, Item.ItemType.NON_PINNED))
            }

            val callback = EntriesDiffCallback(values, finalData)
            val result = DiffUtil.calculateDiff(callback, true)

            Timber.v("diffResult = $result")

            values = finalData
            valuesCopy = ArrayList(finalData)
            result.dispatchUpdatesTo(this)
        }

        fun filter(text: String?) {
            Timber.i("filter($text)")
            if (text.isNullOrEmpty()) {
                values = ArrayList(valuesCopy)
            } else {
                val result = valuesCopy.filter { value ->
                    if (value.type == Item.ItemType.ENTRY) {
                        value.entry!!.entryTitle!!.toLowerCase().indexOf(text) > -1
                    } else {
                        true
                    }
                }
                values = ArrayList(result)
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView by lazy { view.id_title }
            val contentTextView: TextView by lazy { view.id_content }
            val categoryTextView: AppCompatTextView by lazy { view.chip }
            val cardView: CardView by lazy { view.id_card }
        }
    }

    class Item(val entry: Entry?, val type: ItemType) {
        enum class ItemType { ENTRY, EMPTY, PINNED, NON_PINNED }

        override fun equals(other: Any?): Boolean {
            if (other is Item) {
                if (other.type != type) return false
                return when (type) {
                    ItemType.ENTRY -> entry == other.entry
                    else -> true
                }
            }
            return false
        }
    }
}

