package it.sephiroth.android.app.appunti.widget

import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber
import java.util.*

/**
 * Keeps track of the selection for a recycler view
 */
class MultiChoiceHelper<T>(val adapter: RecyclerView.Adapter<*>) {

    private val selectedPositions = hashMapOf<Long, T>()

    var listener: (() -> Unit)? = null

    var selection: HashMap<Long, T>
        get() = selectedPositions
        private set(value) {}

    private fun notifyPosition(position: Long) {
        Timber.i("notifyPosition($position)")
        adapter.notifyItemChanged(position.toInt())
        listener?.invoke()
    }

    fun clearSelection() {
        if (selectedPositions.size > 0) {
            selectedPositions.clear()
            listener?.invoke()
            adapter.notifyDataSetChanged()
        }
    }

    fun select(position: Long, value: T) {
        if (!isSelected(position)) {
            selectedPositions[position] = value
            notifyPosition(position)
            Timber.v("select(position=$position), isSelected=${isSelected(position)}")
        }
    }

    fun deselect(position: Long): T? {
        var result: T? = null
        if (isSelected(position)) {
            result = selectedPositions.remove(position)
            notifyPosition(position)
        }
        return result
    }

    fun isSelected(position: Long): Boolean {
        return selectedPositions.containsKey(position)
    }
}