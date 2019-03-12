package it.sephiroth.android.app.appunti.utils

import it.sephiroth.android.app.appunti.db.tables.Entry

object EntryDiff {
    data class Result(
        var sameID: Boolean,
        var archivedChanged: Boolean = true,
        var deletedChanged: Boolean = true,
        var pinnedChanged: Boolean = true,
        var titleChanged: Boolean = true,
        var textChanged: Boolean = true,
        var categoryChanged: Boolean = true,
        var priorityChanged: Boolean = true,
        var modifiedDateChanged: Boolean = true,
        var attachmentsChanged: Boolean = true,
        var typeChanged: Boolean = true,
        var alarmChanged: Boolean = true
    )

    fun calculateDiff(oldValue: Entry?, newValue: Entry?): Result {
        return if (isSameItem(oldValue, newValue)) {
            calculateContentDiff(oldValue, newValue)
        } else {
            Result(false)
        }
    }

    private fun isSameItem(oldValue: Entry?, newValue: Entry?): Boolean {
        return oldValue?.entryID == newValue?.entryID
    }

    private fun calculateContentDiff(oldValue: Entry?, newValue: Entry?): Result {
        return Result(
            sameID = true,
            archivedChanged = oldValue?.entryArchived != newValue?.entryArchived,
            deletedChanged = oldValue?.entryDeleted != newValue?.entryDeleted,
            pinnedChanged = oldValue?.entryPinned != newValue?.entryPinned,
            titleChanged = oldValue?.entryTitle != newValue?.entryTitle,
            textChanged = oldValue?.entryText != newValue?.entryText,
            categoryChanged = oldValue?.category != newValue?.category,
            priorityChanged = oldValue?.entryPriority != newValue?.entryPriority,
            modifiedDateChanged = oldValue?.entryModifiedDate != newValue?.entryModifiedDate,
            attachmentsChanged = oldValue?.getAttachments() != newValue?.getAttachments(),
            typeChanged = oldValue?.entryType != newValue?.entryType,
            alarmChanged = oldValue?.entryAlarmEnabled != newValue?.entryAlarmEnabled || oldValue?.entryAlarm != newValue?.entryAlarm
        )

    }
}