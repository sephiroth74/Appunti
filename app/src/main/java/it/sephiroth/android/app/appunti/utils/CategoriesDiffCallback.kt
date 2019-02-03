package it.sephiroth.android.app.appunti.utils

import androidx.recyclerview.widget.DiffUtil
import it.sephiroth.android.app.appunti.db.tables.Category

class CategoriesDiffCallback(private var oldData: List<Category>, private var newData: List<Category>) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldData.size
    override fun getNewListSize(): Int = newData.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldData[oldItemPosition].categoryID == newData[newItemPosition].categoryID
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldData[oldItemPosition]
        val newItem = newData[newItemPosition]
        return oldItem == newItem
    }
}