package it.sephiroth.android.app.appunti.db.tables

import com.dbflow5.annotation.*
import com.dbflow5.reactivestreams.structure.BaseRXModel
import it.sephiroth.android.app.appunti.db.AppDatabase


@Table(database = AppDatabase::class)
class Attachment : BaseRXModel() {

    @PrimaryKey(autoincrement = true)
    var attachmentID: Int = 0

    @ForeignKey(tableClass = Entry::class, onDelete = ForeignKeyAction.CASCADE)
    var attachmentEntryID: Int? = 0
    var attachmentTitle: String? = null
    var attachmentMime: String? = null
    var attachmentPath: String? = null
}