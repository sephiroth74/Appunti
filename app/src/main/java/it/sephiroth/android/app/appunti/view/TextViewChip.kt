package it.sephiroth.android.app.appunti.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import it.sephiroth.android.app.appunti.R
import it.sephiroth.android.app.appunti.utils.MaterialBackgroundUtils

class TextViewChip @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {


    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.TextViewChip, defStyleAttr, 0)

        background = if (array.getBoolean(R.styleable.TextViewChip_android_clickable, false))
            MaterialBackgroundUtils.categoryChipClickable(context)
        else
            MaterialBackgroundUtils.categoryChip(context)

        array.recycle()
    }

}