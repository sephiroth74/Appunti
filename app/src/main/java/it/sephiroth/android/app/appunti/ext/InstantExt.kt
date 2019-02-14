package it.sephiroth.android.app.appunti.ext

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle

/**
 * Convert the Instant to a ZonedDateTime with the current
 * system ZoneId and return a formatted string with the given format
 */
fun Instant.getLocalizedDateTimeStamp(format: FormatStyle): String {
    val time = this.atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofLocalizedDateTime(format))
}