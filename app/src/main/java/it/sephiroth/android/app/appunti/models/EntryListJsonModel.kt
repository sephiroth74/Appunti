package it.sephiroth.android.app.appunti.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import it.sephiroth.android.app.appunti.BuildConfig
import timber.log.Timber
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class EntryListJsonModel {

    data class EntryJson(
        var position: Int = 0,
        var text: String = "",
        var checked: Boolean = false
    ) {

        @Transient
        val id: Long = generateID()

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "EntryJson(id=$id, position=$position, text='$text', checked=$checked)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EntryJson

            if (position != other.position) return false
            if (text != other.text) return false
            if (checked != other.checked) return false
            if (id != other.id) return false

            return true
        }

        companion object {
            private val nextID = AtomicLong(0)

            fun generateID(): Long = nextID.incrementAndGet()
        }
    }

    private val gson = if (!BuildConfig.DEBUG) GsonBuilder().setPrettyPrinting().create() else Gson()
    private var uncheckedList = mutableListOf<EntryJson>()
    private var checkedList = mutableListOf<EntryJson>()

    companion object {

        const val TYPE_UNCHECKED = 0
        const val TYPE_CHECKED = 1
        const val TYPE_NEW_ENTRY = 2

        val listComparator = Comparator<EntryJson> { o1, o2 ->
            if (o1.checked == o2.checked) {
                when {
                    o1.position < o2.position -> -1
                    o1.position > o2.position -> 1
                    else -> 0
                }
            } else {
                if (o1.checked) 1 else -1
            }
        }
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

    private fun getNextPosition(): Int {
        val pos1 = if (checkedList.isNotEmpty()) checkedList.sortedByDescending { it.position }.first().position else 0
        val pos2 =
            if (uncheckedList.isNotEmpty()) uncheckedList.sortedByDescending { it.position }.first().position else 0
        return if (pos1 > pos2) pos1 + 1 else pos2 + 1
    }

    private fun getItemById(id: Long): EntryJson? {
        return uncheckedList.find { it.id == id }
            ?: run {
                checkedList.find { it.id == id }
            }
    }

    /**
     * Return the relative item index (according to its list)
     */
    private fun getItemIndexRelative(item: EntryJson): Int {
        return if (item.checked) {
            checkedList.indexOf(item)
        } else {
            uncheckedList.indexOf(item)
        }
    }

    fun getPreviousItemIndex(item: EntryJson): Int {
        return if (item.checked) {
            val index = checkedList.indexOf(item)
            if (index > 0) {
                (uncheckedList.size + 1) + (index - 1)
            } else {
                if (uncheckedList.size > 0) (uncheckedList.size - 1) else -1
            }

        } else {
            val index = uncheckedList.indexOf(item)
            if (index > 0) index - 1 else -1
        }
    }

    private fun increaseItemsPositions(position: Int) {
        uncheckedList.filter { it.position >= position }.forEach { it.position = it.position + 1 }
        checkedList.filter { it.position >= position }.forEach { it.position = it.position + 1 }
    }

    fun deleteItem(entry: EntryJson): Int? {
        if (!entry.checked) {
            val index = uncheckedList.indexOfFirst { entryJson -> entryJson.id == entry.id }
            if (index > -1) {
                uncheckedList.removeAt(index)
                return index
            }
        } else if (entry.checked) {
            val index = checkedList.indexOfFirst { entryJson -> entryJson.id == entry.id }
            if (index > -1) {
                checkedList.removeAt(index)
                return index + uncheckedList.size + 1
            }
        }
        return null
    }

    fun addItem(text: String? = null, checked: Boolean = false): Int {
        Timber.i("addItem($checked)")
        val nextPosition = getNextPosition()
        Timber.v("next position = $nextPosition")

        val newEntry = EntryJson(nextPosition, text ?: "", checked)

        return if (checked) {
            checkedList.add(newEntry)
            uncheckedList.size + checkedList.size
        } else {
            uncheckedList.add(newEntry)
            uncheckedList.size - 1
        }
    }

    @Suppress("NAME_SHADOWING")
    fun insertItem(id: Long, text: String?): Int {
        Timber.i("insertItem($id)")
        val itemBefore = getItemById(id)

        itemBefore?.let { itemBefore ->
            val position = itemBefore.position + 1
            val checked = itemBefore.checked
            val index = getItemIndexRelative(itemBefore)

            index.let { index ->
                Timber.v("index before = $index")
                Timber.v("checked before = $checked")

                val newEntry = EntryJson(position = position, checked = checked, text = text ?: "")
                increaseItemsPositions(position)

                return if (checked) {
                    checkedList.add(index + 1, newEntry)
                    index + 1 + uncheckedList.size + 1
                } else {
                    uncheckedList.add(index + 1, newEntry)
                    index + 1
                }
            }
        }

        return -1

    }

    fun toggle(entry: EntryJson): Pair<Int, Int>? {
        Timber.i("toggle($entry)")

        if (!entry.checked) {
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
        } else if (entry.checked) {
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

    @Suppress("NAME_SHADOWING")
    fun setData(triple: Triple<MutableList<EntryJson>, MutableList<EntryJson>, MutableList<EntryJson>>?) {
        triple?.let { triple ->
            Timber.v("uncheckedList = ${triple.second}")
            Timber.v("checkedList = ${triple.third}")
            uncheckedList = triple.second
            checkedList = triple.third
        } ?: run {
            uncheckedList.clear()
            checkedList.clear()
        }
    }

    fun isFirstEntry(entry: EntryJson): Boolean {
        return if (entry.checked) {
            checkedList.indexOf(entry) == 0 && uncheckedList.size == 0
        } else {
            uncheckedList.indexOf(entry) == 0
        }
    }
}