package com.lessoften.launcher

import org.junit.Assert.*
import org.junit.Test

class UsageComparisonTest {
    private fun c(value: Long, complete: Boolean = true) = UsageComparison.Checkpoint(value, complete)
    @Test fun requiresThreeCompleteDaysAtCompletedBoundary() {
        val today = mapOf(60 to c(600_000))
        val past = listOf(mapOf(60 to c(300_000)), mapOf(60 to c(600_000)), mapOf(60 to c(900_000)), mapOf(60 to c(0, false)))
        val result = UsageComparison.compare(64, today, past)!!
        assertEquals(60, result.cutoffMinute); assertEquals(3, result.baselineDays); assertEquals("About usual by now", result.label())
        assertNull(UsageComparison.compare(64, today, past.take(2)))
        assertNull(UsageComparison.compare(64, mapOf(60 to c(600_000, false)), past))
        assertEquals(60, UsageComparison.compare(65, today, past)!!.cutoffMinute)
    }
    @Test fun excludesDaysBeyondSevenAndUsesNeutralLanguage() {
        val past = List(7) { mapOf(60 to c(600_000)) } + mapOf(60 to c(999_999_999))
        assertEquals("2m more than usual by now", UsageComparison.compare(60, mapOf(60 to c(720_000)), past)!!.label())
        assertEquals("2m less than usual by now", UsageComparison.compare(60, mapOf(60 to c(480_000)), past)!!.label())
    }
    @Test fun daylightSavingDaysAreNotComparisonDays() {
        val zone = java.time.ZoneId.of("America/New_York")
        assertFalse(UsageComparison.isNormalDay(java.time.LocalDate.of(2026, 3, 8), zone))
        assertFalse(UsageComparison.isNormalDay(java.time.LocalDate.of(2026, 11, 1), zone))
        assertTrue(UsageComparison.isNormalDay(java.time.LocalDate.of(2026, 10, 30), zone))
    }
}
