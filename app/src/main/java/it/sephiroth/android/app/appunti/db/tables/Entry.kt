package it.sephiroth.android.app.appunti.db.tables

import android.content.Context
import com.dbflow5.annotation.*
import com.dbflow5.query.select
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.structure.oneToMany
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.EntryTypeConverter
import it.sephiroth.android.app.appunti.db.InstantTypeConverter
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import org.threeten.bp.Instant
import timber.log.Timber

@Table(database = AppDatabase::class, indexGroups = [
    IndexGroup(number = 1, name = "firstIndex")
])
class Entry() : BaseRXModel() {

    constructor(other: Entry) : this() {
        entryID = other.entryID
        entryTitle = other.entryTitle
        entryPriority = other.entryPriority
        category = other.category
        entryCreationDate = other.entryCreationDate
        entryText = other.entryText
        entryType = other.entryType
        entryPinned = other.entryPinned
        entryModifiedDate = other.entryModifiedDate
        entryDeleted = other.entryDeleted
        entryArchived = other.entryArchived
        attachments = other.attachments
    }

    @PrimaryKey(autoincrement = true)
    @Index(indexGroups = [1])
    var entryID: Int = 0

    @Column(defaultValue = "")
    var entryTitle: String? = null

    var entryPriority: Int = 5

    @ForeignKey(onDelete = ForeignKeyAction.SET_NULL)
    var category: Category? = null

    @Index(indexGroups = [1])
    @Column(typeConverter = InstantTypeConverter::class)
    var entryCreationDate: Instant = Instant.now()

    @Column(defaultValue = "")
    var entryText: String? = null

    @Column(typeConverter = EntryTypeConverter::class)
    var entryType: EntryType = EntryType.TEXT

    var entryPinned: Int = 0

    var entryArchived: Int = 0

    var entryDeleted: Int = 0

    @Index(indexGroups = [1])
    @Column(typeConverter = InstantTypeConverter::class)
    var entryModifiedDate: Instant = Instant.now()

    @get:OneToMany
    var attachments by oneToMany { select from Attachment::class where (Attachment_Table.attachmentEntryID_entryID.eq(entryID)) }

    override fun toString(): String {
        return "Entry(id=$entryID, title=$entryTitle, category=$category, pinned=$entryPinned, archived=$entryArchived, " +
                "deleted=$entryDeleted, priority=$entryPriority, modified=${entryModifiedDate.toEpochMilli()})"
    }

    override fun equals(other: Any?): Boolean {
        when (other) {
            is Entry -> {
                return (entryID == other.entryID
                        && entryTitle == other.entryTitle
                        && entryText == other.entryText
                        && entryPriority == other.entryPriority
                        && entryCreationDate == other.entryCreationDate
                        && entryModifiedDate == other.entryModifiedDate
                        && entryPinned == other.entryPinned
                        && category == other.category
                        && attachments == other.attachments)
            }
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        Timber.w("Entry.hashCode")
        var result = entryID
        result = 31 * result + (entryTitle?.hashCode() ?: 0)
        result = 31 * result + (entryText?.hashCode() ?: 0)
        result = 31 * result + entryPriority
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + entryType.hashCode()
//        result = 31 * result + entryPinned
//        result = 31 * result + entryArchived
//        result = 31 * result + entryDeleted

        result = 31 * result + ((entryPinned shl 1) or (entryArchived shl 2) or (entryDeleted shl 3))
        result = 31 * result + entryCreationDate.hashCode()
        result = 31 * result + entryModifiedDate.hashCode()
        return result
    }

    fun getColor(context: Context): Int {
        return ResourceUtils.getCategoryColors(context)[category?.categoryColorIndex ?: 0]
    }

    fun isNew() = entryID == 0
    fun isDeleted() = entryDeleted == 1
    fun isArchived() = entryArchived == 1
    fun isPinned() = entryPinned == 1

    enum class EntryType {
        TEXT, LIST
    }
}