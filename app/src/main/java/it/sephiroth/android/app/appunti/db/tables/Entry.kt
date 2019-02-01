package it.sephiroth.android.app.appunti.db.tables

import com.dbflow5.annotation.*
import com.dbflow5.query.select
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.structure.oneToMany
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.EntryTypeConverter
import java.util.*

@Table(database = AppDatabase::class, indexGroups = [
    IndexGroup(number = 1, name = "firstIndex")
])
class Entry : BaseRXModel() {
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

    enum class EntryType {
        TEXT, LIST
    }
}