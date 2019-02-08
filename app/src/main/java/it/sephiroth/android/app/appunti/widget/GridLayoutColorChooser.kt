package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.setPadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.graphics.CategoryColorDrawable
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.appunti_category_color_button_checkable.view.*
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.min


class GridLayoutColorChooser @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        RecyclerView(context, attrs, defStyleAttr) {

    private var mSelectedColorIndex = -1

    var colors: IntArray
        private set

    private var actionListener: ((Int, Int) -> Unit)? = null
    private var buttonPadding: Int
    private var hasFrame: Boolean = false
    private var buttonSize: Int

    fun setOnColorSelectedListener(action: (index: Int, color: Int) -> Unit) {
        actionListener = action
    }

    fun getSelectedColorIndex(): Int {
        return mSelectedColorIndex
    }

    fun setSelectedColorIndex(value: Int) {
        mSelectedColorIndex = min(value, colors.size - 1)

        if (adapter is ColorsAdapter) {
            (adapter as ColorsAdapter).setSelectedIndex(mSelectedColorIndex, false)
        }
    }

    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.GridLayoutColorChooser, 0, 0)
        val colorsResId = array.getResourceId(R.styleable.GridLayoutColorChooser_appunti_colors, 0)

        colors = if (colorsResId > 0) {
            context.resources.getIntArray(colorsResId)
        } else {
            ResourceUtils.getCategoryColors(context)
        }

        mSelectedColorIndex =
                min(array.getInteger(R.styleable.GridLayoutColorChooser_appunti_selectedColorIndex, -1), colors.size - 1)

        buttonPadding = array.getDimensionPixelSize(R.styleable.GridLayoutColorChooser_appunti_color_padding, 0)
        buttonSize =
                array.getDimensionPixelSize(R.styleable.GridLayoutColorChooser_appunti_colorButtonSize, context.resources.getDimensionPixelSize(R.dimen.appunti_color_button_large))


        array.recycle()

        minimumHeight = buttonSize + buttonPadding
        layoutManager = GridLayoutManager(context, 10)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        Timber.i("onSizeChanged($w, $h)")

        if (w > 0) {
            if (!hasFrame) {
                hasFrame = true
                initialize()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wmode = View.MeasureSpec.getMode(widthMeasureSpec)
        val hmode = View.MeasureSpec.getMode(heightMeasureSpec)
        var wsize = View.MeasureSpec.getSize(widthMeasureSpec)
        var hsize = View.MeasureSpec.getSize(heightMeasureSpec)

        Timber.i("onMeasure(${MeasureSpec.toString(widthMeasureSpec)}, ${MeasureSpec.toString(heightMeasureSpec)})")

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        when (hmode) {
            MeasureSpec.AT_MOST -> {
                val rows = wsize / (buttonSize + buttonPadding)
                val cols = ceil(colors.size.toFloat() / rows).toInt()
                hsize = cols * (buttonSize + buttonPadding)
            }

            MeasureSpec.EXACTLY -> {
            }

            MeasureSpec.UNSPECIFIED -> {
            }
        }

        setMeasuredDimension(wsize, hsize)
    }

    private fun initialize() {
        if (null == adapter) {
            val cols = width / (buttonSize + buttonPadding)
            Timber.v("cols: $cols")
            (layoutManager as GridLayoutManager).spanCount = cols
            adapter = ColorsAdapter()
        }
    }

    inner class ColorsAdapter : RecyclerView.Adapter<ColorViewHolder>() {
        private val layoutInflater = LayoutInflater.from(context)

        fun setSelectedIndex(value: Int, fromUser: Boolean) {
            if (value != mSelectedColorIndex) {
                val oldIndex = mSelectedColorIndex
                mSelectedColorIndex = min(value, colors.size - 1)

                if (oldIndex > -1) notifyItemChanged(oldIndex)
                if (mSelectedColorIndex > -1) notifyItemChanged(mSelectedColorIndex)
                if (fromUser) actionListener?.invoke(mSelectedColorIndex, colors[mSelectedColorIndex])
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = layoutInflater.inflate(R.layout.appunti_category_color_button_checkable, parent,
                    false) as CheckableAppcompatImageButton
            view.setPadding(buttonPadding)
            view.allowUserToggle = false

            val params = view.layoutParams
            params.width = (buttonSize + buttonPadding)
            params.height = (buttonSize + buttonPadding)

            view.layoutParams = params

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

            holder.checkableItemView.isChecked = position == mSelectedColorIndex

            holder.checkableItemView.setOnClickListener {
                if (mSelectedColorIndex != position) {
                    setSelectedIndex(position, true)
                }
            }
        }

    }

    class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkableItemView = itemView as CheckableAppcompatImageButton
    }

}