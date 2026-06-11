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

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
)

data class UpcomingEvent(
    val eventId: Long,
    val title: String,
    val date: LocalDate,
    val daysUntil: Long,
    // Start time, or null for all-day events (birthdays, holidays).
    val time: LocalTime? = null,
    // Every calendar event ID this entry stands for. More than one means the
    // same event lives on multiple calendars — shown once, window applies to all.
    val allIds: List<Long> = listOf(eventId),
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
    // meeting shows up once, on its next occurrence, not 52 times. Finished
    // events drop out: once today's 4pm ends, it stops being "Today at 4".
    fun upcomingEvents(context: Context): List<UpcomingEvent> {
        if (!hasPermission(context)) return emptyList()

        val now = System.currentTimeMillis()
        // Start the window a day back: all-day events are stored at UTC
        // midnight, so a [now, …] window would drop "today" for some timezones
        // before the day is over. The filters below handle anything stale.
        val windowStart = now - 24L * 60 * 60 * 1000
        val oneYearOut = now + 365L * 24 * 60 * 60 * 1000

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, windowStart)
        ContentUris.appendId(builder, oneYearOut)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
        )

        val muted = EventWindows.mutedCalendars(context)
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
                val end = cursor.getLong(4)
                val calendarId = cursor.getLong(5)
                if (calendarId in muted) continue
                // Mark seen only when an instance is actually kept — a skipped
                // (finished or stale) instance must not shadow the next one.
                if (eventId in seen) continue
                // A timed event that has ended is over, even if it's today.
                if (!allDay && end <= now) continue

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
                if (days < 0) continue
                seen.add(eventId)
                events.add(UpcomingEvent(eventId, title, date, days, time))
            }
        }
        return mergeDuplicates(events)
    }

    // The same title at the same moment on different calendars (yours and a
    // shared one, say) is one thing in your life, not several. Show it once;
    // the merged entry remembers every underlying ID so a window set on it
    // covers all the copies.
    internal fun mergeDuplicates(events: List<UpcomingEvent>): List<UpcomingEvent> =
        events.groupBy { Triple(it.title.trim(), it.date, it.time) }
            .values.map { dupes ->
                if (dupes.size == 1) dupes.first()
                else dupes.first().copy(allIds = dupes.flatMap { it.allIds })
            }

    // The phone's calendars, for the mute list in settings.
    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val result = ArrayList<CalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    CalendarInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1) ?: "Calendar",
                        account = cursor.getString(2) ?: "",
                    )
                )
            }
        }
        return result
    }
}
