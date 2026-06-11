package com.projectbluelight

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CalendarSourceTest {

    private fun event(
        id: Long,
        title: String,
        date: LocalDate = LocalDate.of(2026, 6, 20),
        time: LocalTime? = null,
    ) = UpcomingEvent(eventId = id, title = title, date = date, daysUntil = 9, time = time)

    // The same event on two calendars is one thing in your life.
    @Test
    fun sameTitleAndMomentMergesIntoOne() {
        val merged = CalendarSource.mergeDuplicates(
            listOf(
                event(1, "Father's Day"),
                event(2, "Father's Day"),
            )
        )
        assertEquals(1, merged.size)
        assertEquals(listOf(1L, 2L), merged.first().allIds)
        assertEquals(1L, merged.first().eventId)
    }

    @Test
    fun differentTimesAreDifferentEvents() {
        val merged = CalendarSource.mergeDuplicates(
            listOf(
                event(1, "Dentist", time = LocalTime.of(9, 0)),
                event(2, "Dentist", time = LocalTime.of(14, 0)),
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun differentDatesAreDifferentEvents() {
        val merged = CalendarSource.mergeDuplicates(
            listOf(
                event(1, "Riding", date = LocalDate.of(2026, 6, 20)),
                event(2, "Riding", date = LocalDate.of(2026, 6, 27)),
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun soonestFirstOrderSurvivesMerging() {
        val merged = CalendarSource.mergeDuplicates(
            listOf(
                event(1, "Physical", date = LocalDate.of(2026, 6, 15)),
                event(2, "Father's Day", date = LocalDate.of(2026, 6, 21)),
                event(3, "Physical", date = LocalDate.of(2026, 6, 15)),
            )
        )
        assertEquals(listOf("Physical", "Father's Day"), merged.map { it.title })
        assertEquals(listOf(1L, 3L), merged.first().allIds)
    }
}
