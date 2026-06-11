package com.projectbluelight

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

// Bluelight's voice: one quiet line that proves the widget understands what
// it's looking at. A calendar event isn't data — it's stakes. A rehearsal
// means nerves, a flight means packing, a birthday means a gift you haven't
// bought yet. The voice reads the kind of event and the time of day, and says
// the thing a thoughtful friend would say. It speaks only about events the
// user has promoted into view — it never nags about things they chose to hide.
//
// Pure Kotlin on purpose: no Android imports, so every line is unit-testable.
object Voice {

    enum class Kind { BIRTHDAY, TRAVEL, INTERVIEW, EXAM, HEALTH, WEDDING, PERFORMANCE, DEADLINE, GENERIC }

    // Checked in order — "birthday party" is a birthday, not a party.
    private val KIND_KEYWORDS = listOf(
        Kind.BIRTHDAY to listOf("birthday", "bday", "b-day"),
        Kind.WEDDING to listOf("wedding"),
        Kind.INTERVIEW to listOf("interview"),
        Kind.EXAM to listOf("exam", "midterm", "final", "quiz", "test"),
        Kind.TRAVEL to listOf("flight", "airport", "fly to", "depart", "trip", "vacation"),
        Kind.HEALTH to listOf("dentist", "doctor", "dr.", "dr ", "checkup", "check-up", "surgery", "vet", "appointment"),
        Kind.PERFORMANCE to listOf("rehearsal", "audition", "recital", "concert", "gig", "skit", "performance", "show"),
        Kind.DEADLINE to listOf("deadline", "due", "submit", "submission"),
    )

    // "4", "4:30" — the hour the way a friend says it, not a timestamp.
    // You know whether your own appointment is morning or afternoon.
    fun clock(time: LocalTime): String {
        val h = ((time.hour + 11) % 12) + 1
        return if (time.minute == 0) "$h" else "$h:${"%02d".format(time.minute)}"
    }

    // "Saturday" for events 2–6 days out: close enough that the weekday is
    // unambiguous, and a friend says "is Saturday", not "in 3 days". At 7 days
    // the weekday collides with today's own, so the count takes over. Null
    // means: use the number.
    private fun weekday(e: UpcomingEvent): String? =
        if (e.daysUntil in 2L..6L) e.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        else null

    fun kindOf(title: String): Kind {
        val t = title.lowercase()
        for ((kind, words) in KIND_KEYWORDS) {
            if (words.any { it in t }) return kind
        }
        return Kind.GENERIC
    }

    // The one line for right now. `events` must be the in-view list, soonest first.
    fun line(events: List<UpcomingEvent>, now: LocalDateTime = LocalDateTime.now()): String {
        val today = events.firstOrNull { it.daysUntil == 0L }
        val tomorrow = events.firstOrNull { it.daysUntil == 1L }
        val lateNight = now.hour < CalendarSource.DAY_ROLLOVER_HOUR

        if (lateNight) {
            if (tomorrow != null) return lateNightLine(tomorrow, hourLabel(now.hour))
            if (today != null) return "Tonight, for real: ${today.title}."
        }
        if (today != null) return todayLine(today)
        if (tomorrow != null) return tomorrowLine(tomorrow)
        events.firstNotNullOfOrNull { approachLine(it) }?.let { return it }
        return ambientLine(events, now)
    }

    private fun hourLabel(hour: Int) = if (hour == 0) "midnight" else "${hour}am"

    // You're awake when you shouldn't be, and tomorrow has plans for you.
    private fun lateNightLine(e: UpcomingEvent, h: String): String = when (kindOf(e.title)) {
        Kind.PERFORMANCE -> "It's $h. ${e.title} is tomorrow — rest is part of the performance."
        Kind.INTERVIEW -> "It's $h. ${e.title} will go better rested. Lights out."
        Kind.EXAM -> "Cramming at $h costs more than it pays. ${e.title} needs you sharp."
        Kind.TRAVEL -> "Still up at $h? ${e.title} is tomorrow. Airports forgive nothing."
        Kind.BIRTHDAY -> "Up at $h? ${e.title} is tomorrow — be the rested one at the party."
        Kind.DEADLINE -> "It's $h. ${e.title} lands tomorrow — sleep now, finish strong."
        else -> "It's $h. ${e.title} comes tomorrow — it'd rather meet you rested."
    }

    private fun todayLine(e: UpcomingEvent): String = when (kindOf(e.title)) {
        Kind.BIRTHDAY -> "${e.title} — today. Make the call, not just the text."
        Kind.TRAVEL -> "${e.title} today. Leave earlier than feels necessary."
        Kind.INTERVIEW -> "${e.title} today. You know your stuff — let them see it."
        Kind.EXAM -> "${e.title} today. Breathe. You've done the work."
        Kind.HEALTH -> "${e.title} today — by lunch it's behind you."
        Kind.WEDDING -> "${e.title} today. Charge your phone; photos happen."
        Kind.PERFORMANCE -> "${e.title} today. Nerves just mean you care. Go."
        Kind.DEADLINE -> "${e.title} due today. Done beats perfect — ship it."
        Kind.GENERIC -> "Today: ${e.title}."
    }

    private fun tomorrowLine(e: UpcomingEvent): String = when (kindOf(e.title)) {
        Kind.BIRTHDAY -> "${e.title} tomorrow. Card, gift, or words — pick one tonight."
        Kind.TRAVEL -> "${e.title} tomorrow. Pack tonight; morning-you packs badly."
        Kind.INTERVIEW -> "${e.title} tomorrow. Clothes out, route checked, early night."
        Kind.EXAM -> "${e.title} tomorrow. Sleep is studying too."
        Kind.HEALTH -> "${e.title} tomorrow — future you is already grateful."
        Kind.WEDDING -> "${e.title} tomorrow. Iron tonight, dance tomorrow."
        Kind.PERFORMANCE -> "${e.title} tomorrow. Run it once in your head, then rest."
        Kind.DEADLINE -> "${e.title} due tomorrow. An hour tonight is worth three tomorrow."
        Kind.GENERIC -> "${e.title} tomorrow. Tonight is for getting ahead of it."
    }

    // Further out, only kinds with a real preparation task get a nudge —
    // anything else would just be noise.
    private fun approachLine(e: UpcomingEvent): String? {
        val n = e.daysUntil
        // Within the week, name the day; past that, count.
        val lead = weekday(e)?.let { "${e.title} is $it." } ?: "$n days to ${e.title}."
        val dueLead = weekday(e)?.let { "${e.title} lands $it." } ?: "${e.title} lands in $n days."
        return when (kindOf(e.title)) {
            Kind.BIRTHDAY -> if (n in 2..21) "$lead Gift sorted?" else null
            Kind.TRAVEL -> if (n in 2..3) "$lead The good packing happens early." else null
            Kind.WEDDING -> if (n in 2..14) "$lead Outfit, gift, RSVP — all set?" else null
            Kind.EXAM -> if (n in 2..7) "$lead Little and often beats the all-nighter." else null
            Kind.INTERVIEW -> if (n in 2..7) "$lead One good story beats ten facts." else null
            Kind.DEADLINE -> if (n in 2..7) "$dueLead Start ugly, finish early." else null
            else -> null
        }
    }

    // Nothing needs saying — say something calm. Rotates daily so the widget
    // feels alive without ever feeling random.
    private fun ambientLine(events: List<UpcomingEvent>, now: LocalDateTime): String {
        val day = now.dayOfYear
        if (events.isEmpty()) {
            val calm = listOf(
                "All clear. The quiet is the feature.",
                "Nothing in view. Exactly as designed.",
                "All quiet. Go live your day.",
            )
            return calm[day % calm.size]
        }
        val next = events.first()
        val whenBit = weekday(next)?.let { "on $it" } ?: "in ${next.daysUntil} days"
        val steady = listOf(
            "Nothing urgent. ${next.title} leads, $whenBit.",
            "All steady. Next up: ${next.title}, $whenBit.",
            "No fires. ${next.title} arrives $whenBit.",
        )
        return steady[day % steady.size]
    }
}
