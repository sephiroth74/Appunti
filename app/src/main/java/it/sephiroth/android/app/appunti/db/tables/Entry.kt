package it.sephiroth.android.app.appunti.db.tables

import android.os.Parcel
import android.os.Parcelable
import android.provider.ContactsContract
import com.dbflow5.annotation.*
import com.dbflow5.query.select
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.structure.oneToMany
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.EntryTypeConverter
import timber.log.Timber
import java.util.*

@Table(database = AppDatabase::class, indexGroups = [
    IndexGroup(number = 1, name = "firstIndex")
])
class Entry() : BaseRXModel(), Parcelable {

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
    var entryCreationDate: Date = Date()

    @Column(defaultValue = "")
    var entryText: String? = null

    @Column(typeConverter = EntryTypeConverter::class)
    var entryType: EntryType = EntryType.TEXT

    var entryPinned: Int = 0

    @Index(indexGroups = [1])
    var entryModifiedDate: Date = Date()

    @get:OneToMany
    var attachments by oneToMany { select from Attachment::class where (Attachment_Table.attachmentEntryID_entryID.eq(entryID)) }

    constructor(parcel: Parcel) : this() {
        entryID = parcel.readInt()
        entryTitle = parcel.readString()
        entryPriority = parcel.readInt()
        entryText = parcel.readString()
        entryPinned = parcel.readInt()
    }

    override fun toString(): String {
        return "Entry(id=$entryID, title=$entryTitle, category=$category, pinned=$entryPinned, priority=$entryPriority)"
    }

    override fun equals(other: Any?): Boolean {
        Timber.i("$this equals $other")
        when (other) {
            is Entry -> {
                return (entryID == other.entryID
                        && entryTitle == other.entryTitle
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

    enum class EntryType {
        TEXT, LIST
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(entryID)
        parcel.writeString(entryTitle)
        parcel.writeInt(entryPriority)
        parcel.writeString(entryText)
        parcel.writeInt(entryPinned)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Entry> {
        override fun createFromParcel(parcel: Parcel): Entry {
            return Entry(parcel)
        }

        override fun newArray(size: Int): Array<Entry?> {
            return arrayOfNulls(size)
        }
    }
}