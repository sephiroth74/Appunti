package it.sephiroth.android.app.appunti

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import it.sephiroth.android.app.appunti.graphics.CircularSolidDrawable
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import kotlinx.android.synthetic.main.appunti_category_color_button.view.*
import kotlinx.android.synthetic.main.category_colors_bottomsheet_fragment.*

class CategoryColorsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    var actionListener: ((Int) -> Unit)? = null

    private lateinit var categoryColors: IntArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var root = inflater.inflate(R.layout.category_colors_bottomsheet_fragment, container, false)
        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        categoryColors = ResourceUtils.getCategoryColors(context!!)

        for (colorIndex in 0 until categoryColors.size) {
            val color = categoryColors[colorIndex]
            val view = LayoutInflater.from(context).inflate(R.layout.appunti_category_color_bottomsheet_button, categoryColorsContainer, false)

            val drawable = CircularSolidDrawable(context!!, color)
            view.colorButton.setImageDrawable(drawable)
            categoryColorsContainer.addView(view)

            view.colorButton.setOnClickListener {
                actionListener?.invoke(colorIndex)
            }

        }
    }
}