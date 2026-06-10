package com.projectbluelight

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class UpcomingEvent(
    val eventId: Long,
    val title: String,
    val date: LocalDate,
    val daysUntil: Long,
)

// The one place that reads the phone's calendar, shared by the widget and the
// settings screen. Uses the Instances table so recurring events (Christmas,
// birthdays) expand into their actual next date instead of their original one.
object CalendarSource {

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
            val today = LocalDate.now()
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: continue
                val begin = cursor.getLong(1)
                val eventId = cursor.getLong(2)
                if (!seen.add(eventId)) continue
                val date = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDate()
                val days = ChronoUnit.DAYS.between(today, date)
                if (days >= 0) events.add(UpcomingEvent(eventId, title, date, days))
            }
        }
        return events
    }
}
