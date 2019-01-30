package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatImageButton

class CheckableAppcompatImageButton(context: Context?, attrs: AttributeSet?) : AppCompatImageButton(context, attrs), Checkable {

    private var mChecked: Boolean = false


    private var checkedChangedListener: ((checked: Boolean) -> Unit)? = null

    fun doOnCheckedChanged(action: (checked: Boolean) -> Unit) {
        checkedChangedListener = action
    }

    override fun isChecked(): Boolean {
        return mChecked
    }

    override fun setChecked(value: Boolean) {
        mChecked = value
        checkedChangedListener?.invoke(value)
        refreshDrawableState()
    }

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    companion object {
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }


}