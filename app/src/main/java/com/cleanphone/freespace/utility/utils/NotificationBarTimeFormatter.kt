package com.cleanphone.freespace.utility.utils

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.cleanphone.freespace.utility.R

import java.util.Date

object NotificationBarTimeFormatter {
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    internal sealed interface RelativeTime {
        data object JustNow : RelativeTime
        data class Minutes(val count: Int) : RelativeTime
        data class Hours(val count: Int) : RelativeTime
        data class Days(val count: Int) : RelativeTime
        data class CalendarDate(val timestamp: Long) : RelativeTime
    }

    internal fun resolveRelativeTime(
        timestamp: Long,
        now: Long = System.currentTimeMillis()
    ): RelativeTime {
        val delta = (now - timestamp).coerceAtLeast(0L)
        return when {
            delta < MINUTE_MILLIS -> RelativeTime.JustNow
            delta < HOUR_MILLIS -> RelativeTime.Minutes((delta / MINUTE_MILLIS).toInt())
            delta < DAY_MILLIS -> RelativeTime.Hours((delta / HOUR_MILLIS).toInt())
            delta < 7 * DAY_MILLIS -> RelativeTime.Days((delta / DAY_MILLIS).toInt())
            else -> RelativeTime.CalendarDate(timestamp)
        }
    }

    fun formatRelativeTime(
        context: Context,
        timestamp: Long,
        now: Long = System.currentTimeMillis()
    ): String = when (val relativeTime = resolveRelativeTime(timestamp, now)) {
        RelativeTime.JustNow -> context.getString(R.string.notification_time_just_now)
        is RelativeTime.Minutes -> context.resources.getQuantityString(
            R.plurals.notification_time_minutes_ago,
            relativeTime.count,
            relativeTime.count
        )
        is RelativeTime.Hours -> context.resources.getQuantityString(
            R.plurals.notification_time_hours_ago,
            relativeTime.count,
            relativeTime.count
        )
        is RelativeTime.Days -> context.resources.getQuantityString(
            R.plurals.notification_time_days_ago,
            relativeTime.count,
            relativeTime.count
        )
        is RelativeTime.CalendarDate -> DateUtils.formatDateTime(
            context,
            relativeTime.timestamp,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        )
    }

    fun formatClockTime(context: Context, timestamp: Long): String =
        DateFormat.getTimeFormat(context).format(Date(timestamp))
}
