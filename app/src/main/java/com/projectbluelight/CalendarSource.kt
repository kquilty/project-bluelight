package com.projectbluelight

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class UpcomingEvent(
    val eventId: Long,
    val title: String,
    val date: LocalDate,
    val daysUntil: Long,
    // Start time, or null for all-day events (birthdays, holidays).
    val time: LocalTime? = null,
)

// The one place that reads the phone's calendar, shared by the widget and the
// settings screen. Uses the Instances table so recurring events (Christmas,
// birthdays) expand into their actual next date instead of their original one.
object CalendarSource {

    // The day doesn't roll over at midnight — nobody's "tomorrow" starts at
    // 12:01am. Until this hour, you're still living in yesterday: at 1am, an
    // event later today reads "Tomorrow", and an event at 2am is "Today"
    // (it's tonight). Everything (now and event times) is shifted back by
    // this many hours before comparing dates.
    const val DAY_ROLLOVER_HOUR = 4L

    fun perceivedToday(): LocalDate =
        LocalDateTime.now().minusHours(DAY_ROLLOVER_HOUR).toLocalDate()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    // The next year of events, soonest first, one entry per event — a weekly
    // meeting shows up once, on its next occurrence, not 52 times.
    fun upcomingEvents(context: Context): List<UpcomingEvent> {
        if (!hasPermission(context)) return emptyList()

        val now = System.currentTimeMillis()
        val oneYearOut = now + 365L * 24 * 60 * 60 * 1000

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, oneYearOut)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.ALL_DAY,
        )

        val events = ArrayList<UpcomingEvent>()
        val seen = HashSet<Long>()
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            val today = perceivedToday()
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: continue
                val begin = cursor.getLong(1)
                val eventId = cursor.getLong(2)
                val allDay = cursor.getInt(3) == 1
                if (!seen.add(eventId)) continue

                // All-day events (birthdays, holidays) are stored as UTC
                // midnight — read them as UTC or they land a day off. They're
                // date-only, so the rollover shift doesn't apply: your niece's
                // birthday on the 14th is the 14th.
                val date: LocalDate
                val perceivedDate: LocalDate
                val time: LocalTime?
                if (allDay) {
                    date = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    perceivedDate = date
                    time = null
                } else {
                    val local = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    date = local.toLocalDate()
                    perceivedDate = local.minusHours(DAY_ROLLOVER_HOUR).toLocalDate()
                    time = local.toLocalTime()
                }

                val days = ChronoUnit.DAYS.between(today, perceivedDate)
                if (days >= 0) events.add(UpcomingEvent(eventId, title, date, days, time))
            }
        }
        return events
    }
}
