package it.sephiroth.android.app.appunti.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
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


    private var statusBarHeight: Int = 0

    init {
        Timber.i("init")
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
                updateSelectedItems()
            })
        }

    }

    private fun updateSelectedItems() {
        entriesDeleted.isChecked = model?.group?.isDeleted() ?: false
        entriesArchived.isChecked = model?.group?.isArchived() ?: false
    }

    override fun onFinishInflate() {
        Timber.i("onFinishInflate")
        super.onFinishInflate()
        mLifecycleRegistry.markState(Lifecycle.State.CREATED)
        navigationRecycler.adapter = adapter

        val drawable = MaterialBackgroundUtils.navigationItemDrawable(context)

        entriesArchived.background = drawable
        entriesDeleted.background =
            drawable.constantState?.newDrawable() ?: MaterialBackgroundUtils.navigationItemDrawable(context)
        newLabel.background =
            drawable.constantState?.newDrawable() ?: MaterialBackgroundUtils.navigationItemDrawable(context)
        editLabels.background =
            drawable.constantState?.newDrawable() ?: MaterialBackgroundUtils.navigationItemDrawable(context)
        settings.background =
            drawable.constantState?.newDrawable() ?: MaterialBackgroundUtils.navigationItemDrawable(context)

        displayAsList.isChecked = !SettingsManager.getInstance(context).displayAsList

        displayAsList.setOnCheckedChangeListener { buttonView, isChecked ->
            SettingsManager.getInstance(context).displayAsList = !isChecked
        }

        entriesArchived.setOnClickListener { navigationItemSelectedListener?.invoke(R.id.entriesArchived) }
        entriesDeleted.setOnClickListener { navigationItemSelectedListener?.invoke(R.id.entriesDeleted) }
        newLabel.setOnClickListener { navigationItemSelectedListener?.invoke(R.id.newLabel) }
        editLabels.setOnClickListener { navigationItemSelectedListener?.invoke(R.id.editLabels) }
        settings.setOnClickListener { navigationItemSelectedListener?.invoke(R.id.settings) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        Timber.i("onSizeChanged")
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun getLifecycle(): Lifecycle {
        return mLifecycleRegistry
    }

    @SuppressLint("RestrictedApi")
    override fun onDetachedFromWindow() {
        Timber.i("onDetachedFromWindow")
        super.onDetachedFromWindow()
        mLifecycleRegistry.markState(Lifecycle.State.DESTROYED)
    }

    @SuppressLint("RestrictedApi")
    override fun onAttachedToWindow() {
        Timber.i("onAttachedToWindow")
        super.onAttachedToWindow()
        mLifecycleRegistry.markState(Lifecycle.State.RESUMED)
    }

    inner class NavigationItemsAdapter(context: Context, var values: List<Category>) :
        RecyclerView.Adapter<NavigationItemsAdapter.ViewHolderBase>() {

        init {
            setHasStableIds(true)
        }

        private var layoutInflater: LayoutInflater = LayoutInflater.from(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationItemsAdapter.ViewHolderBase {
            val view = layoutInflater.inflate(R.layout.appunti_main_drawer_navigation_item_checkable, parent, false)
            view.background = MaterialBackgroundUtils.navigationItemDrawable(context)
            return ViewHolderBase(view)
        }


        override fun getItemCount(): Int {
            return values.size + 1
        }

        override fun getItemId(position: Int): Long {
            val item = getItem(position)
            item?.let { return it.categoryID.toLong() } ?: kotlin.run { return -1 }
        }

        private fun getItem(position: Int): Category? {
            if (position > 0) return values[position - 1]
            return null
        }

        override fun onBindViewHolder(holder: NavigationItemsAdapter.ViewHolderBase, position: Int) {
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

        inner class ViewHolderBase(view: View) : RecyclerView.ViewHolder(view) {
            val textView = view as CheckedTextView
            var category: Category? = null
        }

    }
}