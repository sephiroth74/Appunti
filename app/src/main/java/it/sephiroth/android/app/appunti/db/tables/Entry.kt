package it.sephiroth.android.app.appunti.db.tables

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.AlarmManagerCompat
import com.dbflow5.annotation.*
import com.dbflow5.query.select
import com.dbflow5.reactivestreams.structure.BaseRXModel
import com.dbflow5.structure.oneToMany
import it.sephiroth.android.app.appunti.AlarmReceiver
import it.sephiroth.android.app.appunti.db.AppDatabase
import it.sephiroth.android.app.appunti.db.EntryTypeConverter
import it.sephiroth.android.app.appunti.db.InstantTypeConverter
import it.sephiroth.android.app.appunti.utils.ResourceUtils
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import timber.log.Timber
import java.io.File

@Table(
    database = AppDatabase::class, indexGroups = [
        IndexGroup(number = 1, name = "firstIndex")
    ]
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
        attachments = other.attachments
        entryAlarm = other.entryAlarm
        entryAlarmEnabled = other.entryAlarmEnabled
    }

    @PrimaryKey(autoincrement = true)
    @Index(indexGroups = [1])
    var entryID: Int = 0

    @Column(defaultValue = "")
    var entryTitle: String = ""

    var entryPriority: Int = 5

    @ForeignKey(onDelete = ForeignKeyAction.SET_NULL)
    var category: Category? = null

    @Index(indexGroups = [1])
    @Column(typeConverter = InstantTypeConverter::class)
    var entryCreationDate: Instant = Instant.now()

    @Column(defaultValue = "")
    var entryText: String = ""

    @Column(typeConverter = EntryTypeConverter::class)
    var entryType: EntryType = EntryType.TEXT

    var entryPinned: Int = 0

    var entryArchived: Int = 0

    var entryDeleted: Int = 0

    @Index(indexGroups = [1])
    @Column(typeConverter = InstantTypeConverter::class)
    var entryModifiedDate: Instant = Instant.now()

    @Column(typeConverter = InstantTypeConverter::class)
    var entryAlarm: Instant? = null // UTC

    var entryAlarmEnabled: Boolean = false

    @get:OneToMany
    var attachments by oneToMany {
        select from Attachment::class where (Attachment_Table.attachmentEntryID_entryID.eq(
            entryID
        ))
    }

    override fun toString(): String {
        return "Entry(id=$entryID, title=$entryTitle, category=$category, pinned=$entryPinned, archived=$entryArchived, " +
                "deleted=$entryDeleted, priority=$entryPriority, modified=${entryModifiedDate.toEpochMilli()}, " +
                "attachments=$attachments)"
    }

    fun touch(): Entry {
        entryModifiedDate = Instant.now()
        return this
    }

    fun isEntryAlarmEnabled() = entryAlarmEnabled

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

    fun isNew() = entryID == 0
    fun isDeleted() = entryDeleted == 1
    fun isArchived() = entryArchived == 1
    fun isPinned() = entryPinned == 1
    fun hasAlarm() = entryAlarmEnabled && entryAlarm != null

    fun isAlarmExpired(): Boolean {
        return isAlarmExpired(Instant.now())
    }

    fun isAlarmExpired(now: Instant): Boolean {
        if (hasAlarm()) {
            return entryAlarm!!.isBefore(now)
        }
        return true
    }

    fun getTextSummary(maxLength: Int = 100, postfix: String? = null): String {
        return if (entryText.length <= maxLength) entryText
        else entryText.substring(0, maxLength) + postfix
    }

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
            if (entry.hasAlarm()) {
                val millis = entry.entryAlarm!!.toEpochMilli()
                return addReminderAt(entry, context, millis)
            }
            return false
        }

        fun addReminderAt(entry: Entry, context: Context, millis: Long): Boolean {
            if (entry.hasAlarm()) {
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

    }

    enum class EntryType {
        TEXT, LIST
    }
}