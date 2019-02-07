package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.setPadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.graphics.CategoryColorDrawable
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.appunti_category_color_button_checkable.view.*
import kotlin.math.min

class GridLayoutColorChooser @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
                                                      ) : FrameLayout(context, attrs, defStyleAttr) {

    private var mSelectedColorIndex = -1

    private var actionListener: ((Int, Int) -> Unit)? = null
    private var recyclerView: RecyclerView = RecyclerView(context)
    private var layoutManager = GridLayoutManager(context, 10)
    private var categoryColors: IntArray
    private var buttonPaddingLeft: Int
    private var buttonPaddingRight: Int
    private var hasFrame: Boolean = false
    private var buttonSize = resources.getDimensionPixelSize(R.dimen.appunti_color_button_large)
    private var buttonPadding = resources.getDimensionPixelSize(R.dimen.appunti_color_button_large_margin)
    private var adapter: ColorsAdapter? = null

    fun setOnColorSelectedListener(action: (index: Int, color: Int) -> Unit) {
        actionListener = action
    }

    fun getSelectedColorIndex(): Int {
        return mSelectedColorIndex
    }

    fun setSelectedColorIndex(value: Int) {
        mSelectedColorIndex = min(value, categoryColors.size - 1)
        adapter?.setSelectedIndex(mSelectedColorIndex, false)
    }

    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.HorizontalColorChooser, 0, 0)
        val colorsResId = array.getResourceId(R.styleable.HorizontalColorChooser_appunti_colors, 0)

        categoryColors = if (colorsResId > 0) {
            context.resources.getIntArray(colorsResId)
        } else {
            ResourceUtils.getCategoryColors(context)
        }

        mSelectedColorIndex =
                min(array.getInteger(R.styleable.HorizontalColorChooser_appunti_selectedColorIndex, -1), categoryColors.size - 1)

        buttonPaddingLeft = array.getDimensionPixelSize(R.styleable.HorizontalColorChooser_appunti_color_padding_left, 0)
        buttonPaddingRight = array.getDimensionPixelSize(R.styleable.HorizontalColorChooser_appunti_color_padding_right, 0)

        array.recycle()

        recyclerView.layoutManager = layoutManager
        addView(recyclerView)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w > 0) {
            if (!hasFrame) {
                hasFrame = true
                initialize()
            }
        }
    }

    private fun initialize() {
        if (null == adapter) {
            val row = width / (buttonSize + buttonPadding)
            layoutManager.spanCount = row
            adapter = ColorsAdapter(context, categoryColors, mSelectedColorIndex)
            adapter?.actionListener = { index, color ->
                mSelectedColorIndex = index
                actionListener?.invoke(index, color)
            }
            recyclerView.adapter = adapter
        }
    }

    class ColorsAdapter(val context: Context,
                        val colors: IntArray,
                        initialSelection: Int) : RecyclerView.Adapter<ColorViewHolder>() {

        var selectedColorIndex: Int = min(initialSelection, colors.size - 1)
            private set

        private val layoutInflater = LayoutInflater.from(context)
        private val buttonSize = context.resources.getDimensionPixelSize(R.dimen.appunti_color_button_large)
        private val buttonPadding = context.resources.getDimensionPixelSize(R.dimen.appunti_color_button_large_margin)

        var actionListener: ((Int, Int) -> Unit)? = null

        fun setSelectedIndex(value: Int, fromUser: Boolean) {
            if (value != selectedColorIndex) {
                val oldIndex = selectedColorIndex
                selectedColorIndex = min(value, colors.size - 1)

                if (oldIndex > -1) notifyItemChanged(oldIndex)
                if (selectedColorIndex > -1) notifyItemChanged(selectedColorIndex)
                if (fromUser) actionListener?.invoke(selectedColorIndex, colors[selectedColorIndex])
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = layoutInflater.inflate(R.layout.appunti_category_color_button_checkable, parent,
                    false) as CheckableAppcompatImageButton
            view.setPadding(buttonPadding)
            view.allowUserToggle = false
            view.layoutParams.width = (buttonSize + buttonPadding)
            view.layoutParams.height = (buttonSize + buttonPadding)

            val drawable = CategoryColorDrawable(context, Color.WHITE)
            view.colorButton.setImageDrawable(drawable)

            return ColorViewHolder(view)
        }

        override fun getItemCount(): Int {
            return colors.size
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val drawable = (holder.itemView.colorButton.drawable as CategoryColorDrawable)
            drawable.setColorFilter(colors[position], PorterDuff.Mode.DST)

            holder.checkableItemView.isChecked = position == selectedColorIndex

            holder.checkableItemView.setOnClickListener {
                if (selectedColorIndex != position) {
                    setSelectedIndex(position, true)
                }
            }
        }

    }

    class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkableItemView = itemView as CheckableAppcompatImageButton
    }

}