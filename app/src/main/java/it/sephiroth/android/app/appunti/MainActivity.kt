package it.sephiroth.android.app.appunti

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import it.sephiroth.android.app.appunti.database.EntryWithCategory
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.isLightTheme
import it.sephiroth.android.app.appunti.models.EntryViewModel
import kotlinx.android.synthetic.main.item_list_content.view.*
import kotlinx.android.synthetic.main.main_activity.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    lateinit var model: EntryViewModel
    lateinit var adapter: ItemEntryListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        setSupportActionBar(bottomAppBar)

        adapter = ItemEntryListAdapter(this, arrayListOf(), false)
        model = ViewModelProviders.of(this).get(EntryViewModel::class.java)

        itemsRecycler.adapter = adapter
        itemsRecycler.setHasFixedSize(false)

        model.entries.observe(this, Observer {
            Timber.i("entries category changed")

            updateNavigationMenuCheckedItems()

            it.removeObservers(this)
            it.observe(this, Observer {
                Timber.i("entries changed")
                adapter.update(it)
            })
        })

//        fab.setOnClickListener {
//            ioThread {
//                AppDatabase.getInstance(this).entryDao().add(Entry("Title1", 1))
//            }
//        }


        searchView.setOnMicClickListener { }

        searchView.setOnLogoClickListener { toggleDrawer() }

        model.categories.observe(this@MainActivity, Observer {
            val menu = navigationView.menu
            val subMenu = menu.findItem(R.id.navigation_item_labels).subMenu

            subMenu.clear()

            subMenu.add(0, R.id.navigation_item_label_all, Menu.NONE, R.string.categories_all)
                    .setIcon(R.drawable.outline_label_24)
                    .setCheckable(true)
                    .isChecked = model.category.isNullOrEmpty()

            for (category in it) {
                subMenu.add(1, R.id.navigation_item_label_id, Menu.NONE, category.category_title)
                        .setIcon(R.drawable.outline_label_24)
                        .setCheckable(true)
                        .isChecked = model.category.equals(category.category_title)
            }
        })

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_item_label_all -> {
                    model.category = null
                    closeDrawerIfOpened()
                }
                R.id.navigation_item_label_id -> {
                    if (!menuItem.isChecked) {
                        model.category = menuItem.title.toString()
                    } else {
                        model.category = null
                    }
                    closeDrawerIfOpened()
                }
                else -> {
                }
            }
            true
        }

        model.displayAsGrid.observe(this, Observer {
            if (it) {
                (itemsRecycler.layoutManager as StaggeredGridLayoutManager).spanCount = 2
            } else {
                (itemsRecycler.layoutManager as StaggeredGridLayoutManager).spanCount = 1
            }

            bottomAppBar.menu.findItem(R.id.menu_show_as_list).isVisible = it
            bottomAppBar.menu.findItem(R.id.menu_show_as_grid).isVisible = !it
        })
    }

    private fun updateNavigationMenuCheckedItems() {
        val menu = navigationView.menu.findItem(R.id.navigation_item_labels).subMenu
        for (item in menu.children) {
            when (item.groupId) {
                0 -> item.isChecked = model.category.isNullOrEmpty()
                1 -> item.isChecked = model.category?.equals(item.title) ?: run { false }
            }
        }
    }

    private fun closeDrawerIfOpened() {
        if (drawerLayout.isDrawerOpen(navigationView))
            drawerLayout.closeDrawer(navigationView)
    }

    private fun toggleDrawer() {
        if (!drawerLayout.isDrawerOpen(navigationView)) drawerLayout.openDrawer(navigationView)
        else drawerLayout.closeDrawer(navigationView)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_show_as_list -> model.displayAsGrid.value = false
            R.id.menu_show_as_grid -> model.displayAsGrid.value = true
        }
        return super.onOptionsItemSelected(item)
    }

    class EntriesDiffCallback(var oldData: List<EntryWithCategory>,
                              var newData: List<EntryWithCategory>) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldData.get(oldItemPosition).entry.entry_uid == newData.get(newItemPosition).entry.entry_uid
        }

        override fun getOldListSize(): Int = oldData.size

        override fun getNewListSize(): Int = newData.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldData.get(oldItemPosition).equals(newData.get(newItemPosition))
        }

    }


    class ItemEntryListAdapter(private val context: Context,
                               val values: ArrayList<EntryWithCategory>,
                               private val twoPane: Boolean) :
            RecyclerView.Adapter<ItemEntryListAdapter.ViewHolder>() {

        private var cardBackgroundColorDefault: ColorStateList? = null
        private var textColorDefault: ColorStateList? = null
        private var textColorInverse: ColorStateList? = null

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

            cardForegroundNoStroke = context.getDrawable(R.drawable.material_selectable_item_background_no_stroke)!!
            cardForegroundStroke = context.getDrawable(R.drawable.material_selectable_item_background_with_stroke)!!

            setHasStableIds(true)
        }

        override fun getItemViewType(position: Int): Int {
            if (position == 0) return TYPE_EMPTY
            return TYPE_REGULAR
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view: View

            if (viewType == TYPE_EMPTY) {
                view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_empty, parent, false)
                val search_view_height = context.resources.getDimensionPixelSize(R.dimen.search_height_view)
                val search_view_margin = context.resources.getDimensionPixelSize(R.dimen.search_view_margin_top)

                val params =
                        StaggeredGridLayoutManager.LayoutParams(MATCH_PARENT, search_view_height + search_view_margin * 2)
                params.isFullSpan = true
                view.layoutParams = params
            } else {
                view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_content, parent, false)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            item?.let { entryItem ->
                holder.titleTextView.text = entryItem.entry.entry_title
                holder.contentTextView.text = entryItem.entry.entry_text.substring(0, 100) + "..."
                holder.categoryTextView.text = entryItem.category.category_title

                if (null == cardBackgroundColorDefault) {
                    cardBackgroundColorDefault = holder.cardView.cardBackgroundColor
                    textColorDefault = holder.titleTextView.textColors
                }

                var textColor: ColorStateList? = textColorDefault

                if (entryItem.category.category_color != 0) {

                    holder.cardView.setCardBackgroundColor(entryItem.category.category_color)
                    holder.cardView.foreground = cardForegroundNoStroke.constantState.newDrawable()
                    holder.categoryTextView.visibility = View.VISIBLE


                    val luminance = ColorUtils.calculateLuminance(entryItem.category.category_color)

                    if (luminance < 0.4) {
                        textColor = textColorInverse
                    }

                } else {
                    holder.cardView.setCardBackgroundColor(cardBackgroundColorDefault)
                    holder.cardView.foreground = cardForegroundStroke.constantState.newDrawable()
                    holder.categoryTextView.visibility = View.GONE
                }

                holder.titleTextView.setTextColor(textColor)

                with(holder.cardView) {
                    tag = entryItem
                    setOnClickListener {
                        Timber.d("card clicked!")
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            if (values.size > 0) return values.size + 1
            return 0
        }

        override fun getItemId(position: Int): Long {
            if (position == 0) return -1
            return values[position - 1].entry.entry_uid.toLong()
        }

        fun getItem(position: Int): EntryWithCategory? {
            if (position == 0) return null
            return values[position - 1]
        }

        fun update(newData: List<EntryWithCategory>?) {
            Timber.i("update: ${newData?.size}")

            newData?.let {
                val callback = EntriesDiffCallback(values, it)
                val result = DiffUtil.calculateDiff(callback, true)
                values.clear()
                values.addAll(it)
                result.dispatchUpdatesTo(this)
            } ?: run {
                values.clear()
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView by lazy { view.id_title }
            val contentTextView: TextView by lazy { view.id_content }
            val categoryTextView by lazy { view.chip }
            val cardView: CardView by lazy { view.id_card }
        }
    }
}
