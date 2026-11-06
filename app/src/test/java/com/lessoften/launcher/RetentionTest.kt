package com.lessoften.launcher

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RetentionTest {
    @Test fun keepsTodayAndSevenPriorDaysAndPreservesFutureDates() {
        val today = LocalDate.of(2026, 10, 30)
        assertFalse(Retention.expired("day:2026-10-23|America/New_York", "day:", today))
        assertTrue(Retention.expired("day:2026-10-22|America/New_York", "day:", today))
        assertFalse(Retention.expired("day:2026-11-02|UTC", "day:", today))
        assertTrue(Retention.expired("day:bad", "day:", today))
    }
}
