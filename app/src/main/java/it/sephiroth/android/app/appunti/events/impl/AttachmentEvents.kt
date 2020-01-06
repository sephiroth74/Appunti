package it.sephiroth.android.app.appunti.events.impl

import it.sephiroth.android.app.appunti.db.tables.Attachment
import it.sephiroth.android.app.appunti.events.RxEvent

/**
 * Share attachment requested
 */
data class AttachmentOnShareEvent(val attachment: Attachment) : RxEvent()

/**
 * Delete attachment action Requested
 */
data class AttachmentOnDeleteEvent(val attachment: Attachment) : RxEvent()

/**
 * Attachment click event
 */
data class AttachmentOnClickEvent(val attachment: Attachment) : RxEvent()