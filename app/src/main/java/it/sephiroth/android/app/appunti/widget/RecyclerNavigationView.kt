@file:Suppress("NAME_SHADOWING")

package it.sephiroth.android.app.appunti.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
import com.google.android.material.navigation.NavigationView
import isTablet
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.views.EntryWithCategory
import it.sephiroth.android.app.appunti.models.MainViewModel
import it.sephiroth.android.app.appunti.models.SettingsManager
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import it.sephiroth.android.app.appunti.view.CheckableLinearLayout
import it.sephiroth.android.library.kotlin_extensions.content.res.getColor
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
            model.categoriesWithEntries.observe(this, Observer {
                Timber.v("categoriesWithEntries changed")
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

    inner class NavigationItemsAdapter(context: Context, var values: List<EntryWithCategory>) :
        RecyclerView.Adapter<ViewHolderBase>() {
        val isTablet = context.resources.isTablet

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
            } else if (viewType == TYPE_LABEL_NEW_CATEGORY
                || viewType == TYPE_LABEL_EDIT_CATEGORY
                || viewType == TYPE_SETTINGS
            ) {
                val view = layoutInflater.inflate(
                    R.layout.appunti_main_drawer_navigation_item_menu_with_drawable,
                    parent,
                    false
                )

                view.setOnClickListener {
                    navigationItemSelectedListener?.invoke(viewType)
                }

                return ViewHolderCheckable(view).also {
                    val text: Int
                    val drawableRes: Int

                    when (viewType) {
                        TYPE_LABEL_NEW_CATEGORY -> {
                            text = R.string.add_new_category
                            drawableRes = R.drawable.sharp_playlist_add_24
                        }
                        TYPE_LABEL_EDIT_CATEGORY -> {
                            text = R.string.edit_categories
                            drawableRes = R.drawable.sharp_edit_24
                        }
                        else -> {
                            text = R.string.settings
                            drawableRes = R.drawable.sharp_settings_24
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
            } else if (viewType == TYPE_LABEL_CATEGORY_ARCHIVED || viewType == TYPE_LABEL_CATEGORY_DELETED) {
                val view = layoutInflater.inflate(R.layout.appunti_main_drawer_navigation_item_checkable, parent, false)
                    .apply {
                        setOnClickListener {
                            navigationItemSelectedListener?.invoke(viewType)
                        }
                    }
                return ViewHolderCheckableWithBadge(view).apply {
                    val text: Int
                    val drawableRes: Int

                    when (viewType) {
                        TYPE_LABEL_CATEGORY_ARCHIVED -> {
                            text = R.string.archived
                            drawableRes = R.drawable.outline_archive_24
                        }
                        else -> {
                            text = R.string.deleted
                            drawableRes = R.drawable.sharp_delete_outline_24
                        }
                    }

                    val drawable = resources.getDrawable(drawableRes, context.theme).apply {
                        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                    }

                    textView.setText(text)
                    textView.setCompoundDrawablesRelative(drawable, null, null, null)
                }
            } else {
                val view = layoutInflater.inflate(R.layout.appunti_main_drawer_navigation_item_checkable, parent, false)
                return ViewHolderCategoryItem(view)
            }
        }

        override fun getItemCount(): Int {
            return values.size + if (isTablet) 10 else 11
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
                values.size + 9 -> if (isTablet) TYPE_SETTINGS else TYPE_DISPLAY_TYPE
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

        private fun getItem(position: Int): EntryWithCategory? {
            if (position > 1 && position <= values.size + 1) return values[position - 2]
            return null
        }

        override fun onBindViewHolder(baseHolder: ViewHolderBase, position: Int) {
            when {
                baseHolder.itemViewType == TYPE_LABEL_CATEGORY_ITEM -> {
                    val holder = baseHolder as ViewHolderCategoryItem

                    val item = getItem(position)

                    holder.category = item
                    holder.isChecked = item?.let { category ->
                        model?.group?.getCategoryID() == category.categoryID
                    } ?: kotlin.run {
                        model?.let {
                            !it.group.isDeleted() && !it.group.isArchived() && it.group.getCategoryID() == null
                        } ?: false
                    }

                    holder.itemView.setOnClickListener {
                        categorySelectedListener?.invoke(item?.category())
                    }

                }

                baseHolder.itemViewType == TYPE_LABEL_CATEGORY_ARCHIVED -> {
                    (baseHolder as ViewHolderCheckableWithBadge).apply {
                        this.isChecked = model?.group?.isArchived() ?: false
                        this.setCount(model?.entriesArchivedCount)
                    }
                }

                baseHolder.itemViewType == TYPE_LABEL_CATEGORY_DELETED -> {
                    (baseHolder as ViewHolderCheckableWithBadge).apply {
                        this.isChecked = model?.group?.isDeleted() ?: false
                        this.setCount(model?.entriesDeletedCount)
                    }
                }

                baseHolder.itemViewType == TYPE_DISPLAY_TYPE -> {
                    val holder = baseHolder as ViewHolderSwitch
                    holder.switchView.setOnCheckedChangeListener(null)
                    holder.isChecked = !SettingsManager.getInstance(context).displayAsList
                    holder.switchView.setOnCheckedChangeListener { _, isChecked ->

                        Answers
                            .getInstance()
                            .logCustom(
                                CustomEvent("main.setDisplayAsList")
                                    .putCustomAttribute("value", if (isChecked) 1 else 0)
                            )

                        SettingsManager.getInstance(context).displayAsList = !isChecked
                    }
                }
            }
        }
    }

    class ViewHolderCategoryItem(view: View) : ViewHolderCheckableWithBadge(view) {
        private val colorStateListCache = hashMapOf<Int, ColorStateList>()

        private fun getColorStateList(context: Context, categoryColorIndex: Int?): ColorStateList {
            categoryColorIndex?.let { categoryColorIndex ->
                return if (colorStateListCache.containsKey(categoryColorIndex)) {
                    colorStateListCache[categoryColorIndex]!!
                } else {
                    colorStateListCache[categoryColorIndex] =
                        ColorStateList.valueOf(Category.getColor(context, categoryColorIndex))
                    colorStateListCache[categoryColorIndex]!!
                }
            } ?: run {
                return accentColorStateList
            }
        }

        var category: EntryWithCategory? = null
            set(value) {
                if (field != value) {
                    field = value
                    value?.let { category ->
                        textView.text = category.categoryTitle
                        setCount(category.entriesCount.toLong())

                    } ?: run {
                        textView.text = context.getString(R.string.categories_all)
                        setCount(0)
                    }
                }
            }

        override var isChecked: Boolean
            get() = checkableItemView.isChecked
            set(value) {
                checkableItemView.isChecked = value

                if (value) {
                    val colorStateList = getColorStateList(context, category?.categoryColorIndex)
                    setTintAndColor(colorStateList)
                } else {
                    setTintAndColor(null)
                }
            }

        init {
            itemView.backgroundTintList = accentColorStateList
            textView.text = context.getString(R.string.categories_all)
        }
    }

    open class ViewHolderCheckableWithBadge(view: View) : ViewHolderSelectableItem(view), ViewHolderCheckableBase {
        val numEntriesText = view.findViewById<TextView>(android.R.id.text2)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        val checkableItemView = itemView as CheckableLinearLayout
        val textColors = textView.textColors

        override var isChecked: Boolean
            get() = checkableItemView.isChecked
            set(value) {
                checkableItemView.isChecked = value
                if (value) {
                    setTintAndColor(accentColorStateList)
                } else {
                    setTintAndColor(null)
                }
            }

        protected fun setTintAndColor(colorStateList: ColorStateList?) {
            colorStateList?.let { colorStateList ->

                itemView.backgroundTintList = colorStateList
                setTextCompoundDrawablesColorFilter(
                    PorterDuffColorFilter(
                        colorStateList.defaultColor,
                        PorterDuff.Mode.SRC_IN
                    )
                )
                textView.setTextColor(colorStateList)
                numEntriesText.setTextColor(colorStateList)
            } ?: run {
                itemView.backgroundTintList = null
                setTextCompoundDrawablesColorFilter(null)
                textView.setTextColor(textColors)
                numEntriesText.setTextColor(textColors)
            }
        }

        private fun setTextCompoundDrawablesColorFilter(colorFilter: PorterDuffColorFilter?) {
            textView.compoundDrawables.filter { it != null }.forEach { it.colorFilter = colorFilter }
        }

        fun setCount(value: Long?) {
            numEntriesText.text = value?.let {
                if (it > 0) it.toString() else ""
            } ?: run {
                ""
            }
        }

        init {
            itemView.backgroundTintList = accentColorStateList
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    open class ViewHolderCheckable(view: View) : ViewHolderSelectableItem(view), ViewHolderCheckableBase {
        val textView = view as CheckedTextView

        override var isChecked: Boolean
            get() = textView.isChecked
            set(value) = textView.setChecked(value)
    }

    @Suppress("UsePropertyAccessSyntax")
    class ViewHolderSwitch(view: View) : ViewHolderBase(view), ViewHolderCheckableBase {
        val switchView = view as SwitchCompat

        override var isChecked: Boolean
            get() = switchView.isChecked
            set(value) = switchView.setChecked(value)
    }

    class ViewHolderCategoryHeader(view: View) : ViewHolderBase(view)


    abstract class ViewHolderSelectableItem(view: View) : ViewHolderBase(view) {
        init {
            itemView.background = MaterialBackgroundUtils.navigationItemDrawable(context)
        }
    }

    open class ViewHolderBase(view: View) : RecyclerView.ViewHolder(view) {
        val context: Context by lazy { itemView.context }

        val accentColorStateList: ColorStateList by lazy {
            ColorStateList.valueOf(context.theme.getColor(context, R.attr.colorAccent))
        }
    }

    interface ViewHolderCheckableBase {
        var isChecked: Boolean
    }
}