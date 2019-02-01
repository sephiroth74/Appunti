package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class NpaStaggeredGridLayoutManager(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
    : StaggeredGridLayoutManager(context, attrs, defStyleAttr, defStyleRes) {

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }

}