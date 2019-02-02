package it.sephiroth.android.app.appunti

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.dbflow5.query.OrderBy
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.runtime.DirectModelNotifier
import com.dbflow5.structure.*
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.mainThread
import it.sephiroth.android.app.appunti.ext.rxSingle
import it.sephiroth.android.app.appunti.graphics.CircularSolidDrawable
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.activity_categories.*
import kotlinx.android.synthetic.main.appunti_category_color_button.view.*
import kotlinx.android.synthetic.main.category_item_list_content.view.*
import timber.log.Timber


class CategoriesEditActivity : AppCompatActivity(), DirectModelNotifier.OnModelStateChangedListener<Category> {

    private lateinit var adapter: CategoriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        adapter = CategoriesAdapter(this, mutableListOf())
        categoriesRecycler.adapter = adapter

        newCategory.setOnClickListener { presentNewCategoryDialog() }
        updateCategories()

        if (intent.hasExtra(ASK_NEW_CATEGORY_STARTUP)) {
            mainThread {
                presentNewCategoryDialog()
            }
        }
    }

    private fun presentNewCategoryDialog() {
        val dialog: AlertDialog = AlertDialog
                .Builder(this)
                .setCancelable(true)
                .setTitle(getString(R.string.category_title_dialog))
                .setView(R.layout.appunti_alertdialog_category_input)
                .create()

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)) { _, which ->
            createCategory(dialog.findViewById<TextView>(android.R.id.text1)?.text.toString())
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun presentCategoryColorChooser(category: Category?) {
        if (null != category) {
            val sheet = CategoryColorsBottomSheetDialogFragment()
            sheet.show(supportFragmentManager, "category_colors")
            sheet.actionListener = { value ->
                category.categoryColorIndex = value
                category.save()
                sheet.dismiss()
            }
        }
    }

    private fun deleteCategory(adapterPosition: Int, category: Category?) {
        if (null != category) {
            adapter.values.remove(category)
            adapter.notifyItemRemoved(adapterPosition)

            Snackbar.make(constraintLayout, getString(R.string.category_deleted_snackbar_title), Snackbar.LENGTH_SHORT)
                    .setAction(getString(R.string.undo_uppercase)) {}
                    .setActionTextColor(theme.getColorStateList(this, R.attr.colorError))
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)

                            if (event == DISMISS_EVENT_ACTION) {
                                Timber.d("must undo the event!")
                                adapter.values.add(adapterPosition, category)
                                adapter.notifyItemInserted(adapterPosition)

                            } else {
                                Timber.d("must consolidate the event!")
                                category.delete()
                            }
                        }
                    })
                    .show()
        }
    }

    private fun createCategory(name: String?) {
        if (!name.isNullOrEmpty()) {
            val category = Category()
            category.categoryTitle = name
            category.categoryColorIndex = 0
            category.categoryType = Category.CategoryType.USER
            category.insert()
        } else {
            Timber.w("must specify a name for the category")
        }
    }

    private fun updateCategory(item: Category, text: String?) {
        if (!text.isNullOrEmpty()) {
            if (text != item.categoryTitle) {
                item.categoryTitle = text
                item.update()
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

    override fun onModelChanged(model: Category, action: ChangeAction) {
        Timber.i("onModelChanged($model, $action)")
        updateCategories()
    }

    @SuppressLint("CheckResult")
    private fun updateCategories() {
        fetchCategories().observeOn(AndroidSchedulers.mainThread()).subscribe { result, error ->
            adapter.values = result
            adapter.notifyDataSetChanged()
        }
    }

    private fun fetchCategories(): Single<MutableList<Category>> {
        return rxSingle(Schedulers.io()) {
            select().from(Category::class).orderBy(OrderBy(Category_Table.categoryID.nameAlias, true)).list
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

    private inner class CategoriesAdapter(private var context: CategoriesEditActivity, var values: MutableList<Category>) :
            RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

        private var categoryColors = ResourceUtils.getCategoryColors(context)
        private var currentEditText: EditText? = null
        private val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.category_item_list_content, parent, false)
            val holder = ViewHolder(view)
            holder.editTextView.imeOptions = EditorInfo.IME_ACTION_DONE
            return holder
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
                drawable = CircularSolidDrawable(context, color)
                holder.colorButton.setImageDrawable(drawable)
            }

            drawable.setColorFilter(color, PorterDuff.Mode.DST)

            holder.isEnabled = item.categoryType == Category.CategoryType.USER

            holder.editTextView.setOnEditorActionListener { v, actionId, event ->
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

            holder.editTextView.setOnFocusChangeListener { v, hasFocus ->
                holder.editButton.isEnabled = !hasFocus

                Timber.i("focus: $hasFocus")

                if (!hasFocus) {
                    val text = holder.editTextView.text.toString()
                    if (text.isEmpty()) holder.editTextView.setText(item.categoryTitle, TextView.BufferType.EDITABLE)
                    else {
                        updateCategory(item, text)
                    }
                } else {
                    currentEditText = holder.editTextView
                    inputMethodManager?.showSoftInput(holder.editTextView, 0)

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
            inputMethodManager?.hideSoftInputFromWindow(currentEditText?.windowToken, 0)
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
