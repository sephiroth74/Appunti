package it.sephiroth.android.app.appunti.ext

import android.content.Context
import it.sephiroth.android.app.appunti.R
import org.threeten.bp.LocalDate
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle

fun ZonedDateTime.isToday() = this.toLocalDate() == LocalDate.now(this.zone)

fun ZonedDateTime.isTomorrow() = this.toLocalDate() == LocalDate.now(this.zone).plusDays(1)

fun ZonedDateTime.isYesterday() = this.toLocalDate() == LocalDate.now(this.zone).minusDays(1)

fun ZonedDateTime.formatDiff(context: Context, dateStyle: FormatStyle, timeStyle: FormatStyle): String {
    return if (isToday()) {
        context.getString(R.string.today_at, this.format(DateTimeFormatter.ofLocalizedTime(timeStyle)))
    } else if (isYesterday()) {
        context.getString(R.string.yesterday_at, this.format(DateTimeFormatter.ofLocalizedTime(timeStyle)))
    } else if (isTomorrow()) {
        context.getString(R.string.tomorrow_at, this.format(DateTimeFormatter.ofLocalizedTime(timeStyle)))
    } else {
        this.format(DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle))
    }
}