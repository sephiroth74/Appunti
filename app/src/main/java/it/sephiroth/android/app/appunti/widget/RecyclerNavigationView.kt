package it.sephiroth.android.app.appunti.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import kotlinx.android.synthetic.main.appunti_main_drawer_navigation_content.view.*
import timber.log.Timber

class RecyclerNavigationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NavigationView(context, attrs, defStyleAttr), LifecycleOwner {

    private val mLifecycleRegistry = LifecycleRegistry(this)
    private var adapter: NavigationItemsAdapter

    private var categorySelectedListener: ((Category?) -> Unit)? = null
    private var navigationItemSelectedListener: ((Int) -> Unit)? = null

    init {
        mLifecycleRegistry.markState(Lifecycle.State.INITIALIZED)
        adapter = NavigationItemsAdapter(context, mutableListOf())
    }

    fun setNavigationItemSelectedListener(action: (Int) -> Unit) {
        navigationItemSelectedListener = action
    }

    fun setNavigationCategorySelectedListener(action: (Category?) -> Unit) {
        categorySelectedListener = action
    }

    var model: MainViewModel? = null
        set(value) {
            field = value
            onModelChanged(value)
        }

    @Suppress("NAME_SHADOWING")
    private fun onModelChanged(model: MainViewModel?) {
        Timber.i("onModelChanged: $model")
        model?.let { model ->
            model.categories.observe(this, Observer {
                Timber.v("categories changed")
                adapter.values = it
                adapter.notifyDataSetChanged()
            })

            model.categoryChanged.observe(this, Observer {
                Timber.v("selected category changed")
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            })
        }
    }


    override fun onFinishInflate() {
        super.onFinishInflate()
        mLifecycleRegistry.markState(Lifecycle.State.CREATED)
        navigationRecycler.adapter = adapter
    }

    override fun getLifecycle(): Lifecycle {
        return mLifecycleRegistry
    }

    @SuppressLint("RestrictedApi")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mLifecycleRegistry.markState(Lifecycle.State.DESTROYED)
    }

    @SuppressLint("RestrictedApi")
    override fun onAttachedToWindow() {
        Timber.i("onAttachedToWindow")
        super.onAttachedToWindow()
        mLifecycleRegistry.markState(Lifecycle.State.RESUMED)
    }

    companion object {
        const val TYPE_LABEL_CATEGORY_HEADER = 0
        const val TYPE_LABEL_CATEGORY_ITEM = 1
        const val TYPE_SEPARATOR = 2
        const val TYPE_LABEL_CATEGORY_ARCHIVED = 3
        const val TYPE_LABEL_CATEGORY_DELETED = 4
        const val TYPE_LABEL_NEW_CATEGORY = 5
        const val TYPE_LABEL_EDIT_CATEGORY = 6
        const val TYPE_DISPLAY_TYPE = 7
        const val TYPE_SETTINGS = 8
    }

    inner class NavigationItemsAdapter(context: Context, var values: List<Category>) :
        RecyclerView.Adapter<ViewHolderBase>() {

        init {
            setHasStableIds(true)
        }

        private var layoutInflater: LayoutInflater = LayoutInflater.from(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderBase {
            if (viewType == TYPE_LABEL_CATEGORY_HEADER) {
                val view =
                    layoutInflater.inflate(R.layout.appunti_main_drawer_navigation_item_category_header, parent, false)
                return ViewHolderCategoryHeader(view)
            } else if (viewType == TYPE_SEPARATOR) {
                return ViewHolderBase(layoutInflater.inflate(R.layout.appunti_navigation_item_separator, parent, false))
            } else if (viewType == TYPE_LABEL_CATEGORY_ARCHIVED
                || viewType == TYPE_LABEL_CATEGORY_DELETED
                || viewType == TYPE_LABEL_NEW_CATEGORY
                || viewType == TYPE_LABEL_EDIT_CATEGORY
                || viewType == TYPE_SETTINGS
            ) {
                val view = layoutInflater.inflate(
                    R.layout.appunti_main_drawer_navigation_item_menu_with_drawable,
                    parent,
                    false
                )

                view.background = MaterialBackgroundUtils.navigationItemDrawable(context)
                view.setOnClickListener {
                    navigationItemSelectedListener?.invoke(viewType)
                }

                return ViewHolderSelectableCategory(view).also {
                    val text: Int
                    val drawableRes: Int

                    when (viewType) {
                        TYPE_LABEL_CATEGORY_ARCHIVED -> {
                            text = R.string.archived
                            drawableRes = R.drawable.outline_archive_24
                        }
                        TYPE_LABEL_NEW_CATEGORY -> {
                            text = R.string.add_new_category
                            drawableRes = R.drawable.sharp_playlist_add_24
                        }
                        TYPE_LABEL_EDIT_CATEGORY -> {
                            text = R.string.edit_categories
                            drawableRes = R.drawable.sharp_edit_24
                        }
                        TYPE_SETTINGS -> {
                            text = R.string.settings
                            drawableRes = R.drawable.sharp_settings_24
                        }
                        else -> {
                            text = R.string.deleted
                            drawableRes = R.drawable.sharp_delete_outline_24
                        }
                    }

                    val drawable = resources.getDrawable(drawableRes, context.theme).apply {
                        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                    }

                    it.textView.setText(text)
                    it.textView.setCompoundDrawablesRelative(drawable, null, null, null)
                }
            } else if (viewType == TYPE_DISPLAY_TYPE) {
                val view = layoutInflater.inflate(R.layout.appunti_main_drawer_navigation_item_switch, parent, false)
                return ViewHolderSwitch(view).also {
                    it.switchView.setText(R.string.display_as_grid)
                }
            } else {
                val view = layoutInflater.inflate(R.layout.appunti_main_drawer_navigation_item_checkable, parent, false)
                view.background = MaterialBackgroundUtils.navigationItemDrawable(context)
                return ViewHolderCategoryItem(view)
            }
        }

        override fun getItemCount(): Int {
            return values.size + 11
        }

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> TYPE_LABEL_CATEGORY_HEADER
                values.size + 2 -> TYPE_SEPARATOR
                values.size + 3 -> TYPE_LABEL_CATEGORY_ARCHIVED
                values.size + 4 -> TYPE_LABEL_CATEGORY_DELETED
                values.size + 5 -> TYPE_SEPARATOR
                values.size + 6 -> TYPE_LABEL_NEW_CATEGORY
                values.size + 7 -> TYPE_LABEL_EDIT_CATEGORY
                values.size + 8 -> TYPE_SEPARATOR
                values.size + 9 -> TYPE_DISPLAY_TYPE
                values.size + 10 -> TYPE_SETTINGS
                else -> TYPE_LABEL_CATEGORY_ITEM
            }
        }

        override fun getItemId(position: Int): Long {
            val type = getItemViewType(position)
            val itemId = when (type) {
                TYPE_LABEL_CATEGORY_HEADER -> -2
                TYPE_LABEL_CATEGORY_ARCHIVED -> -4
                TYPE_LABEL_CATEGORY_DELETED -> -5
                TYPE_LABEL_NEW_CATEGORY -> -6
                TYPE_LABEL_EDIT_CATEGORY -> -7
                TYPE_SETTINGS -> -8
                TYPE_DISPLAY_TYPE -> -9
                TYPE_SEPARATOR -> -10 - position.toLong()
                TYPE_LABEL_CATEGORY_ITEM -> getItem(position)?.categoryID ?: -1
                else -> -1
            }
            return itemId
        }

        private fun getItem(position: Int): Category? {
            if (position > 1 && position <= values.size + 1) return values[position - 2]
            return null
        }

        override fun onBindViewHolder(baseHolder: ViewHolderBase, position: Int) {
            when {
                baseHolder.itemViewType == TYPE_LABEL_CATEGORY_ITEM -> {
                    val holder = baseHolder as ViewHolderCategoryItem

                    val item = getItem(position)
                    holder.category = item

                    item?.let { category ->
                        holder.textView.text = category.categoryTitle
                        holder.textView.isChecked = model?.group?.getCategoryID() == category.categoryID
                    } ?: kotlin.run {
                        holder.textView.text = context.getString(R.string.categories_all)
                        holder.textView.isChecked = model?.let {
                            !it.group.isDeleted() && !it.group.isArchived() && it.group.getCategoryID() == null
                        } ?: false
                    }

                    holder.itemView.setOnClickListener {
                        categorySelectedListener?.invoke(item)
                    }
                }
                baseHolder.itemViewType == TYPE_LABEL_CATEGORY_ARCHIVED -> (baseHolder as ViewHolderSelectableCategory).textView.isChecked =
                    model?.group?.isArchived() ?: false
                baseHolder.itemViewType == TYPE_LABEL_CATEGORY_DELETED -> (baseHolder as ViewHolderSelectableCategory).textView.isChecked =
                    model?.group?.isDeleted() ?: false
                baseHolder.itemViewType == TYPE_DISPLAY_TYPE -> {
                    val holder = baseHolder as ViewHolderSwitch
                    holder.switchView.setOnCheckedChangeListener(null)
                    holder.switchView.isChecked = !SettingsManager.getInstance(context).displayAsList
                    holder.switchView.setOnCheckedChangeListener { _, isChecked ->
                        SettingsManager.getInstance(context).displayAsList = !isChecked
                    }
                }
            }
        }
    }

    class ViewHolderCategoryHeader(view: View) : ViewHolderBase(view)

    class ViewHolderCategoryItem(view: View) : ViewHolderSelectableCategory(view) {
        var category: Category? = null
    }

    open class ViewHolderSelectableCategory(view: View) : ViewHolderBase(view) {
        val textView = view as CheckedTextView
    }

    open class ViewHolderSwitch(view: View) : ViewHolderBase(view) {
        val switchView = view as SwitchCompat
    }

    open class ViewHolderBase(view: View) : RecyclerView.ViewHolder(view)
}