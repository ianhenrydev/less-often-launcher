package com.lessoften.launcher

import org.json.JSONObject

/** Adds an independently observed post-discontinuity interval to the preserved totals. */
internal object UsageSegment {
    fun merge(base: JSONObject, day: UsageReducer.Day, covered: Boolean, checkpoints: JSONObject, complete: Boolean): JSONObject {
        fun add(a: Long, b: Long): Long {
            val left = a.coerceAtLeast(0); val right = b.coerceAtLeast(0)
            return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
        }
        val screen = add(base.optLong("screen"), day.screenMillis)
        val value = JSONObject().put("screen", screen)
            .put("unlocks", add(base.optInt("unlocks").toLong(), day.unlocks.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).put("checkpoints", checkpoints)
            .put("completeThroughNow", complete)
        if (covered || base.has("restricted")) value.put("restricted", add(base.optLong("restricted"), day.restrictedMillis).coerceAtMost(screen))
        return value
    }
    fun preserveIncompleteHistory(previous: JSONObject?, current: JSONObject): JSONObject {
        if (previous == null) return current
        val previousScreen = previous.optLong("screen", -1)
        val previousUnlocks = previous.optInt("unlocks", -1)
        val currentRestricted = if (current.has("restricted")) current.optLong("restricted") else -1L
        val previousRestricted = if (previous.has("restricted")) previous.optLong("restricted") else -1L
        val screenRegressed = !current.optBoolean("completeThroughNow") &&
            (previousScreen > current.optLong("screen", -1) || previousUnlocks > current.optInt("unlocks", -1))
        val restrictedRegressed = !current.optBoolean("restrictedComplete") && previousRestricted > currentRestricted
        if (screenRegressed || restrictedRegressed) {
            return JSONObject(previous.toString()).put("completeThroughNow", false).put("restrictedComplete", false).put("checkpoints", JSONObject())
        }
        return current
    }
}
