package it.sephiroth.android.app.appunti

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.category_colors_bottomsheet_fragment.*

class CategoryColorsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    var actionListener: ((Int) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.category_colors_bottomsheet_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        colorChooser.setOnColorSelectedListener { colorIndex, colorValue ->
            actionListener?.invoke(colorIndex)
        }

        if (arguments?.containsKey(BUNDLE_KEY_COLOR_INDEX) == true) {
            colorChooser.selectedColorIndex = arguments?.getInt(BUNDLE_KEY_COLOR_INDEX) ?: -1
        }
    }

    companion object {
        const val BUNDLE_KEY_COLOR_INDEX = "key_color_index"
    }
}