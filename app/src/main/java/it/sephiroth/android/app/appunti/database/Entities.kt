@file:Suppress("PropertyName")

package it.sephiroth.android.app.appunti.database

import androidx.annotation.IntDef
import androidx.room.*
import java.util.*

@Entity(foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["category_uid"], childColumns = ["entry_category_uid"], onDelete = ForeignKey.SET_DEFAULT)],
        tableName = "entries",
        indices = [Index(value = ["entry_category_uid"])])
data class Entry(
        var entry_title: String,
        var entry_priority: Int = 5,
        var entry_category_uid: Int = 1,
        var entry_date: Date = Date(),
        var entry_text: String = "") {

    @TypeConverters(EntryTypeConverter::class)
    var entry_type: EntryType = EntryType.TEXT

    @PrimaryKey(autoGenerate = true)
    var entry_uid: Int = 0

    enum class EntryType {
        TEXT, LIST
    }
}

@Entity(tableName = "attachments",
        foreignKeys = [ForeignKey(entity = Entry::class, parentColumns = ["entry_uid"], childColumns = ["attachment_entry_uid"], onDelete = ForeignKey.CASCADE)])
data class Attachment(
        var attachment_entry_uid: Int,
        var attachment_title: String,
        var attachment_mime: String,
        var attachment_path: String) {

    @PrimaryKey(autoGenerate = true)
    var attachment_uid: Int = 0
}

@Entity(tableName = "categories", indices = [Index(value = ["category_title"], unique = true)])
data class Category(
        var category_title: String,
        var category_color_index: Int = 0) {

    @PrimaryKey(autoGenerate = true)
    var category_uid: Int = 0

    @TypeConverters(CategoryTypeConverter::class)
    var category_type: CategoryType = CategoryType.SYSTEM

    override fun toString(): String {
        return "Category(uid=$category_uid, title=$category_title, color=$category_color_index)"
    }

    enum class CategoryType {
        SYSTEM, USER
    }
}

class EntryWithCategory {
    @Embedded
    lateinit var entry: Entry

    @Embedded
    lateinit var category: Category

    @Relation(parentColumn = "entry_uid", entityColumn = "attachment_uid", entity = Attachment::class)
    var attachments: List<Attachment>? = null

    override fun toString(): String {
        return "EntryWithCategory(entry=$entry, category=$category, attachments=$attachments)"
    }
}
