package it.sephiroth.android.app.appunti.db.tables

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.AlarmManagerCompat
import com.dbflow5.annotation.*
import com.dbflow5.config.FlowManager
import com.dbflow5.query.and
import com.dbflow5.query.list
import com.dbflow5.query.select
import com.dbflow5.query.selectCountOf
import com.dbflow5.reactivestreams.structure.BaseRXModel
import it.sephiroth.android.app.appunti.AlarmReceiver
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.EntryTypeConverter
import it.sephiroth.android.app.appunti.db.InstantTypeConverter
import it.sephiroth.android.app.appunti.ext.asList
import it.sephiroth.android.app.appunti.utils.EntryIOUtils
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import timber.log.Timber

@Table(
    database = AppDatabase::class, indexGroups = [
        IndexGroup(number = 1, name = "firstIndex")
    ], allFields = false
)
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
        attachmentList = other.attachmentList?.toList()
        hasAttachments = other.hasAttachments()
        entryAlarm = other.entryAlarm
        entryAlarmEnabled = other.entryAlarmEnabled
    }

    @PrimaryKey(autoincrement = true)
    @Index(indexGroups = [1])
    @Column
    var entryID: Long = 0

    @Column(defaultValue = "")
    var entryTitle: String = ""

    @Column
    var entryPriority: Int = 5

    @ForeignKey(onDelete = ForeignKeyAction.SET_NULL)
    @Column
    var category: Category? = null

    @Index(indexGroups = [1])
    @Column(typeConverter = InstantTypeConverter::class)
    var entryCreationDate: Instant = Instant.now()

    @Column(defaultValue = "")
    var entryText: String = ""

    @Column(typeConverter = EntryTypeConverter::class)
    var entryType: EntryType = EntryType.TEXT

    @Column
    var entryPinned: Int = 0

    @Column
    var entryArchived: Int = 0

    @Column
    var entryDeleted: Int = 0

    @Index(indexGroups = [1])
    @Column(typeConverter = InstantTypeConverter::class)
    var entryModifiedDate: Instant = Instant.now()

    @Column(typeConverter = InstantTypeConverter::class)
    var entryAlarm: Instant? = null // UTC

    @Column
    var entryAlarmEnabled: Boolean = false

    var entryStream: Uri? = null

    // attachments

    private var attachmentList: List<Attachment>? = null
    private var hasAttachments: Boolean? = null

    @OneToMany(oneToManyMethods = [OneToManyMethod.ALL], variableName = "attachmentList")
    fun getAttachments(): List<Attachment>? {
        if (attachmentList == null) {
            attachmentList =
                (select from Attachment::class where (Attachment_Table.attachmentEntryID_entryID.eq(entryID))).list
        }
        return attachmentList
    }

    fun hasAttachments(): Boolean {
        hasAttachments?.let {
            return it
        } ?: run {
            val result = hasAttachmentsInternal()
            hasAttachments = result
            return result
        }
    }

    private fun hasAttachmentsInternal(): Boolean {
        return selectCountOf(Attachment_Table.attachmentEntryID_entryID)
            .from(Attachment::class)
            .where(Attachment_Table.attachmentEntryID_entryID.eq(entryID))
            .hasData(FlowManager.getDatabase(AppDatabase::class.java))
    }

    fun setAttachmentList(value: List<Attachment>?) {
        invalidateAttachments()
        attachmentList = value
    }

    fun invalidateAttachments() {
        Timber.i("invalidateAttachments")
        hasAttachments = null
        attachmentList = null
    }

    // remote urls

    private var remoteUrlList: List<RemoteUrl>? = null
    private var hasRemoteUrls: Boolean? = null

    @OneToMany(oneToManyMethods = [OneToManyMethod.ALL], variableName = "remoteUrlList")
    fun getRemoteUrls(): List<RemoteUrl>? {
        if (remoteUrlList == null) {
            remoteUrlList =
                (select().from(RemoteUrl::class).where(RemoteUrl_Table.remoteUrlEntryID_entryID.eq(entryID))
                    .and(
                        RemoteUrl_Table.remoteUrlVisible.eq(true)
                    )).list
        }
        return remoteUrlList
    }

    /**
     * Returns all the remote urls, not matter if they are visible or not
     * and doesn't cache the result
     */
    fun getAllRemoteUrls(): List<RemoteUrl>? {
        return (select from RemoteUrl::class where (RemoteUrl_Table.remoteUrlEntryID_entryID.eq(entryID))).list
    }

    fun hasRemoteUrls(): Boolean {
        hasRemoteUrls?.let {
            return it
        } ?: run {
            val result = selectCountOf(RemoteUrl_Table.remoteUrlEntryID_entryID)
                .from(RemoteUrl::class)
                .where(
                    RemoteUrl_Table.remoteUrlEntryID_entryID.eq(entryID)
                        .and(RemoteUrl_Table.remoteUrlVisible.eq(true))
                )
                .hasData(FlowManager.getDatabase(AppDatabase::class.java))
            hasRemoteUrls = result
            return result
        }
    }

    fun invalidateRemoteUrls() {
        Timber.i("invalidateRemoteUrls")
        hasRemoteUrls = null
        remoteUrlList = null
    }

    fun setRemoteUrlList(value: List<RemoteUrl>?) {
        invalidateRemoteUrls()
        remoteUrlList = value
    }

    override fun toString(): String {
        return "Entry(id=$entryID, title=$entryTitle, category=$category, pinned=$entryPinned, archived=$entryArchived, " +
                "deleted=$entryDeleted, priority=$entryPriority, modified=${entryModifiedDate.toEpochMilli()}, " +
                "attachments=${hasAttachments()})"
    }

    fun touch(): Entry {
        entryModifiedDate = Instant.now()
        return this
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
                        && attachmentList == other.attachmentList)
            }
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = entryID.hashCode()
        result = 31 * result + entryTitle.hashCode()
        result = 31 * result + entryText.hashCode()
        result = 31 * result + entryPriority
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + entryType.hashCode()
//        result = 31 * result + entryPinned
//        result = 31 * result + entryArchived
//        result = 31 * result + entryDeleted

        result = 31 * result + ((entryPinned shl 1) or (entryArchived shl 2) or (entryDeleted shl 3))
        result = 31 * result + entryCreationDate.hashCode()
        result = 31 * result + entryModifiedDate.hashCode()

        entryAlarm?.let { entryAlarm ->
            result = 31 * result + entryAlarm.hashCode()
        }

        result = 31 * result + (if (entryAlarmEnabled) 1 else 0)
        return result
    }

    fun getColor(context: Context): Int {
        return ResourceUtils.getCategoryColors(context)[category?.categoryColorIndex ?: 0]
    }

    fun isEmpty(): Boolean {
        val titleEmpty = entryTitle.isBlank()
        val hasAttachments = hasAttachmentsInternal()

        val textEmpty = if (entryType == EntryType.TEXT) {
            entryText.isBlank()
        } else {
            asList()?.first.isNullOrEmpty()
        }

        Timber.v("title=$titleEmpty, attachments=$hasAttachments, text=$textEmpty")

        return titleEmpty && !hasAttachments && textEmpty
    }

    fun isNew() = entryID == 0L
    fun isDeleted() = entryDeleted == 1
    fun isArchived() = entryArchived == 1
    fun isPinned() = entryPinned == 1
    fun hasReminder() = entryAlarmEnabled && entryAlarm != null

    fun isReminderExpired(): Boolean {
        return isReminderExpired(Instant.now())
    }

    fun isReminderExpired(now: Instant): Boolean {
        if (hasReminder()) {
            return entryAlarm!!.isBefore(now)
        }
        return true
    }

    @Suppress("unused")
    fun isEntryAlarmEnabled() = entryAlarmEnabled

    companion object {
        private fun getViewReminderPendingIntent(entry: Entry, context: Context): PendingIntent {
            return getReminderPendingIntent(entry, context, AlarmReceiver.ACTION_ENTRY_VIEW_REMINDER)
        }

        fun getDeleteReminderPendingIntent(entry: Entry, context: Context): PendingIntent {
            return getReminderPendingIntent(entry, context, AlarmReceiver.ACTION_ENTRY_REMOVE_REMINDER)
        }

        private fun getReminderPendingIntent(entry: Entry, context: Context, action: String): PendingIntent {
            return Intent(context, AlarmReceiver::class.java).let { intent ->
                intent.action = action
                intent.data = Uri.withAppendedPath(Uri.EMPTY, "entry/reminder/${entry.entryID}")
                PendingIntent.getBroadcast(context, 0, intent, 0)
            }
        }

        fun removeReminder(entry: Entry, context: Context) {
            Timber.i("removeReminder($entry)")
            val intent = getViewReminderPendingIntent(entry, context)
            val alarmManager = context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(intent)
        }

        fun addReminder(entry: Entry, context: Context): Boolean {
            if (entry.hasReminder()) {
                val millis = entry.entryAlarm!!.toEpochMilli()
                return addReminderAt(entry, context, millis)
            }
            return false
        }

        fun addReminderAt(entry: Entry, context: Context, millis: Long): Boolean {
            if (entry.hasReminder()) {
                Timber.i("addReminderAt($entry, $millis)")
                Timber.v("now=${System.currentTimeMillis()}")
                val alarmManager = context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager
                val pendingIntent = getViewReminderPendingIntent(entry, context)

                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    millis, pendingIntent
                )
                return true
            }
            return false
        }

        fun getLocalizedTime(date: Instant, format: FormatStyle): String? {
            val time = date.atZone(ZoneId.systemDefault())
            return time.format(DateTimeFormatter.ofLocalizedDateTime(format))
        }


        @Suppress("NAME_SHADOWING")
        fun fromString(text: String?): Entry {
            text?.let { text ->
                val jsonString = EntryIOUtils.convertStringToList(text)
                jsonString?.let { jsonString ->
                    return Entry().apply {
                        entryText = jsonString
                        entryType = EntryType.LIST
                    }
                } ?: run {
                    return Entry().apply {
                        entryText = text
                        entryType = EntryType.TEXT
                    }
                }
            } ?: run {
                return Entry()
            }
        }
    }

    enum class EntryType {
        TEXT, LIST
    }
}