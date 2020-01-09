package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.postDelayed
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.crashlytics.android.answers.CustomEvent
import com.dbflow5.query.OrderBy
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.ChangeAction
import com.dbflow5.structure.delete
import com.dbflow5.structure.insert
import com.dbflow5.structure.update
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.graphics.CategoryColorDrawable
import it.sephiroth.android.app.appunti.utils.CategoriesDiffCallback
import it.sephiroth.android.app.appunti.utils.IntentUtils
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import it.sephiroth.android.app.appunti.widget.GridLayoutColorChooser
import it.sephiroth.android.library.kotlin_extensions.content.res.getColorStateList
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.doOnMainThread
import it.sephiroth.android.library.kotlin_extensions.io.reactivex.rxSingle
import it.sephiroth.android.library.kotlin_extensions.view.hideSoftInput
import it.sephiroth.android.library.kotlin_extensions.view.showSoftInput
import kotlinx.android.synthetic.main.activity_categories.*
import kotlinx.android.synthetic.main.appunti_category_color_button_checkable.view.*
import kotlinx.android.synthetic.main.appunti_category_content_item.view.*
import timber.log.Timber


class CategoriesEditActivity : AppuntiActivity(), DirectModelNotifier.OnModelStateChangedListener<Category> {

    private lateinit var mAdapter: CategoriesAdapter
    private var mSnackbar: Snackbar? = null

    private var mPickCategory = false
    private var mPickCategorySelection: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val event = CustomEvent("categories.init")

        intent?.let { intent ->
            intent.action?.let { event.putCustomAttribute("action", it) }

            mPickCategory = intent.action == Intent.ACTION_PICK
            mPickCategorySelection = intent.getLongExtra(IntentUtils.KEY_CATEGORY_ID, -1)
        }

        answers.logCustom(event)

        mAdapter = CategoriesAdapter(this, mPickCategory, mPickCategorySelection, mutableListOf())

        if (mPickCategory) {
            mAdapter.categorySelectedListener = { category ->
                answers.logCustom(CustomEvent("categories.categorySelected"))
                setResultAndFinish(category)
            }
        }

        categoriesRecycler.adapter = mAdapter

        newCategory.setOnClickListener { presentNewCategoryDialog() }
        updateCategories()

        if (intent?.action == IntentUtils.ACTION_ASK_NEW_CATEGORY_STARTUP) {
            doOnMainThread {
                presentNewCategoryDialog()
            }
        }
    }

    override fun getToolbar(): Toolbar? = toolbar
    override fun getContentLayout(): Int = R.layout.activity_categories

    private fun setResultAndFinish(category: Category) {
        Timber.i("categorySelectedListener = $category")
        val newIntent = Intent(intent)
        newIntent.putExtra(IntentUtils.KEY_CATEGORY_ID, category.categoryID)
        setResult(Activity.RESULT_OK, newIntent)
        finish()
    }

    private fun presentNewCategoryDialog() {
        answers.logCustom(CustomEvent("categories.presentNewCategoryDialog"))
        val alertDialog: AlertDialog = AlertDialog
            .Builder(this)
            .setCancelable(true)
            .setTitle(getString(R.string.category_title_dialog))
            .setView(R.layout.appunti_alertdialog_category_input)
            .create().also { dialog ->

                dialog.setOnShowListener {
                    val textview = dialog.findViewById<TextView>(android.R.id.text1)
                    textview?.postDelayed(300) {
                        textview.requestFocus()
                        textview.showSoftInput()
                    }
                }

                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { _, _ ->
                    val title = dialog.findViewById<TextView>(android.R.id.text1)?.text.toString()
                    val colorIndex =
                        dialog.findViewById<GridLayoutColorChooser>(R.id.colorChooser)?.getSelectedColorIndex()
                    createCategory(title, colorIndex ?: 0)
                }

                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }

                dialog.setOnDismissListener {
                    Timber.v("dismiss")
                    dialog.findViewById<TextView>(android.R.id.text1)?.hideSoftInput()
                }
            }

        alertDialog.show()
    }

    private fun presentCategoryColorChooser(category: Category?) {
        answers.logCustom(CustomEvent("categories.presentCategoryColorChooser"))
        if (null != category) {
            val categoryCopy = Category(category)

            val alertDialog: AlertDialog = AlertDialog
                .Builder(this)
                .setCancelable(true)
                .setTitle(getString(R.string.pick_a_color))
                .setView(R.layout.appunti_alertdialog_color_input)
                .create()

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { dialog, _ ->
                val view = alertDialog.findViewById<GridLayoutColorChooser>(R.id.colorChooser)
                view?.let { view ->
                    val colorIndex = view.getSelectedColorIndex()
                    categoryCopy.categoryColorIndex = colorIndex
                    categoryCopy.update()
                }
                dialog.dismiss()
            }

            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }

            alertDialog.setOnShowListener { dialog ->
                val view = alertDialog.findViewById<GridLayoutColorChooser>(R.id.colorChooser)
                view?.let { view ->
                    view.setSelectedColorIndex(categoryCopy.categoryColorIndex)
                }
            }

            alertDialog.show()
        }
    }

    private fun deleteCategory(adapterPosition: Int, category: Category?) {
        answers.logCustom(CustomEvent("categories.deleteCategory"))
        if (null != category) {

            if (mSnackbar != null) {
                mSnackbar!!.dismiss()
                mSnackbar = null
            }

            mAdapter.trash(category)

            mSnackbar = Snackbar.make(
                coordinatorLayout,
                getString(R.string.category_deleted_snackbar_title),
                Snackbar.LENGTH_SHORT
            )
                .setAction(getString(R.string.undo_uppercase)) {}
                .setActionTextColor(theme.getColorStateList(this, R.attr.colorError))
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)

                        mSnackbar = null

                        if (event == DISMISS_EVENT_ACTION) {
                            Timber.d("must undo the event!")
                            answers.logCustom(CustomEvent("categories.undoDeleteCategory"))
                            mAdapter.restore(category)

                        } else {
                            Timber.d("must consolidate the event!")
                            category.delete()
                        }
                    }
                })

            mSnackbar?.show()
        }
    }

    private fun createCategory(name: String?, colorIndex: Int) {
        if (!name.isNullOrEmpty()) {
            val category = Category(name, colorIndex, Category.CategoryType.USER)
            category.insert()

            if (mPickCategory) {
                setResultAndFinish(category)
            }


        } else {
            Toast.makeText(this, "Invalid Category title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCategory(item: Category, text: String?) {
        if (!text.isNullOrEmpty()) {
            if (text != item.categoryTitle) {
                val copy = Category(item)
                copy.categoryTitle = text
                copy.update()
            }
        } else {
            Timber.w("must specify a name for the category")
        }
    }

    override fun onStart() {
        super.onStart()
        DirectModelNotifier.get().registerForModelStateChanges(Category::class.java, this)
    }

    override fun onStop() {
        super.onStop()
        DirectModelNotifier.get().unregisterForModelStateChanges(Category::class.java, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mAdapter.emptyTrash()
    }

    override fun onModelChanged(model: Category, action: ChangeAction) {
        Timber.i("onModelChanged($model, $action)")
        updateCategories()
    }

    @SuppressLint("CheckResult")
    private fun updateCategories() {
        rxSingle(Schedulers.io()) {
            select().from(Category::class).orderBy(OrderBy(Category_Table.categoryID.nameAlias, true)).list
        }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { result, _ ->
                mAdapter.update(result)
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class CategoriesAdapter(
        var context: CategoriesEditActivity,
        val pickCategory: Boolean,
        val selectedCategoryID: Long,
        var values: MutableList<Category>
    ) :
        RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

        private var categoryColors = ResourceUtils.getCategoryColors(context)
        private var currentEditText: EditText? = null
        private var deletedQueue = mutableListOf<Category>()
        private var originalValues = ArrayList(values)

        var categorySelectedListener: ((Category) -> (Unit))? = null

        init {

        }

        internal fun trash(category: Category) {
            if (!deletedQueue.contains(category)) {
                deletedQueue.add(category)
                update(originalValues)
            }
        }

        internal fun restore(category: Category) {
            deletedQueue.remove(category)
            originalValues.add(category)
            update(originalValues)
        }

        internal fun emptyTrash() {
            deletedQueue.clear()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.appunti_category_content_item, parent, false)
            view.colorButton.allowUserToggle = false

            if (pickCategory) {
                view.background = MaterialBackgroundUtils.categoryItemDrawable(context)
            }

            val holder = ViewHolder(view)
            holder.editTextView.imeOptions = EditorInfo.IME_ACTION_DONE
            return holder
        }

        fun update(newData: MutableList<Category>?) {
            Timber.i("update: ${newData?.size}")

            newData?.let {
                val filteredData = it.filter { item -> !deletedQueue.contains(item) }

                val callback = CategoriesDiffCallback(values, filteredData)
                val result = DiffUtil.calculateDiff(callback, true)
                values = filteredData.toMutableList()
                originalValues = ArrayList(values)
                result.dispatchUpdatesTo(this)
            } ?: run {
                values.clear()
                originalValues = ArrayList(values)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int {
            return values.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]

            holder.category = item


            val color = categoryColors[item.categoryColorIndex]
            var drawable: Drawable? = holder.colorButton.drawable

            if (null == drawable) {
                drawable = CategoryColorDrawable(context, color)
                holder.colorButton.setImageDrawable(drawable)
            }

            drawable.setColorFilter(color, PorterDuff.Mode.DST)

            holder.isEnabled = item.categoryType == Category.CategoryType.USER

            if (pickCategory) {
                holder.titleTextVew.text = item.categoryTitle
                holder.isSelected = item.categoryID == selectedCategoryID
                holder.editTextView.visibility = View.INVISIBLE
                holder.titleTextVew.visibility = View.VISIBLE
                holder.editButton.visibility = View.INVISIBLE
                holder.deleteButton.visibility = View.INVISIBLE
                holder.colorButton.isClickable = false
                holder.colorButton.isFocusable = false
                holder.colorButton.isFocusableInTouchMode = false

                holder.itemView.setOnClickListener {
                    Timber.i("item.setOnClickListener")
                    categorySelectedListener?.invoke(item)
                }
            } else {
                holder.editTextView.setText(item.categoryTitle, TextView.BufferType.EDITABLE)
                holder.editTextView.setOnEditorActionListener { _, actionId, _ ->
                    var result = false
                    when (actionId) {
                        EditorInfo.IME_ACTION_DONE -> {
                            removeFocusFromEditText()
                            updateCategory(item, holder.editTextView.text.toString())
                            result = true
                        }
                    }
                    result
                }

                holder.editTextView.setOnFocusChangeListener { _, hasFocus ->
                    holder.editButton.isEnabled = !hasFocus

                    Timber.i("focus: $hasFocus")

                    if (!hasFocus) {
                        val text = holder.editTextView.text.toString()
                        if (text.isEmpty()) holder.editTextView.setText(
                            item.categoryTitle,
                            TextView.BufferType.EDITABLE
                        )
                        else {
                            updateCategory(item, text)
                        }
                    } else {
                        currentEditText = holder.editTextView
                        holder.editTextView.showSoftInput()
                    }
                }

                holder.colorButton.setOnClickListener {
                    removeFocusFromEditText()
                    presentCategoryColorChooser(holder.category)
                }

                holder.deleteButton.setOnClickListener {
                    removeFocusFromEditText()
                    deleteCategory(holder.adapterPosition, holder.category)
                }

                holder.editButton.setOnClickListener {
                    holder.editTextView.requestFocus()
                    val selection = holder.editTextView.text?.length ?: 0
                    holder.editTextView.setSelection(selection)
                }
            }
        }

        private fun removeFocusFromEditText() {
            currentEditText?.clearFocus()
            currentEditText?.hideSoftInput()
            currentEditText = null
        }


        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var category: Category? = null
            val editTextView = view.editText
            val colorButton = view.colorButton
            val editButton = view.editButton
            val deleteButton = view.deleteButton
            val titleTextVew = view.titleText
            val frameLayout = view.frameLayout

            var isEnabled: Boolean
                get() = itemView.isEnabled
                set(value) {
                    itemView.isEnabled = value
                    editTextView.isEnabled = value
                    colorButton.isEnabled = value

                    if (value) {
                        editButton.visibility = View.VISIBLE
                        deleteButton.visibility = View.VISIBLE
                    } else {
                        editButton.visibility = View.INVISIBLE
                        deleteButton.visibility = View.INVISIBLE
                    }

                }

            var isSelected: Boolean
                set(value) {
                    itemView.isSelected = value
                }
                get() = itemView.isSelected

        }
    }
}
