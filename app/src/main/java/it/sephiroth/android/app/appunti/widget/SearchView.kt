package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import com.lapism.searchview.widget.SearchViewSavedState

class SearchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.lapism.searchview.widget.SearchView(context, attrs, defStyleAttr) {

    override fun onSaveInstanceState(): Parcelable? {
        return super.onSaveInstanceState()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SearchViewSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)

        if (state.query != null) {
            setText(state.query)
        }
        requestLayout()
    }
}