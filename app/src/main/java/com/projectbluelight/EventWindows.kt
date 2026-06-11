package com.projectbluelight

import android.content.Context

// How far ahead each event becomes visible on the widget, in days.
// Stored per calendar event ID, so a recurring event has one setting
// that applies to every occurrence.
object EventWindows {

    // The slider stops. 0 means the event never shows on the widget.
    val PRESETS = intArrayOf(0, 1, 2, 3, 5, 7, 14, 21, 30, 60, 90, 180, 365)

    // Everything starts hidden. The widget is curated headspace — events only
    // earn a spot when you give them a window, so recurring noise (standups,
    // gym) never shows up uninvited.
    const val DEFAULT_DAYS = 0

    private fun prefs(context: Context) =
        context.getSharedPreferences("event_windows", Context.MODE_PRIVATE)

    fun daysFor(context: Context, eventId: Long): Int =
        prefs(context).getInt(eventId.toString(), DEFAULT_DAYS)

    fun setDays(context: Context, eventId: Long, days: Int) {
        prefs(context).edit().putInt(eventId.toString(), days).apply()
    }
}
