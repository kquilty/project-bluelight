package com.projectbluelight

import android.content.Context

// How far ahead each event becomes visible on the widget, in days.
// Stored per calendar event ID, so a recurring event has one setting
// that applies to every occurrence.
object EventWindows {

    // Everything starts hidden. The widget is curated headspace — events only
    // earn a spot when you give them a window, so recurring noise (standups,
    // gym) never shows up uninvited.
    const val DEFAULT_DAYS = 0

    // Sentinel for the "Smart" default: new events get a window suited to
    // what they are — birthdays get shopping time, exams get study time.
    const val SMART = -1

    // Sentinel window: visible only on the day it happens. Payday doesn't
    // need a heads-up; it needs a "today".
    const val DAY_OF = -2

    // The one visibility rule, shared by the widget and the app's sections.
    fun isVisible(window: Int, daysUntil: Long): Boolean = when {
        window == DAY_OF -> daysUntil == 0L
        window > 0 -> daysUntil <= window
        else -> false
    }

    // Settings share the windows file; event keys are numeric, so the
    // "setting:" prefix can never collide.
    private const val KEY_DEFAULT = "setting:default"
    private const val KEY_MUTED = "setting:muted"

    private fun prefs(context: Context) =
        context.getSharedPreferences("event_windows", Context.MODE_PRIVATE)

    fun daysFor(context: Context, eventId: Long): Int =
        prefs(context).getInt(eventId.toString(), DEFAULT_DAYS)

    fun setDays(context: Context, eventId: Long, days: Int) {
        prefs(context).edit().putInt(eventId.toString(), days).apply()
    }

    // Distinguishes "chose Hidden" (stored 0) from "never asked" (no entry).
    // Both stay off the widget; bulk-promote respects the explicit choice.
    fun isSet(context: Context, eventId: Long): Boolean =
        prefs(context).contains(eventId.toString())

    // ---------- Default window for new events ----------

    // What a never-asked event gets. 0 (Hidden) preserves the original
    // promote-by-hand behavior; SMART defers to the event's kind.
    fun defaultDays(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT, DEFAULT_DAYS)

    fun setDefaultDays(context: Context, days: Int) {
        prefs(context).edit().putInt(KEY_DEFAULT, days).apply()
    }

    // Whether the user has ever picked a default — drives the one-time
    // "choose a default" nudge on the main screen.
    fun isDefaultChosen(context: Context): Boolean =
        prefs(context).contains(KEY_DEFAULT)

    // The window an event actually has: the explicit choice if one exists,
    // otherwise the default (resolved per kind when the default is SMART).
    // A merged duplicate checks every underlying ID; if old per-copy choices
    // disagree, the most generous one wins until the next save aligns them.
    fun effectiveDaysFor(context: Context, event: UpcomingEvent): Int =
        explicitDaysFor(context, event) ?: resolveDefault(defaultDays(context), event.title)

    // The user's explicit choice across all of an event's calendar copies,
    // or null if they were never asked.
    fun explicitDaysFor(context: Context, event: UpcomingEvent): Int? =
        event.allIds.filter { isSet(context, it) }.maxOfOrNull { daysFor(context, it) }

    // Pure, so it's unit-testable alongside the smart table.
    fun resolveDefault(defaultDays: Int, title: String): Int =
        if (defaultDays == SMART) smartDays(Voice.kindOf(title)) else defaultDays

    // Lead time by kind: enough days to actually do the thing the event
    // implies — shop, pack, study — not just to know about it.
    fun smartDays(kind: Voice.Kind): Int = when (kind) {
        Voice.Kind.BIRTHDAY -> 14
        Voice.Kind.WEDDING -> 14
        Voice.Kind.EXAM -> 7
        Voice.Kind.INTERVIEW -> 7
        Voice.Kind.DEADLINE -> 7
        Voice.Kind.TRAVEL -> 3
        Voice.Kind.HEALTH -> 1
        Voice.Kind.PERFORMANCE -> 1
        Voice.Kind.GENERIC -> 1
    }

    // ---------- "Handled" answers to the voice ----------

    private const val KEY_HANDLED = "setting:handled"

    // "Gift sorted?" — answered. Stored as "eventId:epochDay" so the answer
    // expires with the occurrence: next year's birthday asks again.
    fun handledIds(context: Context, events: List<UpcomingEvent>): Set<Long> {
        val entries = prefs(context).getStringSet(KEY_HANDLED, emptySet())!!
        val dateById = events.associate { it.eventId to it.date.toEpochDay() }
        return entries.mapNotNull { entry ->
            val id = entry.substringBefore(":").toLongOrNull()
            val day = entry.substringAfter(":", "").toLongOrNull()
            if (id != null && day != null && dateById[id] == day) id else null
        }.toSet()
    }

    fun setHandled(context: Context, event: UpcomingEvent) {
        val today = CalendarSource.perceivedToday().toEpochDay()
        // Prune answers whose day has passed while we're here.
        val entries = prefs(context).getStringSet(KEY_HANDLED, emptySet())!!
            .filter { (it.substringAfter(":", "").toLongOrNull() ?: -1L) >= today }
            .toMutableSet()
        entries.add("${event.eventId}:${event.date.toEpochDay()}")
        prefs(context).edit().putStringSet(KEY_HANDLED, entries).apply()
    }

    // ---------- One-time hints ----------

    private const val KEY_SCRUB_HINT = "setting:scrub_hint_seen"

    // The scrub gesture is invisible until someone tells you. The tip line
    // shows until the first successful scrub, then never again.
    fun isScrubHintSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCRUB_HINT, false)

    fun setScrubHintSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_SCRUB_HINT, true).apply()
    }

    // ---------- Widget text size ----------

    private const val KEY_FONT = "setting:widget_font"

    // Multiplier on every widget font size (and the countdown column width,
    // so "Tomorrow" keeps fitting). 1.0 is the designed size.
    fun widgetFontScale(context: Context): Float =
        prefs(context).getFloat(KEY_FONT, 1f)

    fun setWidgetFontScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_FONT, scale).apply()
    }

    // ---------- Muted calendars ----------

    // Whole calendars (work spam, US Holidays) that never reach Bluelight.
    fun mutedCalendars(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_MUTED, emptySet())!!.map { it.toLong() }.toSet()

    fun setCalendarMuted(context: Context, calendarId: Long, muted: Boolean) {
        val next = mutedCalendars(context).toMutableSet()
        if (muted) next.add(calendarId) else next.remove(calendarId)
        // Always write a fresh set — mutating the one getStringSet returned
        // is undefined behavior in SharedPreferences.
        prefs(context).edit().putStringSet(KEY_MUTED, next.map { it.toString() }.toSet()).apply()
    }
}
