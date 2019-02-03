package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
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
import com.lapism.searchview.Search
import it.sephiroth.android.app.appunti.db.tables.Entry
import it.sephiroth.android.app.appunti.ext.currentThread
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.isAPI
import it.sephiroth.android.app.appunti.ext.isLightTheme
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.main_item_list_content.view.*
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    lateinit var model: MainViewModel
    lateinit var adapter: ItemEntryListAdapter
    lateinit var layoutManager: StaggeredGridLayoutManager

    private var lightTheme = false

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lightTheme = SettingsManager.getInstance(this).isLightTheme
        setTheme(if (lightTheme) R.style.Theme_Appunti_Light_NoActionbar else R.style.Theme_Appunti_Dark_NoActionbar)

        setContentView(R.layout.main_activity)
        setSupportActionBar(toolbar)

        if (lightTheme && isAPI(26)) {
            toolbar.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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

        searchView.setOnMicClickListener {
            Search.setVoiceSearch(this, "")
        }
        searchView.setOnLogoClickListener { toggleDrawer() }

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
                }
            }
        }

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

    class EntriesDiffCallback(private var oldData: List<Entry?>,
                              private var newData: List<Entry?>) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldData.size
        override fun getNewListSize(): Int = newData.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldData[oldItemPosition]?.entryID == newData[newItemPosition]?.entryID
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldData[oldItemPosition]
            val newItem = newData[newItemPosition]
            return oldItem == newItem
        }
    }


    class ItemEntryListAdapter(private val context: Context,
                               private var values: MutableList<Entry?>) :
            RecyclerView.Adapter<ItemEntryListAdapter.ViewHolder>() {

        private var cardBackgroundColorDefault: ColorStateList? = null
        private var textColorDefault: ColorStateList? = null
        private var textColorInverse: ColorStateList? = null
        private val categoryColors = ResourceUtils.getCategoryColors(context)

        private var cardForegroundStroke: Drawable
        private var cardForegroundNoStroke: Drawable

        companion object {
            const val TYPE_EMPTY = 0
            const val TYPE_REGULAR = 1
        }

        init {
            val isLightTheme = context.isLightTheme()
            textColorInverse =
                    context.theme.getColorStateList(context, if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary)

            cardForegroundNoStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_no_stroke)!!
            cardForegroundStroke = context.getDrawable(R.drawable.appunti_card_selectable_item_background_with_stroke)!!

            setHasStableIds(true)
        }

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            if (null != item) return TYPE_REGULAR
            return TYPE_EMPTY
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
            } else {
                view = LayoutInflater.from(parent.context).inflate(R.layout.main_item_list_content, parent, false)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            Timber.v("onBindViewHolder, position=$position")
            val item = getItem(position)
            item?.let { entryItem ->
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

                with(holder.cardView) {
                    tag = entryItem
                    setOnClickListener {
                        Timber.d("card clicked!")
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return values.size
        }

        override fun getItemId(position: Int): Long {
            val item = getItem(position)
            return item?.entryID?.toLong() ?: -1L
        }

        private fun getItem(position: Int): Entry? {
            return values[position]
        }

        fun update(newData: List<Entry>?) {
            Timber.i("update: ${newData?.size}")

            val finalData = mutableListOf<Entry?>(null)
            newData?.let {
                finalData.addAll(it)
            }

            val callback = EntriesDiffCallback(values, finalData)
            val result = DiffUtil.calculateDiff(callback, true)

            Timber.v("diffResult = $result")

            values = finalData
            result.dispatchUpdatesTo(this)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView by lazy { view.id_title }
            val contentTextView: TextView by lazy { view.id_content }
            val categoryTextView: AppCompatTextView by lazy { view.chip }
            val cardView: CardView by lazy { view.id_card }
        }
    }

}

