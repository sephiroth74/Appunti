package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatImageButton
import it.sephiroth.android.app.appunti.R

class CheckableAppcompatImageButton(context: Context, attrs: AttributeSet?) : AppCompatImageButton(context, attrs), Checkable {

    private var mChecked: Boolean = false
    private var mBroadcasting = false

    var allowUserToggle: Boolean = true

    private var checkedChangedListener: ((checked: Boolean) -> Unit)? = null

    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.CheckableAppcompatImageButton, 0, 0)
        allowUserToggle = array.getBoolean(R.styleable.CheckableAppcompatImageButton_appunti_toggle_enabled, true)
        array.recycle()
    }


    fun doOnCheckedChanged(action: (checked: Boolean) -> Unit) {
        checkedChangedListener = action
    }

    override fun isChecked(): Boolean {
        return mChecked
    }

    override fun setChecked(value: Boolean) {
        if (mChecked != value) {
            mChecked = value
            refreshDrawableState()
            postInvalidate()

            if (mBroadcasting) return

            mBroadcasting = true
            checkedChangedListener?.invoke(value)
            mBroadcasting = false
        }
    }

    override fun toggle() {
        if (allowUserToggle) isChecked = !isChecked
    }

    override fun performClick(): Boolean {
        toggle()
        return super.performClick()
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