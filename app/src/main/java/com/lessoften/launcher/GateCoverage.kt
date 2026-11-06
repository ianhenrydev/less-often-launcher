package com.lessoften.launcher

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

/** Gate/window coverage contains only time boundaries and a boolean, never screen or notification content. */
internal class GateCoverage(context: Context) {
    private val appContext = context.applicationContext
    private val boot = runCatching { android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
    private val prefs = context.getSharedPreferences("gate_coverage", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private var previousAllowed: Boolean? = null
    private var previousElapsed = 0L
    fun prune() {
        val wall = System.currentTimeMillis(); val zone = ZoneId.systemDefault()
        worker.execute { runCatching {
            val today = Instant.ofEpochMilli(wall).atZone(zone).toLocalDate()
            if (prefs.getBoolean("pruneBlocked:$today|${zone.id}", false)) return@runCatching
            val cutoff = Instant.ofEpochMilli(wall).atZone(zone).toLocalDate().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val source = JSONArray(prefs.getString("intervals", "[]")); val kept = JSONArray()
            for (i in 0 until source.length()) if (source.getJSONObject(i).optLong("end") >= cutoff) kept.put(source.getJSONObject(i))
            val edit = prefs.edit()
            if (kept.length() != source.length()) edit.putString("intervals", kept.toString())
            val oldest = Instant.ofEpochMilli(wall).atZone(zone).toLocalDate().minusDays(7)
            prefs.all.keys.filter { it.startsWith("pruneBlocked:") }.forEach { key ->
                val day = runCatching { java.time.LocalDate.parse(key.removePrefix("pruneBlocked:").substringBefore('|')) }.getOrNull()
                if (day == null || day.isBefore(oldest)) edit.remove(key)
            }
            edit.apply()
        } }
    }
    @Synchronized fun observe(allowed: Boolean) {
        val elapsed = SystemClock.elapsedRealtime()
        if (allowed == previousAllowed && elapsed - previousElapsed < 1_000) return
        previousAllowed = allowed; previousElapsed = elapsed
        val wall = System.currentTimeMillis(); val zone = ZoneId.systemDefault().id
        worker.execute { runCatching { append(wall, elapsed, zone, boot, allowed) } }
    }
    private fun append(wall: Long, elapsed: Long, zone: String, boot: Int, allowed: Boolean) {
        val list = runCatching { JSONArray(prefs.getString("intervals", "[]")) }.getOrElse { JSONArray() }
        val keepAfter = Instant.ofEpochMilli(wall).atZone(ZoneId.of(zone)).toLocalDate().minusDays(7).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
        val prior = if (list.length() > 0) list.optJSONObject(list.length() - 1) else null
        val deltaElapsed = if (prior == null) 0 else elapsed - prior.optLong("elapsed")
        val deltaWall = if (prior == null) 0 else wall - prior.optLong("end")
        val date = Instant.ofEpochMilli(wall).atZone(ZoneId.of(zone)).toLocalDate()
        val blockedKey = "pruneBlocked:$date|$zone"
        if (prior != null && (deltaElapsed < 0 || kotlin.math.abs(deltaWall - deltaElapsed) > 3_000 || prior.optString("zone") != zone))
            prefs.edit().putBoolean(blockedKey, true).apply()
        val pruneBlocked = prefs.getBoolean(blockedKey, false) ||
            prefsForUsage().getBoolean("invalid:$date|$zone", false)
        val result = JSONArray()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            if (pruneBlocked || item.optLong("end") >= keepAfter) result.put(item)
        }
        val last = if (result.length() > 0) result.getJSONObject(result.length() - 1) else null
        val elapsedDelta = if (last == null) Long.MAX_VALUE else elapsed - last.optLong("elapsed")
        val wallDelta = if (last == null) Long.MAX_VALUE else wall - last.optLong("end")
        if (last != null && last.optInt("boot", -2) == boot && last.optString("zone") == zone && last.optBoolean("allowed") == allowed &&
            elapsedDelta in 0..1500 && kotlin.math.abs(wallDelta - elapsedDelta) < 500) {
            last.put("end", wall); last.put("elapsed", elapsed)
        } else {
            result.put(JSONObject().put("start", wall).put("end", wall).put("elapsed", elapsed).put("boot", boot).put("zone", zone).put("allowed", allowed))
        }
        prefs.edit().putString("intervals", result.toString()).apply()
    }
    private fun prefsForUsage() = appContext.getSharedPreferences("usage_partial", Context.MODE_PRIVATE)
}
