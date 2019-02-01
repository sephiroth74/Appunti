package it.sephiroth.android.app.appunti

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.dbflow5.query.OrderBy
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.structure.save
import com.google.android.material.snackbar.Snackbar
import it.sephiroth.android.app.appunti.db.tables.Category
import it.sephiroth.android.app.appunti.db.tables.Category_Table
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.graphics.CircularSolidDrawable
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.activity_categories.*
import kotlinx.android.synthetic.main.category_item_list_content.view.*
import timber.log.Timber


class CategoriesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val categories = select().from(Category::class).orderBy(OrderBy(Category_Table.categoryID.nameAlias, true)).list
        val adapter = CategoriesAdapter(this, categories.toMutableList())
        categoriesRecycler.adapter = adapter

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }


    class CategoriesAdapter(private var context: CategoriesActivity, private var values: MutableList<Category>) : RecyclerView
    .Adapter<CategoriesAdapter.ViewHolder>() {

        private val categoryColors = ResourceUtils.getCategoryColors(context)
        private var currentEditText: EditText? = null
        private val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.category_item_list_content, parent, false)
            return ViewHolder(view)
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

            Timber.v("category title=${item.categoryTitle}, type=${item.categoryType}")

            holder.editTextView.setOnFocusChangeListener { v, hasFocus ->
                holder.editButton.isEnabled = !hasFocus

                if (!hasFocus) {
                    val text = holder.editTextView.text.toString()
                    if (text.isEmpty()) holder.editTextView.setText(item.categoryTitle, TextView.BufferType.EDITABLE)
                    else {
                        if (text != item.categoryTitle) {
                            Timber.v("saving new category title....")
                            item.categoryTitle = text
                            item.save()
                        }
                    }
                } else {
                    currentEditText = holder.editTextView
                    inputMethodManager?.showSoftInput(holder.editTextView, 0)

                }
            }

            holder.colorButton.setOnClickListener {
                removeFocusFromEditText()
            }

            holder.deleteButton.setOnClickListener {
                removeFocusFromEditText()


                val index = holder.adapterPosition
                val category = holder.category!!

                values.remove(category)
                notifyItemRemoved(index)

                Snackbar.make(context.constraintLayout, "Category Deleted", Snackbar.LENGTH_LONG)
                        .setAction("UNDO") {
                            Timber.v("undo clicked!")
                        }
                        .setActionTextColor(context.theme.getColorStateList(context, R.attr.colorError))
                        .addCallback(object : Snackbar.Callback() {

                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                super.onDismissed(transientBottomBar, event)

                                if (event == Snackbar.Callback.DISMISS_EVENT_ACTION) {
                                    Timber.d("must undo the event!")
                                    values.add(index, category)
                                    notifyItemInserted(index)

                                } else {
                                    Timber.d("must consolidate the event!")
                                }
                            }
                        })
                        .show()

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
