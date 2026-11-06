package com.lessoften.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class UsageDay(val date: LocalDate, val screenMillis: Long?, val unlocks: Int?, val restrictedMillis: Long?, val notifications: Int?, val grants: Int?, val checkpoints: Map<Int, UsageComparison.Checkpoint>, val screenComplete: Boolean, val restrictedComplete: Boolean, val notificationsComplete: Boolean, val grantsComplete: Boolean)
internal data class UsageSnapshot(val access: Boolean, val days: List<UsageDay>, val comparison: UsageComparison.Result?)

/** Queries run off the main thread; recent intervals are replayed from durable checkpoints. */
internal class UsageReader(private val context: Context) {
    companion object { private val lock = Any() }
    private val preferences = context.getSharedPreferences("usage_partial", Context.MODE_PRIVATE)
    fun read(forceFull: Boolean = false): UsageSnapshot = synchronized(lock) { readLocked(forceFull) }
    private fun readLocked(forceFull: Boolean): UsageSnapshot {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val access = context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        val history = runCatching { JSONObject(preferences.getString("days", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val elapsed = SystemClock.elapsedRealtime()
        val previousWall = preferences.getLong("lastWall", 0)
        val previousElapsed = preferences.getLong("lastElapsed", 0)
        val previousZone = preferences.getString("lastZone", null)
        val discontinuity = previousWall != 0L && (elapsed < previousElapsed ||
            kotlin.math.abs((now - previousWall) - (elapsed - previousElapsed)) > 3_000 || previousZone != zone.id)
        val invalidKey = "invalid:$today|${zone.id}"
        val revisionKey = "revision:$today|${zone.id}"
        if (discontinuity) preferences.edit().putBoolean(invalidKey, true)
            .putInt(revisionKey, preferences.getInt(revisionKey, 0) + 1).apply()
        if (!preferences.getBoolean(invalidKey, false)) {
            var prunedHistory = false
            history.keys().asSequence().toList().forEach { key ->
                if (Retention.expired(key, "", today)) { history.remove(key); prunedHistory = true }
            }
            if (prunedHistory) preferences.edit().putString("days", history.toString()).apply()
            (context.applicationContext as LessOftenApp).gateCoverage.prune()
        }
        val expiredKeys = preferences.all.keys.filter { it.startsWith("invalid:") || it.startsWith("comparison-invalid:") || it.startsWith("classification:") || it.startsWith("cursors:") || it.startsWith("fullReplay:") || it.startsWith("segment:") || it.startsWith("revision:") || it.startsWith("historyAttempt:") }.filter { key ->
            Retention.expired(key, key.substringBefore(':') + ":", today)
        }
        if (!preferences.getBoolean(invalidKey, false) && expiredKeys.isNotEmpty()) preferences.edit().also { edit -> expiredKeys.forEach(edit::remove) }.apply()
        preferences.edit().putLong("lastWall", now).putLong("lastElapsed", elapsed).putString("lastZone", zone.id).apply()
        var refreshed = false
        fun collect(targetDate: LocalDate, endTime: Long, historical: Boolean) {
            runCatching {
                val today = targetDate
                val now = endTime
                val invalidKey = "invalid:$today|${zone.id}"
                val revisionKey = "revision:$today|${zone.id}"
                val calendarStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val invalid = preferences.getBoolean(invalidKey, false)
                val revision = preferences.getInt(revisionKey, 0)
                val segmentKey = "segment:$today|${zone.id}"
                var segment = runCatching { JSONObject(preferences.getString(segmentKey, "{}") ?: "{}") }.getOrElse { JSONObject() }
                if (invalid && (!segment.has("start") || segment.optInt("revision", -1) != revision)) {
                    check(!historical) { "No reliable historical segment state" }
                    val power = context.getSystemService(android.os.PowerManager::class.java).isInteractive
                    val locked = context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
                    segment = JSONObject().put("start", now).put("elapsed", elapsed).put("revision", revision)
                        .put("boot", android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1))
                        .put("interactive", power).put("locked", locked)
                        .put("base", history.optJSONObject("$today|${zone.id}") ?: JSONObject())
                    preferences.edit().putString(segmentKey, segment.toString()).remove("cursors:$today|${zone.id}")
                        .remove("fullReplay:$today|${zone.id}").apply()
                }
                val start = if (invalid) segment.getLong("start") else calendarStart
                val base = if (invalid) segment.optJSONObject("base") ?: JSONObject() else JSONObject()
                val manager = context.getSystemService(UsageStatsManager::class.java)
                val cursorKey = "cursors:$today|${zone.id}"
                val oldCursors = runCatching { JSONArray(preferences.getString(cursorKey, "[]")) }.getOrElse { JSONArray() }
                val candidates = (0 until oldCursors.length()).mapNotNull { oldCursors.optJSONObject(it) }
                val fullKey = "fullReplay:$today|${zone.id}"
                val fullDue = forceFull || historical || now - preferences.getLong(fullKey, 0) >= 1_800_000
                val cursor = if (fullDue) null else candidates.filter { it.optLong("at") in start..(now - 600_000) }.maxByOrNull { it.optLong("at") }
                val anchor = cursor?.optLong("at") ?: start
                val before = if (cursor == null && !invalid) manager.queryEvents(today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), start) else null
                var screenState: UsageReducer.Event? = null
                var lockState: UsageReducer.Event? = null
                if (invalid) {
                    screenState = if (segment.getBoolean("interactive")) UsageReducer.Event.SCREEN_ON else UsageReducer.Event.SCREEN_OFF
                    lockState = if (segment.getBoolean("locked")) UsageReducer.Event.LOCKED else UsageReducer.Event.UNLOCKED
                }
                val prior = UsageEvents.Event()
                while (before != null && before.hasNextEvent()) {
                    before.getNextEvent(prior)
                    when (prior.eventType) {
                        UsageEvents.Event.SCREEN_INTERACTIVE -> screenState = UsageReducer.Event.SCREEN_ON
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> screenState = UsageReducer.Event.SCREEN_OFF
                        UsageEvents.Event.KEYGUARD_SHOWN -> lockState = UsageReducer.Event.LOCKED
                        UsageEvents.Event.KEYGUARD_HIDDEN -> lockState = UsageReducer.Event.UNLOCKED
                        UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> { screenState = null; lockState = null }
                    }
                }
                val events = manager.queryEvents(if (cursor == null) start else anchor + 1, now)
                val reducer = UsageReducer(zone)
                if (cursor != null) reducer.restore(cursor.getJSONObject("state"))
                data class Timed(val time: Long, val kind: UsageReducer.Event?, val pkg: String? = null, val instance: String? = null, val paused: Boolean = false, val checkpoint: Int? = null)
                val timeline = mutableListOf<Timed>()
                if (cursor == null) {
                    timeline.add(Timed(start, UsageReducer.Event.COVERAGE_UNKNOWN))
                    screenState?.let { timeline.add(Timed(start, it)) }
                    lockState?.let { timeline.add(Timed(start, it)) }
                }
                val normalDay = UsageComparison.isNormalDay(today, zone)
                if (normalDay) for (minute in 5..1435 step 5) {
                    val at = calendarStart + minute * 60_000L
                    if (at > anchor && at <= now) timeline.add(Timed(at, UsageReducer.Event.TICK, checkpoint = minute))
                }
                val event = UsageEvents.Event()
                var lastDeviceEvent = 0L
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val kind = when (event.eventType) {
                        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageReducer.Event.SCREEN_ON
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageReducer.Event.SCREEN_OFF
                        UsageEvents.Event.KEYGUARD_SHOWN -> UsageReducer.Event.LOCKED
                        UsageEvents.Event.KEYGUARD_HIDDEN -> UsageReducer.Event.UNLOCKED
                        UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> UsageReducer.Event.GAP
                        else -> null
                    }
                    if (event.timeStamp in (if (cursor == null) start else anchor + 1)..now) {
                        if (kind != null) { timeline.add(Timed(event.timeStamp, kind)); lastDeviceEvent = maxOf(lastDeviceEvent, event.timeStamp) }
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.ACTIVITY_PAUSED)
                            timeline.add(Timed(event.timeStamp, null, event.packageName, event.className, event.eventType == UsageEvents.Event.ACTIVITY_PAUSED))
                    }
                }
                val coverage = runCatching { JSONArray(context.getSharedPreferences("gate_coverage", Context.MODE_PRIVATE).getString("intervals", "[]")) }.getOrElse { JSONArray() }
                var covered = cursor?.optBoolean("covered") == true
                for (i in 0 until coverage.length()) {
                    val item = coverage.optJSONObject(i) ?: continue
                    if (item.optString("zone") != zone.id) continue
                    if (invalid && (segment.optInt("boot", -1) < 0 || item.optInt("boot", -2) != segment.optInt("boot") ||
                            item.optLong("elapsed") < segment.optLong("elapsed"))) continue
                    val from = maxOf(start, item.optLong("start")); val until = minOf(now, item.optLong("end"))
                    if (until > maxOf(from, anchor)) {
                        if (from > anchor || cursor == null) timeline.add(Timed(maxOf(from, anchor), if (item.optBoolean("allowed")) UsageReducer.Event.GATE_CLOSED else UsageReducer.Event.GATE_OPEN))
                        timeline.add(Timed(until, UsageReducer.Event.COVERAGE_UNKNOWN))
                    }
                }
                var currentInstance: Pair<String, String?>? = cursor?.optString("pkg")?.takeIf { it.isNotEmpty() }?.let { it to cursor.optString("class").takeIf(String::isNotEmpty) }
                val app = context.applicationContext as LessOftenApp
                if (historical) check(lastDeviceEvent > 0L) { "No retained device events for this day" }
                val collectionEnd = if (historical && app.processStartedAt >= start) minOf(now, lastDeviceEvent) else now
                val classificationKey = "classification:$today|${zone.id}"
                val classifications = runCatching { JSONObject(preferences.getString(classificationKey, "{}") ?: "{}") }.getOrElse { JSONObject() }
                val checkpoints = history.optJSONObject("$today|${zone.id}")?.optJSONObject("checkpoints") ?: JSONObject()
                checkpoints.keys().asSequence().toList().forEach { key ->
                    val minute = key.toIntOrNull()
                    if (invalid || minute == null || calendarStart + minute * 60_000L > anchor) checkpoints.remove(key)
                }
                val newCursors = mutableListOf<JSONObject>()
                if (cursor != null) newCursors.add(cursor)
                val eligibleDay = !invalid && app.processStartedAt < start && normalDay && !preferences.getBoolean("comparison-invalid:$today|${zone.id}", false)
                timeline.filter { it.time <= collectionEnd }.sortedWith(compareBy<Timed> { it.time }.thenBy { if (it.checkpoint != null) 1 else 0 }).forEach { item ->
                    if (item.kind != null) {
                        reducer.accept(item.time, item.kind)
                        if (item.kind == UsageReducer.Event.GATE_CLOSED || item.kind == UsageReducer.Event.GATE_OPEN) covered = true
                    }
                    else if (item.pkg != null) {
                        val identity = item.pkg to item.instance
                        if (item.paused) {
                            if (currentInstance == identity) { currentInstance = null; reducer.accept(item.time, UsageReducer.Event.OTHER_APP) }
                        } else {
                            currentInstance = identity
                            if (!classifications.has(item.pkg)) {
                                val configured = app.policy.configured(item.pkg)
                                val classification = when {
                                    item.pkg == context.packageName -> 0
                                    configured != null -> if (configured.bucket == Bucket.Restricted) 1 else 0
                                    app.catalog.isLaunchable(item.pkg) -> 1
                                    else -> -1
                                }
                                classifications.put(item.pkg, classification)
                            }
                            val classification = when (val saved = classifications.opt(item.pkg)) {
                                is Boolean -> if (saved) 1 else if (item.pkg == context.packageName || app.policy.configured(item.pkg) != null) 0 else -1
                                is Number -> saved.toInt()
                                else -> -1
                            }
                            reducer.accept(item.time, when (classification) {
                                1 -> UsageReducer.Event.RESTRICTED
                                0 -> UsageReducer.Event.OTHER_APP
                                else -> UsageReducer.Event.FOREGROUND_UNKNOWN
                            })
                        }
                    }
                    item.checkpoint?.let { minute ->
                        val dayAtCutoff = reducer.snapshot()[today]
                        if (dayAtCutoff != null && eligibleDay && !dayAtCutoff.screenIncomplete)
                            checkpoints.put(minute.toString(), dayAtCutoff.screenMillis)
                        newCursors.add(JSONObject().put("at", item.time).put("state", reducer.save()).put("covered", covered)
                            .put("pkg", currentInstance?.first ?: "").put("class", currentInstance?.second ?: ""))
                    }
                }
                preferences.edit().putString(classificationKey, classifications.toString()).apply()
                reducer.accept(collectionEnd, UsageReducer.Event.TICK)
                val day = reducer.snapshot()[today]
                if (day != null && day.knownMillis > 0) {
                    check(preferences.getInt(revisionKey, 0) == revision) { "Clock changed during collection" }
                    val restrictedComplete = eligibleDay && !day.screenIncomplete && !day.restrictedIncomplete
                    var value = UsageSegment.merge(base, day, covered || restrictedComplete, checkpoints, eligibleDay && !day.screenIncomplete)
                        .put("restrictedComplete", restrictedComplete)
                    if (historical) value = UsageSegment.preserveIncompleteHistory(history.optJSONObject("$today|${zone.id}"), value)
                    if (historical) value.put("finalizedAt", System.currentTimeMillis())
                    history.put("$today|${zone.id}", value)
                    history.keys().asSequence().toList().forEach { key ->
                        if (Retention.expired(key, "", today)) history.remove(key)
                    }
                    val retained = JSONArray()
                    newCursors.takeLast(4).forEach(retained::put)
                    val edit = preferences.edit().putString("days", history.toString()).putString(cursorKey, retained.toString())
                    if (cursor == null) edit.putLong(fullKey, now)
                    edit.apply()
                    if (!historical) refreshed = true
                }
            }
        }
        if (access) {
            if (!preferences.getBoolean(invalidKey, false)) for (offset in 1..7) {
                val previousDate = today.minusDays(offset.toLong())
                val previous = history.optJSONObject("$previousDate|${zone.id}")
                val attemptKey = "historyAttempt:$previousDate|${zone.id}"
                val retryAfter = if (offset == 1 && previous != null) 1_800_000L else 21_600_000L
                val lastAttempt = maxOf(preferences.getLong(attemptKey, 0), previous?.optLong("finalizedAt", 0) ?: 0)
                val due = (previous == null || offset == 1) && now - lastAttempt >= retryAfter
                if (due) {
                    preferences.edit().putLong(attemptKey, now).apply()
                    collect(previousDate, previousDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), historical = true)
                }
            }
            collect(today, now, historical = false)
        }
        val notificationPrefs = context.getSharedPreferences("notification_counts", Context.MODE_PRIVATE)
        val runtimePrefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        fun prune(prefs: android.content.SharedPreferences, prefix: String) {
            val expired = prefs.all.keys.filter { it.startsWith(prefix) }.filter { key ->
                Retention.expired(key, prefix, today)
            }
            if (expired.isNotEmpty()) prefs.edit().also { edit -> expired.forEach(edit::remove) }.apply()
        }
        if (!preferences.getBoolean(invalidKey, false)) { prune(runtimePrefs, "grant:"); prune(notificationPrefs, "day:") }
        val connection = UsageNotificationListener.connection
        val notificationConnected = connection != null && context.getSystemService(android.app.NotificationManager::class.java)
            .isNotificationListenerAccessGranted(android.content.ComponentName(context, UsageNotificationListener::class.java))
        val notificationContinuity = notificationConnected && connection != null && connection.zone == zone.id &&
            kotlin.math.abs((now - connection.wall) - (elapsed - connection.elapsed)) < 3_000
        val days = (0..7).map { offset ->
            val date = today.minusDays(offset.toLong())
            val key = "$date|${zone.id}"
            val value = history.optJSONObject(key)
            val grants = runtimePrefs.getString("grant:$key", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val grantsComplete = (context.applicationContext as LessOftenApp).sessions.countReliable(date, zone.id)
            val notificationsComplete = notificationContinuity && connection != null &&
                connection.wall <= date.atStartOfDay(zone).toInstant().toEpochMilli() && !preferences.getBoolean("invalid:$key", false)
            val checkpointJson = value?.optJSONObject("checkpoints")
            val checkpointMap = checkpointJson?.keys()?.asSequence()?.mapNotNull { k ->
                k.toIntOrNull()?.let { minute -> minute to UsageComparison.Checkpoint(checkpointJson.optLong(k), true) }
            }?.toMap() ?: emptyMap()
            val historicalComplete = offset > 0 && value != null && value.optLong("finalizedAt", 0) != 0L &&
                !preferences.getBoolean("invalid:$key", false) && !preferences.getBoolean("comparison-invalid:$key", false)
            UsageDay(date, value?.optLong("screen"), value?.optInt("unlocks"), if (value?.has("restricted") == true) value.optLong("restricted") else null,
                if (notificationPrefs.contains("day:$key")) notificationPrefs.getInt("day:$key", 0) else if ((offset == 0 && notificationConnected) || notificationsComplete) 0 else null,
                grants?.keys()?.asSequence()?.sumOf { grants.optInt(it) } ?: if (grantsComplete) 0 else null, checkpointMap,
                ((offset == 0 && access && refreshed && !preferences.getBoolean(invalidKey, false) && !preferences.getBoolean("comparison-invalid:$today|${zone.id}", false)) || historicalComplete) && value?.optBoolean("completeThroughNow") == true,
                ((offset == 0 && access && refreshed && !preferences.getBoolean(invalidKey, false) && !preferences.getBoolean("comparison-invalid:$today|${zone.id}", false)) || historicalComplete) && value?.optBoolean("restrictedComplete") == true,
                notificationsComplete, grantsComplete)
        }
        val localNow = Instant.ofEpochMilli(now).atZone(zone)
        val comparison = if (access && days.first().screenComplete) UsageComparison.compare(localNow.hour * 60 + localNow.minute, days.first().checkpoints, days.drop(1).map { it.checkpoints }) else null
        return UsageSnapshot(access, days, comparison)
    }
}
