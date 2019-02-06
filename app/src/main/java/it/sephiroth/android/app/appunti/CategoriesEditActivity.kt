package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
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
import it.sephiroth.android.app.appunti.ext.*
import it.sephiroth.android.app.appunti.graphics.CategoryColorDrawable
import it.sephiroth.android.app.appunti.utils.CategoriesDiffCallback
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import it.sephiroth.android.app.appunti.widget.HorizontalColorChooser
import kotlinx.android.synthetic.main.activity_categories.*
import kotlinx.android.synthetic.main.appunti_category_color_button_checkable.view.*
import kotlinx.android.synthetic.main.category_item_list_content.view.*
import timber.log.Timber


class CategoriesEditActivity : AppuntiActivity(), DirectModelNotifier.OnModelStateChangedListener<Category> {

    private lateinit var mAdapter: CategoriesAdapter
    private var mSnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        mAdapter = CategoriesAdapter(this, mutableListOf())
        categoriesRecycler.adapter = mAdapter

        newCategory.setOnClickListener { presentNewCategoryDialog() }
        updateCategories()

        if (intent.hasExtra(ASK_NEW_CATEGORY_STARTUP)) {
            mainThread {
                presentNewCategoryDialog()
            }
        }
    }

    override fun getToolbar(): Toolbar? = toolbar
    override fun getContentLayout(): Int = R.layout.activity_categories

    private fun presentNewCategoryDialog() {
        val alertDialog: AlertDialog = AlertDialog
            .Builder(this)
            .setCancelable(true)
            .setTitle(getString(R.string.category_title_dialog))
            .setView(R.layout.appunti_alertdialog_category_input)
            .create()

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { _, _ ->
            val title = alertDialog.findViewById<TextView>(android.R.id.text1)?.text.toString()
            val colorIndex = alertDialog.findViewById<HorizontalColorChooser>(R.id.colorChooser)?.selectedColorIndex
            createCategory(title, colorIndex ?: 0)
        }

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }

        alertDialog.setOnDismissListener {
            alertDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }

        alertDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        alertDialog.show()
    }

    private fun presentCategoryColorChooser(category: Category?) {
        /*
        if (null != category) {
            val copy = Category(category)
            val sheet = CategoryColorsBottomSheetDialogFragment()
            val arguments = Bundle()
            arguments.putInt(CategoryColorsBottomSheetDialogFragment.BUNDLE_KEY_COLOR_INDEX, copy.categoryColorIndex)
            sheet.arguments = arguments

            sheet.show(supportFragmentManager, "category_colors")
            sheet.actionListener = { value ->
                copy.categoryColorIndex = value
                copy.update()
                sheet.dismiss()
            }
        }*/

        if (null != category) {
            val alertDialog: AlertDialog = AlertDialog
                .Builder(this)
                .setCancelable(true)
                .setTitle(getString(R.string.category_title_dialog))
                .setView(R.layout.appunti_alertdialog_color_input)
                .create()

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { _, _ ->
                val title = alertDialog.findViewById<TextView>(android.R.id.text1)?.text.toString()
                val colorIndex = alertDialog.findViewById<HorizontalColorChooser>(R.id.colorChooser)?.selectedColorIndex
                createCategory(title, colorIndex ?: 0)
            }

            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }

            alertDialog.show()
        }
    }

    private fun deleteCategory(adapterPosition: Int, category: Category?) {
        if (null != category) {

            if (mSnackbar != null) {
                mSnackbar !!.dismiss()
                mSnackbar = null
            }

            mAdapter.trash(category)

            mSnackbar = Snackbar.make(constraintLayout, getString(R.string.category_deleted_snackbar_title), Snackbar.LENGTH_SHORT)
                .setAction(getString(R.string.undo_uppercase)) {}
                .setActionTextColor(theme.getColorStateList(this, R.attr.colorError))
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)

                        mSnackbar = null

                        if (event == DISMISS_EVENT_ACTION) {
                            Timber.d("must undo the event!")
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
        if (! name.isNullOrEmpty()) {
            val category = Category(name, colorIndex, Category.CategoryType.USER)
            category.insert()
        } else {
            Timber.w("must specify a name for the category")
        }
    }

    private fun updateCategory(item: Category, text: String?) {
        if (! text.isNullOrEmpty()) {
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

    companion object {
        const val ASK_NEW_CATEGORY_STARTUP = "ask_for_new_category_startup"
    }

    private inner class CategoriesAdapter(
            var context: CategoriesEditActivity,
            var values: MutableList<Category>) :
            RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

        private var categoryColors = ResourceUtils.getCategoryColors(context)
        private var currentEditText: EditText? = null
        private var deletedQueue = mutableListOf<Category>()
        private var originalValues = ArrayList(values)

        init {

        }

        internal fun trash(category: Category) {
            if (! deletedQueue.contains(category)) {
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
            val view = LayoutInflater.from(context).inflate(R.layout.category_item_list_content, parent, false)
            view.colorButton.allowUserToggle = false

            val holder = ViewHolder(view)
            holder.editTextView.imeOptions = EditorInfo.IME_ACTION_DONE
            return holder
        }

        fun update(newData: MutableList<Category>?) {
            Timber.i("update: ${newData?.size}")

            newData?.let {
                val filteredData = it.filter { item -> ! deletedQueue.contains(item) }

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
            holder.editTextView.setText(item.categoryTitle, TextView.BufferType.EDITABLE)

            val color = categoryColors[item.categoryColorIndex]
            var drawable: Drawable? = holder.colorButton.drawable

            if (null == drawable) {
                drawable = CategoryColorDrawable(context, color)
                holder.colorButton.setImageDrawable(drawable)
            }

            drawable.setColorFilter(color, PorterDuff.Mode.DST)

            holder.isEnabled = item.categoryType == Category.CategoryType.USER

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
                holder.editButton.isEnabled = ! hasFocus

                Timber.i("focus: $hasFocus")

                if (! hasFocus) {
                    val text = holder.editTextView.text.toString()
                    if (text.isEmpty()) holder.editTextView.setText(item.categoryTitle, TextView.BufferType.EDITABLE)
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

        }
    }
}
