package com.lessoften.launcher

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/** A deterministic interval reducer. Unknown state is coverage loss, not zero usage. */
internal class UsageReducer(private val zone: ZoneId) {
    data class Day(var screenMillis: Long = 0, var restrictedMillis: Long = 0, var unlocks: Int = 0, var incomplete: Boolean = false, var knownMillis: Long = 0, var screenIncomplete: Boolean = false, var restrictedIncomplete: Boolean = false)
    enum class Event { SCREEN_ON, SCREEN_OFF, LOCKED, UNLOCKED, RESTRICTED, OTHER_APP, FOREGROUND_UNKNOWN, IME, INTERRUPTED, GATE_OPEN, GATE_CLOSED, COVERAGE_UNKNOWN, GAP, TICK }
    private val days = linkedMapOf<LocalDate, Day>()
    private var last: Long? = null
    private var interactive: Boolean? = null
    private var locked: Boolean? = null
    private var restricted: Boolean? = null
    private var interrupted = false
    private var gate = true
    private var coverageKnown = false
    private fun day(at: Long) = days.getOrPut(Instant.ofEpochMilli(at).atZone(zone).toLocalDate()) { Day() }
    fun snapshot(): Map<LocalDate, Day> = days.mapValues { (_, value) -> value.copy() }
    fun save(): JSONObject {
        val aggregates = JSONObject()
        days.forEach { (date, value) -> aggregates.put(date.toString(), JSONObject()
            .put("screen", value.screenMillis).put("restricted", value.restrictedMillis).put("unlocks", value.unlocks)
            .put("incomplete", value.incomplete).put("known", value.knownMillis).put("screenIncomplete", value.screenIncomplete).put("restrictedIncomplete", value.restrictedIncomplete)) }
        return JSONObject().put("last", last ?: JSONObject.NULL).put("interactive", interactive ?: JSONObject.NULL)
            .put("locked", locked ?: JSONObject.NULL).put("restricted", restricted ?: JSONObject.NULL)
            .put("interrupted", interrupted).put("gate", gate).put("coverageKnown", coverageKnown).put("days", aggregates)
    }
    fun restore(saved: JSONObject) {
        fun nullableBoolean(key: String) = if (saved.isNull(key)) null else saved.getBoolean(key)
        last = if (saved.isNull("last")) null else saved.getLong("last")
        interactive = nullableBoolean("interactive"); locked = nullableBoolean("locked"); restricted = nullableBoolean("restricted")
        interrupted = saved.getBoolean("interrupted"); gate = saved.getBoolean("gate")
        coverageKnown = saved.optBoolean("coverageKnown", false)
        days.clear()
        val aggregates = saved.getJSONObject("days")
        aggregates.keys().asSequence().forEach { key ->
            val value = aggregates.getJSONObject(key)
            days[LocalDate.parse(key)] = Day(value.getLong("screen"), value.getLong("restricted"), value.getInt("unlocks"),
                value.getBoolean("incomplete"), value.getLong("known"), value.getBoolean("screenIncomplete"), value.optBoolean("restrictedIncomplete", true))
        }
    }
    fun accept(at: Long, event: Event) {
        val previous = last
        if (previous != null && at < previous) {
            day(previous).incomplete = true; day(at).incomplete = true
            day(previous).screenIncomplete = true; day(at).screenIncomplete = true
            day(previous).restrictedIncomplete = true; day(at).restrictedIncomplete = true
            interactive = null; locked = null; restricted = null; interrupted = false; gate = true; coverageKnown = false
        } else if (previous != null) {
            var cursor: Long = previous
            while (cursor < at) {
                val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val boundary = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = minOf(at, boundary)
                val record = day(cursor)
                if (interactive == null || (interactive == true && locked == null)) { record.incomplete = true; record.screenIncomplete = true; record.restrictedIncomplete = true }
                else {
                  record.knownMillis += end - cursor
                  if (interactive == true && locked == false) {
                    record.screenMillis += end - cursor
                    if (!coverageKnown) { record.incomplete = true; record.restrictedIncomplete = true }
                    else if (!gate && !interrupted && restricted == null) { record.incomplete = true; record.restrictedIncomplete = true }
                    else if (restricted == true && !interrupted && !gate) record.restrictedMillis += end - cursor
                  }
                }
                cursor = end
            }
        }
        when (event) {
            Event.SCREEN_ON -> interactive = true
            Event.SCREEN_OFF -> interactive = false
            Event.LOCKED -> locked = true
            Event.UNLOCKED -> { if (locked == true && interactive == true) day(at).unlocks++; locked = false }
            Event.RESTRICTED -> { restricted = true; interrupted = false }
            Event.OTHER_APP -> { restricted = false; interrupted = false }
            Event.FOREGROUND_UNKNOWN -> { restricted = null; interrupted = false }
            Event.IME -> Unit // Keep the underlying application attribution.
            Event.INTERRUPTED -> interrupted = true
            Event.GATE_OPEN -> { gate = true; coverageKnown = true }
            Event.GATE_CLOSED -> { gate = false; coverageKnown = true }
            Event.COVERAGE_UNKNOWN -> { gate = true; coverageKnown = false }
            Event.GAP -> { day(at).incomplete = true; day(at).screenIncomplete = true; day(at).restrictedIncomplete = true; interactive = null; locked = null; restricted = null; interrupted = false; gate = true; coverageKnown = false }
            Event.TICK -> Unit
        }
        last = at
    }
}
