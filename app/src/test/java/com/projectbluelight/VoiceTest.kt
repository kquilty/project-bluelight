package com.projectbluelight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VoiceTest {

    private fun event(title: String, daysUntil: Long) =
        UpcomingEvent(eventId = daysUntil + 1, title = title, date = LocalDate.of(2026, 6, 20), daysUntil = daysUntil)

    private val oneAm: LocalDateTime = LocalDateTime.of(2026, 6, 11, 1, 30)
    private val noon: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)

    // The founding scenario: up past midnight, rehearsal "tomorrow".
    @Test
    fun lateNightBeforeAPerformanceTellsYouToRest() {
        val line = Voice.line(listOf(event("Skit rehearsal", 1)), oneAm)
        assertEquals("It's 1am. Skit rehearsal is tomorrow — rest is part of the performance.", line)
    }

    @Test
    fun lateNightGenericStillSaysSomething() {
        val line = Voice.line(listOf(event("Coffee with Sam", 1)), oneAm)
        assertTrue(line, line.startsWith("It's 1am."))
    }

    @Test
    fun midnightReadsAsMidnightNotZeroAm() {
        val line = Voice.line(listOf(event("Skit rehearsal", 1)), LocalDateTime.of(2026, 6, 11, 0, 10))
        assertTrue(line, "midnight" in line)
    }

    @Test
    fun eventLaterTonightWhileUpLate() {
        val line = Voice.line(listOf(event("Airport run", 0)), oneAm)
        assertEquals("Tonight, for real: Airport run.", line)
    }

    @Test
    fun todayBeatsTomorrowWhenBothExist() {
        val line = Voice.line(listOf(event("Dentist", 0), event("Final exam", 1)), noon)
        assertEquals("Dentist today — by lunch it's behind you.", line)
    }

    @Test
    fun birthdayApproachAsksAboutTheGift() {
        val line = Voice.line(listOf(event("Maya's birthday", 12)), noon)
        assertEquals("12 days to Maya's birthday. Gift sorted?", line)
    }

    @Test
    fun farOffGenericEventsGetCalmNotNagging() {
        val line = Voice.line(listOf(event("Team offsite", 40)), noon)
        assertTrue(line, "Team offsite" in line && "40" in line)
    }

    @Test
    fun emptyViewIsCalm() {
        val line = Voice.line(emptyList(), noon)
        assertTrue(line, line.isNotBlank())
    }

    @Test
    fun kindDetectionReadsRealTitles() {
        assertEquals(Voice.Kind.PERFORMANCE, Voice.kindOf("Skit rehearsal"))
        assertEquals(Voice.Kind.BIRTHDAY, Voice.kindOf("Maya's Birthday Party"))
        assertEquals(Voice.Kind.TRAVEL, Voice.kindOf("Flight to Denver"))
        assertEquals(Voice.Kind.EXAM, Voice.kindOf("Chem 101 Final"))
        assertEquals(Voice.Kind.HEALTH, Voice.kindOf("Dentist"))
        assertEquals(Voice.Kind.DEADLINE, Voice.kindOf("Taxes due"))
        assertEquals(Voice.Kind.GENERIC, Voice.kindOf("Coffee with Sam"))
    }

    // After 4am you're in the new day; the late-night tone must be gone.
    @Test
    fun fourAmIsMorningNotLateNight() {
        val line = Voice.line(listOf(event("Skit rehearsal", 1)), LocalDateTime.of(2026, 6, 11, 4, 1))
        assertEquals("Skit rehearsal tomorrow. Run it once in your head, then rest.", line)
    }
}
