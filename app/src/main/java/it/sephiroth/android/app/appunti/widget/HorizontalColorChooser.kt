package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.graphics.CategoryColorDrawable
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.appunti_category_color_button_checkable.view.*
import timber.log.Timber

class HorizontalColorChooser @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var selectedColorIndex: Int = -1
        set(value) {
            if (value != field) {
                setSelectedColorIndex(field, false)
                setSelectedColorIndex(value, true)
                field = value
            }
        }

    private var actionListener: ((Int, Int) -> Unit)? = null
    private var linearLayout: LinearLayout = LinearLayout(context)
    private var categoryColors: IntArray
    private var buttonPaddingLeft: Int
    private var buttonPaddingRight: Int

    fun setOnColorSelectedListener(action: (index: Int, color: Int) -> Unit) {
        actionListener = action
    }

    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.HorizontalColorChooser, 0, 0)
        val colorsResId = array.getResourceId(R.styleable.HorizontalColorChooser_appunti_colors, 0)

        categoryColors = if (colorsResId > 0) {
            context.resources.getIntArray(colorsResId)
        } else {
            ResourceUtils.getCategoryColors(context)
        }

        selectedColorIndex = array.getInteger(R.styleable.HorizontalColorChooser_appunti_selectedColorIndex, -1)
        buttonPaddingLeft = array.getDimensionPixelSize(R.styleable.HorizontalColorChooser_appunti_color_padding_left, 0)
        buttonPaddingRight = array.getDimensionPixelSize(R.styleable.HorizontalColorChooser_appunti_color_padding_right, 0)

        array.recycle()

        linearLayout.orientation = LinearLayout.HORIZONTAL
        linearLayout.gravity = Gravity.CENTER_VERTICAL
        addView(linearLayout)

        populate()

        if (selectedColorIndex != -1) {
            setSelectedColorIndex(selectedColorIndex, true)
        }
    }

    private fun setSelectedColorIndex(index: Int, checked: Boolean) {
        Timber.i("setSelectedColorIndex($index, $checked)")
        if (index >= 0 && index < linearLayout.childCount) {
            val button = linearLayout.getChildAt(index) as CheckableAppcompatImageButton
            button.isChecked = checked
        }
    }

    private fun populate() {
        for (colorIndex in 0 until categoryColors.size) {
            val color = categoryColors[colorIndex]
            val view = LayoutInflater.from(context).inflate(R.layout.appunti_category_color_button_checkable, linearLayout, false) as CheckableAppcompatImageButton
            view.allowUserToggle = false

            val params = view.layoutParams
            if (params is MarginLayoutParams) {
                params.marginStart = buttonPaddingLeft
                params.marginEnd = buttonPaddingRight
            }

            val drawable = CategoryColorDrawable(context!!, color)
            view.colorButton.setImageDrawable(drawable)
            linearLayout.addView(view)

            view.colorButton.setOnClickListener {
                selectedColorIndex = colorIndex
                actionListener?.invoke(colorIndex, categoryColors[colorIndex])
            }

        }
    }

}