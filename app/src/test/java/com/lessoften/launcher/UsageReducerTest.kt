package com.lessoften.launcher

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UsageReducerTest {
    private val zone = ZoneId.of("UTC")
    private val start = Instant.parse("2026-10-30T23:59:00Z").toEpochMilli()
    @Test fun splitsMidnightAndExcludesGateImeAndInterruption() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.LOCKED)
        r.accept(start, UsageReducer.Event.UNLOCKED); r.accept(start, UsageReducer.Event.RESTRICTED); r.accept(start, UsageReducer.Event.GATE_CLOSED)
        r.accept(start + 30_000, UsageReducer.Event.IME)
        r.accept(start + 60_000, UsageReducer.Event.GATE_OPEN)
        r.accept(start + 90_000, UsageReducer.Event.GATE_CLOSED)
        r.accept(start + 100_000, UsageReducer.Event.INTERRUPTED)
        r.accept(start + 110_000, UsageReducer.Event.OTHER_APP)
        r.accept(start + 120_000, UsageReducer.Event.SCREEN_OFF)
        val first = r.snapshot().getValue(LocalDate.of(2026, 10, 30))
        val second = r.snapshot().getValue(LocalDate.of(2026, 10, 31))
        assertEquals(60_000, first.screenMillis); assertEquals(60_000, first.restrictedMillis); assertEquals(1, first.unlocks)
        assertEquals(60_000, second.screenMillis); assertEquals(10_000, second.restrictedMillis)
    }
    @Test fun duplicatesAndUnknownStateDoNotInventUnlocksOrUsage() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start + 1000, UsageReducer.Event.UNLOCKED)
        r.accept(start + 2000, UsageReducer.Event.UNLOCKED); r.accept(start + 3000, UsageReducer.Event.SCREEN_OFF)
        val d = r.snapshot().values.first()
        assertTrue(d.incomplete); assertEquals(0, d.unlocks); assertEquals(0, d.restrictedMillis)
    }
    @Test fun clockRollbackMarksAffectedDaysIncomplete() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start + 1000, UsageReducer.Event.LOCKED)
        r.accept(start - 1000, UsageReducer.Event.UNLOCKED)
        assertTrue(r.snapshot().values.all { it.incomplete })
    }
    @Test fun gapNeverResumesRestrictedAttributionWithoutGateCoverage() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.LOCKED)
        r.accept(start, UsageReducer.Event.UNLOCKED); r.accept(start, UsageReducer.Event.RESTRICTED)
        r.accept(start + 1_000, UsageReducer.Event.GAP)
        r.accept(start + 1_000, UsageReducer.Event.SCREEN_ON); r.accept(start + 1_000, UsageReducer.Event.UNLOCKED)
        r.accept(start + 1_000, UsageReducer.Event.RESTRICTED); r.accept(start + 10_000, UsageReducer.Event.TICK)
        assertEquals(0, r.snapshot().values.first().restrictedMillis)
    }
    @Test fun savedCursorResumesWithoutRecounting() {
        val first = UsageReducer(zone)
        first.accept(start, UsageReducer.Event.SCREEN_ON); first.accept(start, UsageReducer.Event.UNLOCKED)
        first.accept(start, UsageReducer.Event.OTHER_APP); first.accept(start + 10_000, UsageReducer.Event.TICK)
        val resumed = UsageReducer(zone).apply { restore(first.save()) }
        resumed.accept(start + 20_000, UsageReducer.Event.SCREEN_OFF)
        assertEquals(20_000, resumed.snapshot().values.first().screenMillis)
    }
    @Test fun replayFromCursorIncludesLateLockOnce() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.UNLOCKED)
        r.accept(start, UsageReducer.Event.OTHER_APP); r.accept(start + 10_000, UsageReducer.Event.TICK)
        val cursor = r.save()
        fun replay(): UsageReducer = UsageReducer(zone).apply {
            restore(cursor)
            accept(start + 15_000, UsageReducer.Event.LOCKED)
            accept(start + 20_000, UsageReducer.Event.TICK)
        }
        assertEquals(15_000, replay().snapshot().values.first().screenMillis)
        assertEquals(replay().snapshot(), replay().snapshot())
    }
    @Test fun screenOffAndLockedWakeNeverCountBackgroundTimeOrDuplicateUnlocks() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_OFF); r.accept(start, UsageReducer.Event.LOCKED)
        r.accept(start + 60_000, UsageReducer.Event.SCREEN_ON)
        r.accept(start + 70_000, UsageReducer.Event.UNLOCKED)
        r.accept(start + 71_000, UsageReducer.Event.UNLOCKED)
        r.accept(start + 90_000, UsageReducer.Event.SCREEN_OFF)
        r.accept(start + 150_000, UsageReducer.Event.TICK)
        val days = r.snapshot().values.toList()
        assertEquals(20_000, days.sumOf { it.screenMillis }); assertEquals(1, days.sumOf { it.unlocks })
    }
    @Test fun switchingAppsAndInterruptionsNeverExceedScreenTime() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.UNLOCKED)
        r.accept(start, UsageReducer.Event.RESTRICTED); r.accept(start, UsageReducer.Event.GATE_CLOSED)
        r.accept(start + 10_000, UsageReducer.Event.OTHER_APP)
        r.accept(start + 20_000, UsageReducer.Event.RESTRICTED)
        r.accept(start + 30_000, UsageReducer.Event.INTERRUPTED)
        r.accept(start + 40_000, UsageReducer.Event.RESTRICTED)
        r.accept(start + 50_000, UsageReducer.Event.GATE_OPEN)
        r.accept(start + 60_000, UsageReducer.Event.TICK)
        val day = r.snapshot().values.first()
        assertEquals(60_000, day.screenMillis); assertEquals(30_000, day.restrictedMillis)
        assertTrue(day.restrictedMillis <= day.screenMillis)
    }
    @Test fun unknownGateCoverageMarksRestrictedPartialWithoutInventingTime() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.UNLOCKED)
        r.accept(start, UsageReducer.Event.RESTRICTED); r.accept(start, UsageReducer.Event.GATE_CLOSED)
        r.accept(start + 10_000, UsageReducer.Event.COVERAGE_UNKNOWN)
        r.accept(start + 20_000, UsageReducer.Event.GATE_OPEN)
        r.accept(start + 30_000, UsageReducer.Event.TICK)
        val day = r.snapshot().values.first()
        assertEquals(10_000, day.restrictedMillis); assertTrue(day.restrictedIncomplete)
        assertFalse(day.screenIncomplete)
    }
    @Test fun knownBlockedCoverageDoesNotMakeRestrictedTimePartial() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.UNLOCKED)
        r.accept(start, UsageReducer.Event.GATE_OPEN)
        r.accept(start + 10_000, UsageReducer.Event.TICK)
        val day = r.snapshot().values.first()
        assertEquals(0, day.restrictedMillis); assertFalse(day.restrictedIncomplete)
    }
    @Test fun unresolvableForegroundIsPartialInsteadOfAssumedPermitted() {
        val r = UsageReducer(zone)
        r.accept(start, UsageReducer.Event.SCREEN_ON); r.accept(start, UsageReducer.Event.UNLOCKED)
        r.accept(start, UsageReducer.Event.GATE_CLOSED); r.accept(start, UsageReducer.Event.FOREGROUND_UNKNOWN)
        r.accept(start + 10_000, UsageReducer.Event.TICK)
        val day = r.snapshot().values.first()
        assertEquals(0, day.restrictedMillis); assertTrue(day.restrictedIncomplete)
    }
}
