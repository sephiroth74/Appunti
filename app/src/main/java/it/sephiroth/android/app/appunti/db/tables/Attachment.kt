package it.sephiroth.android.app.appunti.db.tables

import com.dbflow5.annotation.*
import com.dbflow5.reactivestreams.structure.BaseRXModel
import it.sephiroth.android.app.appunti.db.AppDatabase


@Table(database = AppDatabase::class)
class Attachment() : BaseRXModel() {

    constructor(other: Attachment) : this() {
        attachmentID = other.attachmentID
        attachmentEntryID = other.attachmentEntryID
        attachmentTitle = other.attachmentTitle
        attachmentMime = other.attachmentMime
        attachmentPath = other.attachmentPath
        attachmentOriginalPath = other.attachmentOriginalPath
        attachmentDescription = other.attachmentDescription
    }

    enum class AttachmentType { LOCAL }

    @PrimaryKey(autoincrement = true)
    var attachmentID: Long = 0

    @ForeignKey(tableClass = Entry::class, onDelete = ForeignKeyAction.CASCADE)
    var attachmentEntryID: Long? = 0

    var attachmentTitle: String? = null

    var attachmentDescription: String? = null

    var attachmentMime: String? = null

    var attachmentType: AttachmentType = AttachmentType.LOCAL

    /** relative path */
    @Column(defaultValue = "")
    var attachmentPath: String = ""

    /** original file/content path */
    var attachmentOriginalPath: String? = null

    fun isImage() = attachmentMime?.startsWith("image/", true) == true

    fun isVideo() = attachmentMime?.startsWith("video/", true) == true

    fun isPdf() = attachmentMime?.startsWith("application/pdf", true) == true

    fun isText() = attachmentMime?.equals("text/plain", true) == true

    fun isDoc() = attachmentMime?.equals("application/msword", true) == true

    fun isZip() = attachmentMime?.equals("application/zip", true) == true

    fun isAudio() = attachmentMime?.startsWith("audio/", true) == true

    fun isVoice() = isAudio() && (
            attachmentMime?.endsWith("amr", true) == true
                    || attachmentMime?.endsWith("amr-wb", true) == true
            )

    override fun toString(): String {
        return "Attachment(entryID=$attachmentEntryID, attachmentTitle=$attachmentTitle, attachmentMime=$attachmentMime, attachmentPath=$attachmentPath)"
    }
}