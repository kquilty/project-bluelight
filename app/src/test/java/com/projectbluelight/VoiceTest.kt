package com.projectbluelight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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

    // Within the week a friend names the day; the count would be colder.
    // (The fixture date, June 20 2026, is a Saturday.)
    @Test
    fun withinTheWeekSpeaksWeekdayNotCount() {
        val line = Voice.line(listOf(event("Chem 101 Final", 3)), noon)
        assertEquals("Chem 101 Final is Saturday. Little and often beats the all-nighter.", line)
    }

    @Test
    fun birthdayWithinTheWeekNamesTheDay() {
        val line = Voice.line(listOf(event("Maya's birthday", 4)), noon)
        assertEquals("Maya's birthday is Saturday. Gift sorted?", line)
    }

    // At 7 days the weekday collides with today's own — stay numeric.
    @Test
    fun sevenDaysOutStaysNumeric() {
        val line = Voice.line(listOf(event("Chem 101 Final", 7)), noon)
        assertEquals("7 days to Chem 101 Final. Little and often beats the all-nighter.", line)
    }

    @Test
    fun calmLineNamesTheDayWhenClose() {
        val line = Voice.line(listOf(event("Team offsite", 5)), noon)
        assertTrue(line, "Team offsite" in line && "Saturday" in line)
    }

    // After 9pm tomorrow leads the list; by day the soonest-first order holds.
    @Test
    fun eveningsBelongToTomorrow() {
        val events = listOf(event("Dentist", 0), event("Flight to Denver", 1), event("Riding", 3))
        val tenPm = LocalDateTime.of(2026, 6, 11, 22, 0)
        assertEquals(
            listOf("Flight to Denver", "Dentist", "Riding"),
            Voice.tonightOrder(events, tenPm).map { it.title },
        )
        assertEquals(
            listOf("Dentist", "Flight to Denver", "Riding"),
            Voice.tonightOrder(events, noon).map { it.title },
        )
    }

    // Sunday's calm comes with a receipt — proof the quiet was earned.
    @Test
    fun sundayCalmComesWithAReceipt() {
        val sunday = LocalDateTime.of(2026, 6, 14, 12, 0)
        assertEquals(
            "Four things came and went this week — all handled, no fires.",
            Voice.line(emptyList(), sunday, passedLastWeek = 4),
        )
        assertEquals(
            "One thing came and went this week — handled, no fuss.",
            Voice.line(emptyList(), sunday, passedLastWeek = 1),
        )
    }

    @Test
    fun recapWaitsForSundayAndNeverInterruptsUrgency() {
        // Thursday: no recap even with a busy week behind you.
        assertTrue("came and went" !in Voice.line(emptyList(), noon, passedLastWeek = 4))
        // Sunday with something due today: the day wins.
        val sunday = LocalDateTime.of(2026, 6, 14, 12, 0)
        val line = Voice.line(listOf(event("Dentist", 0)), sunday, passedLastWeek = 4)
        assertEquals("Dentist today — by lunch it's behind you.", line)
    }

    // "Today at 4", not "16:00" — the hour the way a friend says it.
    @Test
    fun clockSpeaksLikeAFriend() {
        assertEquals("4", Voice.clock(LocalTime.of(16, 0)))
        assertEquals("4:30", Voice.clock(LocalTime.of(16, 30)))
        assertEquals("9:05", Voice.clock(LocalTime.of(9, 5)))
        assertEquals("12", Voice.clock(LocalTime.of(12, 0)))
        assertEquals("12:15", Voice.clock(LocalTime.of(0, 15)))
    }
}
