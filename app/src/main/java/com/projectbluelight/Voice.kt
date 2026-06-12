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

    // After dark the day flips: what's left of today is mostly done, and
    // tomorrow is what you can still do something about. From 9pm to the 4am
    // rollover, tomorrow's events lead the widget and today's step back.
    fun isTonight(now: LocalDateTime = LocalDateTime.now()): Boolean =
        now.hour >= 21 || now.hour < CalendarSource.DAY_ROLLOVER_HOUR

    fun tonightOrder(
        events: List<UpcomingEvent>,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<UpcomingEvent> =
        if (!isTonight(now)) events
        else events.sortedBy { if (it.daysUntil == 1L) -1L else it.daysUntil }

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

    // A spoken line that knows what it's about. `answerable` means the line
    // asks something the user can settle with one tap ("Gift sorted?").
    data class Utterance(
        val text: String,
        val subjectId: Long? = null,
        val answerable: Boolean = false,
    )

    // The one line for right now — or silence. The voice speaks only when it
    // adds something the list below it can't say: kind-specific advice, a
    // late-night nudge toward bed, an answerable question, the Sunday recap.
    // "Today: Riding." above a row reading "Today  Riding" is just an echo,
    // and echoes are noise. `events` must be the in-view list, soonest first.
    fun line(
        events: List<UpcomingEvent>,
        now: LocalDateTime = LocalDateTime.now(),
        passedLastWeek: Int = 0,
        handled: Set<Long> = emptySet(),
    ): String = utterance(events, now, passedLastWeek, handled)?.text ?: ""

    fun utterance(
        events: List<UpcomingEvent>,
        now: LocalDateTime = LocalDateTime.now(),
        passedLastWeek: Int = 0,
        handled: Set<Long> = emptySet(),
    ): Utterance? {
        val today = events.firstOrNull { it.daysUntil == 0L }
        val tomorrow = events.firstOrNull { it.daysUntil == 1L }
        val lateNight = now.hour < CalendarSource.DAY_ROLLOVER_HOUR

        if (lateNight) {
            // At 1am even a generic event earns a word — the advice is sleep.
            if (tomorrow != null) return Utterance(lateNightLine(tomorrow, hourLabel(now.hour)), tomorrow.eventId)
            if (today != null) return Utterance("Tonight, for real: ${today.title}.", today.eventId)
        }
        // Generic events fall through, not silent over — a generic today with
        // a flight tomorrow still gets "pack tonight".
        today?.takeIf { kindOf(it.title) != Kind.GENERIC }?.let {
            return Utterance(todayLine(it), it.eventId)
        }
        tomorrow?.takeIf { kindOf(it.title) != Kind.GENERIC }?.let {
            return tomorrowUtterance(it, it.eventId in handled)
        }
        events.firstNotNullOfOrNull { approachUtterance(it, it.eventId in handled) }?.let { return it }
        if (now.dayOfWeek == java.time.DayOfWeek.SUNDAY && passedLastWeek > 0) {
            return Utterance(recapLine(passedLastWeek))
        }
        return null
    }

    // "Four", for the recap — counts read warmer as words.
    private val COUNT_WORDS =
        listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")

    private fun countWord(n: Int): String = COUNT_WORDS.getOrNull(n) ?: "$n"

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

    // Tomorrow-eve prep that can be marked done; once handled, the voice
    // stops asking and starts reassuring.
    private val ANSWERABLE_TOMORROW = setOf(Kind.BIRTHDAY, Kind.TRAVEL, Kind.INTERVIEW)

    private fun tomorrowUtterance(e: UpcomingEvent, isHandled: Boolean): Utterance {
        val kind = kindOf(e.title)
        if (isHandled) {
            val text = when (kind) {
                Kind.BIRTHDAY -> "${e.title} tomorrow. Gift's ready — sleep easy."
                Kind.TRAVEL -> "${e.title} tomorrow. Bags packed — morning-you says thanks."
                Kind.INTERVIEW -> "${e.title} tomorrow. All prepped — early night anyway."
                else -> tomorrowLine(e)
            }
            return Utterance(text, e.eventId)
        }
        return Utterance(tomorrowLine(e), e.eventId, answerable = kind in ANSWERABLE_TOMORROW)
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
    // anything else would just be noise. Birthday and wedding nudges are
    // questions, so they're answerable; once handled, the voice relaxes.
    private fun approachUtterance(e: UpcomingEvent, isHandled: Boolean): Utterance? {
        val n = e.daysUntil
        // Within the week, name the day; past that, count.
        val lead = weekday(e)?.let { "${e.title} is $it." } ?: "$n days to ${e.title}."
        val dueLead = weekday(e)?.let { "${e.title} lands $it." } ?: "${e.title} lands in $n days."
        return when (kindOf(e.title)) {
            Kind.BIRTHDAY -> if (n in 2..21) {
                if (isHandled) Utterance("$lead Gift's handled — just show up.", e.eventId)
                else Utterance("$lead Gift sorted?", e.eventId, answerable = true)
            } else null
            Kind.WEDDING -> if (n in 2..14) {
                if (isHandled) Utterance("$lead All set — just bring the dancing.", e.eventId)
                else Utterance("$lead Outfit, gift, RSVP — all set?", e.eventId, answerable = true)
            } else null
            Kind.TRAVEL -> if (n in 2..3) Utterance("$lead The good packing happens early.", e.eventId) else null
            Kind.EXAM -> if (n in 2..7) Utterance("$lead Little and often beats the all-nighter.", e.eventId) else null
            Kind.INTERVIEW -> if (n in 2..7) Utterance("$lead One good story beats ten facts.", e.eventId) else null
            Kind.DEADLINE -> if (n in 2..7) Utterance("$dueLead Start ugly, finish early.", e.eventId) else null
            else -> null
        }
    }

    // Sunday's calm earns a receipt: the week's watched events that came and
    // went without fuss. Every other quiet moment stays actually quiet.
    private fun recapLine(passedLastWeek: Int): String =
        if (passedLastWeek == 1) "One thing came and went this week — handled, no fuss."
        else "${countWord(passedLastWeek)} things came and went this week — all handled, no fires."
}
