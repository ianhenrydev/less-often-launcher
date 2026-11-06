package com.lessoften.launcher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UsageSegmentTest {
    @Test fun preservesPreviousTotalsAndAddsOnlyObservedPostGapIntervals() {
        val base = JSONObject().put("screen", 10_000).put("unlocks", 2).put("restricted", 3_000)
        val part = UsageReducer.Day(screenMillis = 5_000, restrictedMillis = 1_000, unlocks = 1)
        val result = UsageSegment.merge(base, part, true, JSONObject(), false)
        assertEquals(15_000, result.getLong("screen")); assertEquals(4_000, result.getLong("restricted"))
        assertEquals(3, result.getInt("unlocks")); assertFalse(result.getBoolean("completeThroughNow"))
    }
    @Test fun unknownRestrictedCoverageDoesNotBecomeZero() {
        val result = UsageSegment.merge(JSONObject(), UsageReducer.Day(), false, JSONObject(), false)
        assertFalse(result.has("restricted"))
    }
    @Test fun repeatedReconciliationUsesTheSameBaseWithoutDoubleCounting() {
        val base = JSONObject().put("screen", 10_000).put("unlocks", 2).put("restricted", 3_000)
        val first = UsageSegment.merge(base, UsageReducer.Day(screenMillis = 5_000, restrictedMillis = 1_000), true, JSONObject(), false)
        val replay = UsageSegment.merge(base, UsageReducer.Day(screenMillis = 5_000, restrictedMillis = 1_000), true, JSONObject(), false)
        assertEquals(first.toString(), replay.toString())
        assertEquals(15_000, replay.getLong("screen"))
        assertEquals(4_000, replay.getLong("restricted"))
    }
    @Test fun incompleteHistoricalRequeryCannotReplaceALargerSavedSnapshot() {
        val previous = JSONObject().put("screen", 100_000).put("unlocks", 3).put("restricted", 20_000)
        val incomplete = JSONObject().put("screen", 40_000).put("unlocks", 1).put("restricted", 0).put("completeThroughNow", false)
        val saved = UsageSegment.preserveIncompleteHistory(previous, incomplete)
        assertEquals(100_000, saved.getLong("screen")); assertEquals(3, saved.getInt("unlocks"))
        assertEquals(20_000, saved.getLong("restricted")); assertFalse(saved.getBoolean("completeThroughNow"))
        val complete = JSONObject().put("screen", 90_000).put("unlocks", 2).put("restricted", 15_000).put("completeThroughNow", true).put("restrictedComplete", true)
        assertEquals(90_000, UsageSegment.preserveIncompleteHistory(previous, complete).getLong("screen"))
    }
}
