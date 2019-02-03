package it.sephiroth.android.app.appunti.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager

class NpaLinearLayoutManager(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        LinearLayoutManager(context, attrs, defStyleAttr, defStyleRes) {

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}