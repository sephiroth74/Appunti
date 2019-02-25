package it.sephiroth.android.app.appunti.models

import com.google.gson.Gson
import it.sephiroth.android.app.appunti.ext.fromJson
import it.sephiroth.android.app.appunti.utils.UUIDUtils
import timber.log.Timber

class EntryListJsonModel {

    data class EntryJson(
        val id: Long = UUIDUtils.randomLongUUID(),
        val position: Int = 0,
        var text: String = "",
        var checked: Boolean = false
    ) {
        override fun hashCode(): Int {
            return id.hashCode()
        }

        fun toJSon(): String {
            return Gson().toJson(this)
        }
    }

    private val gson = Gson()
    private var data: MutableList<EntryJson>? = null
    private var uncheckedList = mutableListOf<EntryJson>()
    private var checkedList = mutableListOf<EntryJson>()

    companion object {
        const val TYPE_UNCHECKED = 0
        const val TYPE_CHECKED = 1
        const val TYPE_NEW_ENTRY = 2
    }

    private val listComparator = Comparator<EntryJson> { o1, o2 ->
        if (o1.checked == o2.checked) {
            if (o1.position < o2.position) -1 else 1
        } else {
            if (o1.checked) 1 else -1
        }
    }

    fun fromJson(text: String) {
        Timber.i("parseString($text)")
        data = gson.fromJson<MutableList<EntryJson>>(text)
        data?.let { data ->
            uncheckedList = (data.filter { entry -> !entry.checked }.sortedWith(listComparator)).toMutableList()
            checkedList = (data.filter { entry -> entry.checked }.sortedWith(listComparator)).toMutableList()
        }
        Timber.i("unchecked size: ${uncheckedList.size}, checked size: ${checkedList.size}")
    }

    fun toJson(): String {
        val finalData = mutableListOf<EntryJson>()
        finalData.addAll(uncheckedList)
        finalData.addAll(checkedList)
        val jsonString = gson.toJson(finalData)
        Timber.v("jsonString = $jsonString")
        return jsonString
    }

    override fun toString(): String {
        return StringBuilder().apply {
            uncheckedList.forEach { append("${it.text}\n") }
            checkedList.forEach { append("${it.text}\n") }
        }.toString()
    }

    fun size(): Int {
        return uncheckedList.size + checkedList.size + 1
    }

    fun getItemId(position: Int): Long {
        return when {
            position < uncheckedList.size -> uncheckedList[position].id
            position == uncheckedList.size -> -1
            else -> checkedList[position - uncheckedList.size - 1].id
        }
    }

    fun getItem(position: Int): EntryJson {
        return when {
            position < uncheckedList.size -> uncheckedList[position]
            else -> checkedList[position - uncheckedList.size - 1]
        }
    }

    fun getItemType(position: Int): Int {
        return when {
            position < uncheckedList.size -> TYPE_UNCHECKED
            position == uncheckedList.size -> TYPE_NEW_ENTRY
            else -> TYPE_CHECKED
        }
    }

    fun deleteItem(entry: EntryJson, type: Int): Int? {
        if (type == TYPE_UNCHECKED) {
            val index = uncheckedList.indexOfFirst { entryJson -> entryJson.id == entry.id }
            if (index > -1) {
                uncheckedList.removeAt(index)
                return index
            }
        } else if (type == TYPE_CHECKED) {
            val index = checkedList.indexOfFirst { entryJson -> entryJson.id == entry.id }
            if (index > -1) {
                checkedList.removeAt(index)
                return index + uncheckedList.size + 1
            }
        }
        return null
    }

    fun newItem(): Int {
        val newEntry =
            EntryJson(UUIDUtils.randomLongUUID(), (uncheckedList.size + checkedList.size))
        uncheckedList.add(newEntry)
        return (uncheckedList.size - 1)
    }

    fun toggle(entry: EntryJson, type: Int): Pair<Int, Int>? {
        Timber.i("toggle($entry, $type)")

        if (type == TYPE_UNCHECKED) {
            val removedIndex = uncheckedList.indexOfFirst { entryJson -> entryJson.id == entry.id }
            if (removedIndex > -1) {
                uncheckedList.removeAt(removedIndex)
                entry.checked = true
                var addedIndex = checkedList.indexOfFirst { entryJson -> entryJson.position > entry.position }
                if (addedIndex < 0) addedIndex = checkedList.size
                checkedList.add(addedIndex, entry)

                Timber.v("removedIndex=$removedIndex, addedIndex=${addedIndex + uncheckedList.size + 1}")
                return Pair(removedIndex, addedIndex + uncheckedList.size + 1)

            }
        } else if (type == TYPE_CHECKED) {
            val removedIndex = checkedList.indexOfFirst { entryJson -> entryJson.id == entry.id }
            if (removedIndex > -1) {
                checkedList.removeAt(removedIndex)
                entry.checked = false
                var addedIndex = uncheckedList.indexOfFirst { entryJson -> entryJson.position > entry.position }
                if (addedIndex < 0) addedIndex = uncheckedList.size
                uncheckedList.add(addedIndex, entry)

                Timber.v("removedIndex=${removedIndex + uncheckedList.size}, addedIndex=${addedIndex}")

                return Pair(removedIndex + uncheckedList.size, addedIndex)
            }
        }
        return null
    }

    fun setData(triple: Triple<MutableList<EntryJson>, MutableList<EntryJson>, MutableList<EntryJson>>?) {
        triple?.let { triple ->
            data = triple.first
            uncheckedList = triple.second
            checkedList = triple.third
        } ?: run {
            data?.clear()
            uncheckedList.clear()
            checkedList.clear()
        }
    }
}