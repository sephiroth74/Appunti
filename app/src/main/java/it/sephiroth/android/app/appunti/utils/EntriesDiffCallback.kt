package it.sephiroth.android.app.appunti.utils

import androidx.recyclerview.widget.DiffUtil
import it.sephiroth.android.app.appunti.widget.ItemEntryListAdapter

class EntriesDiffCallback(private var oldData: List<ItemEntryListAdapter.EntryItem>,
                          private var newData: List<ItemEntryListAdapter.EntryItem>) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldData.size
    override fun getNewListSize(): Int = newData.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldData[oldItemPosition]
        val newItem = newData[newItemPosition]

        if (newItem.type == oldItem.type) {
            return when (newItem.type) {
                ItemEntryListAdapter.EntryItem.ItemType.ENTRY -> newItem.entry !!.entryID == oldItem.entry !!.entryID
                else -> true
            }
        }

        return false
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldData[oldItemPosition]
        val newItem = newData[newItemPosition]
        return oldItem == newItem
    }
}