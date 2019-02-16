package it.sephiroth.android.app.appunti.db.tables

import com.dbflow5.annotation.ForeignKey
import com.dbflow5.annotation.ForeignKeyAction
import com.dbflow5.annotation.PrimaryKey
import com.dbflow5.annotation.Table
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

    /** relative path */
    var attachmentPath: String? = null

    /** original file/content path */
    var attachmentOriginalPath: String? = null

    override fun toString(): String {
        return "Attachment(entryID=$attachmentEntryID, attachmentTitle=$attachmentTitle, attachmentMime=$attachmentMime, attachmentPath=$attachmentPath)"
    }
}