package com.projectbluelight

import org.junit.Assert.assertEquals
import org.junit.Test

class EventWindowsTest {

    // Smart defaults give doing-time, not just notice: shopping time for a
    // birthday, packing time for a trip, study time for an exam.
    @Test
    fun smartDefaultResolvesByKind() {
        assertEquals(14, EventWindows.resolveDefault(EventWindows.SMART, "Maya's birthday"))
        assertEquals(14, EventWindows.resolveDefault(EventWindows.SMART, "Sam & Riley's wedding"))
        assertEquals(7, EventWindows.resolveDefault(EventWindows.SMART, "Chem 101 Final"))
        assertEquals(7, EventWindows.resolveDefault(EventWindows.SMART, "Interview at Initech"))
        assertEquals(3, EventWindows.resolveDefault(EventWindows.SMART, "Flight to Denver"))
        assertEquals(1, EventWindows.resolveDefault(EventWindows.SMART, "Dentist"))
        assertEquals(1, EventWindows.resolveDefault(EventWindows.SMART, "Coffee with Sam"))
    }

    @Test
    fun plainDefaultPassesThroughUntouched() {
        assertEquals(0, EventWindows.resolveDefault(0, "Coffee with Sam"))
        assertEquals(1, EventWindows.resolveDefault(1, "Maya's birthday"))
        assertEquals(30, EventWindows.resolveDefault(30, "Flight to Denver"))
    }
}
