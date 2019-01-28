package it.sephiroth.android.app.appunti

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.calculateDiff
import androidx.recyclerview.widget.RecyclerView
import it.sephiroth.android.app.appunti.database.EntryWithCategory
import it.sephiroth.android.app.appunti.ext.getColorStateList
import it.sephiroth.android.app.appunti.ext.isLightTheme
import it.sephiroth.android.app.appunti.models.EntryViewModel
import kotlinx.android.synthetic.main.item_list_content.view.*
import kotlinx.android.synthetic.main.item_list_fragment.*
import timber.log.Timber

class ItemListFragment : Fragment() {

    lateinit var model: EntryViewModel
    lateinit var adapter: ItemEntryListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ItemEntryListAdapter(context!!, arrayListOf(), false)
        model = ViewModelProviders.of(this).get(EntryViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.item_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        item_list.setHasFixedSize(false)
        item_list.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        model.entries.observe(this, Observer {
            adapter.update(it)
        })

    }

    class EntriedDiffCallback(var oldData: List<EntryWithCategory>,
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

        init {
            val isLightTheme = context.isLightTheme()
            textColorInverse =
                    context.theme.getColorStateList(context, if (isLightTheme) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary)

            cardForegroundNoStroke = context.getDrawable(R.drawable.material_selectable_item_background_no_stroke)
            cardForegroundStroke = context.getDrawable(R.drawable.material_selectable_item_background_with_stroke)
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.titleTextView.text = item.entry.entry_title
            holder.contentTextView.text = item.entry.entry_text.substring(0, 100) + "..."
            holder.categoryTextView.text = item.category.category_title

            if (null == cardBackgroundColorDefault) {
                cardBackgroundColorDefault = holder.cardView.cardBackgroundColor
                textColorDefault = holder.titleTextView.textColors
            }

            var textColor: ColorStateList? = textColorDefault

            if (item.category.category_color != 0) {

                holder.cardView.setCardBackgroundColor(item.category.category_color)
                holder.cardView.foreground = cardForegroundNoStroke.constantState.newDrawable()
                holder.categoryTextView.visibility = View.VISIBLE


                val luminance = ColorUtils.calculateLuminance(item.category.category_color)

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
                tag = item
                setOnClickListener {
                    Timber.d("card clicked!")
                }
            }

        }

        override fun getItemCount() = values.size

        fun update(newData: List<EntryWithCategory>?) {
            Timber.i("update: ${newData?.size}")

            newData?.let {
                val callback = EntriedDiffCallback(values, it)
                val result = calculateDiff(callback, true)
                values.clear()
                values.addAll(it)
                result.dispatchUpdatesTo(this)
            } ?: run {
                values.clear()
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView = view.id_title
            val contentTextView: TextView = view.id_content
            val categoryTextView = view.chip
            val cardView: CardView = view.id_card

        }
    }
}