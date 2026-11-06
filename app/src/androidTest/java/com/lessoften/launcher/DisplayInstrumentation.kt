package com.lessoften.launcher

import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings

/** Feasibility check on the real phone, using the app's actual ADB-granted permission. */
class DisplayInstrumentation : Instrumentation() {
    private var mode = "display"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode") ?: "display"; start() }
    override fun onStart() {
        val app = targetContext.state
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!app.initialized && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(20)
        check(app.initialized) { "Application initialization did not complete" }
        if (mode == "segment") {
            val name = "usage_segment_instrumentation"
            try {
                targetContext.deleteSharedPreferences(name)
                val isolated = object : android.content.ContextWrapper(targetContext) {
                    override fun getSharedPreferences(nameRequested: String, mode: Int): android.content.SharedPreferences =
                        if (nameRequested == "usage_partial") super.getSharedPreferences(name, mode) else super.getSharedPreferences(nameRequested, mode)
                }
                val zone = java.time.ZoneId.systemDefault()
                val key = "${java.time.LocalDate.now(zone)}|${zone.id}"
                val base = org.json.JSONObject().put("screen", 12_345).put("unlocks", 2).put("restricted", 1_000)
                val prefs = isolated.getSharedPreferences("usage_partial", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("invalid:$key", true).putString("days", org.json.JSONObject().put(key, base).toString()).commit()
                val reader = UsageReader(isolated)
                reader.read()
                android.os.SystemClock.sleep(2_000)
                val result = reader.read().days.first()
                check(result.screenMillis != null && result.screenMillis >= 12_345) { "Prior total was not preserved" }
                check(result.unlocks == 2) { "Unlocks changed without a lock transition" }
                check(!result.screenComplete && !result.restrictedComplete) { "Discontinuous segment appeared complete" }
                val replay = reader.read(forceFull = true).days.first()
                check(replay.screenMillis != null && kotlin.math.abs(replay.screenMillis - result.screenMillis) < 10_000) { "Segment replay duplicated time" }
                finish(0, Bundle().apply { putString("stream", "PASS: simulated discontinuity preserves totals, resumes partial collection, and replays without duplication.\n") })
            } catch (e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}\n") }) }
            finally { targetContext.deleteSharedPreferences(name) }
            return
        }
        if (mode == "history") {
            val name = "usage_history_instrumentation"
            try {
                targetContext.deleteSharedPreferences(name)
                val isolated = object : android.content.ContextWrapper(targetContext) {
                    override fun getSharedPreferences(nameRequested: String, mode: Int): android.content.SharedPreferences =
                        if (nameRequested == "usage_partial") super.getSharedPreferences(name, mode) else super.getSharedPreferences(nameRequested, mode)
                }
                val result = UsageReader(isolated).read()
                check(result.access) { "Usage Access is required for this check" }
                val prior = result.days.getOrNull(1) ?: error("Previous day missing")
                check(prior.screenMillis != null && prior.screenMillis > 0) { "No reliable previous-day intervals found" }
                check(!prior.screenComplete) { "An untracked prior day was presented as complete" }
                check(targetContext.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).getString("days", "")!!.contains("finalizedAt")) { "Previous-day reconciliation was not saved" }
                finish(0, Bundle().apply { putString("stream", "PASS: isolated previous-day backfill is saved and marked partial.\n") })
            } catch (e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}\n") }) }
            finally { targetContext.deleteSharedPreferences(name) }
            return
        }
        if (mode == "usage") {
            try {
                val reader = UsageReader(targetContext)
                val first = reader.read()
                check(first.access) { "Usage Access is required for this check" }
                val incremental = first.days.first()
                val full = reader.read(forceFull = true).days.first()
                check(incremental.screenMillis != null && full.screenMillis != null) { "No measurable usage events" }
                check(kotlin.math.abs(full.screenMillis - incremental.screenMillis) < 10_000) { "Screen replay mismatch" }
                if (incremental.restrictedMillis != null && full.restrictedMillis != null)
                    check(kotlin.math.abs(full.restrictedMillis - incremental.restrictedMillis) < 10_000) { "Restricted replay mismatch" }
                check(incremental.unlocks == full.unlocks) { "Unlock replay mismatch" }
                finish(0, Bundle().apply { putString("stream", "PASS: incremental replay agrees with a full-day query within the observation window.\n") })
            } catch (e: Throwable) { finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}\n") }) }
            return
        }
        val enabled = Settings.Secure.getString(targetContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (enabled.contains(targetContext.packageName)) {
            finish(1, Bundle().apply { putString("stream", "SKIPPED: Disable the service before the isolated display round-trip check.\n") }); return
        }
        val baseline = DisplayJournal.transform.keys.associateWith { Settings.Secure.getString(targetContext.contentResolver, it) }
        try {
            check(app.securePermission()) { "ADB secure-settings grant missing" }
            check(Quotes.normalize("\u00A0 ONE\u2003two \n") == "one two") { "Android whitespace normalization differs" }
            check(!app.display.active()) { "Resolve the existing journal in setup before this isolated test" }
            app.display.apply()
            check(DisplayJournal.transform.all { (key, value) -> Settings.Secure.getString(targetContext.contentResolver, key) == value })
            app.display.restore()
            check(baseline.all { (key, value) -> Settings.Secure.getString(targetContext.contentResolver, key) == value })
            val counts = Bucket.entries.joinToString { bucket -> "$bucket=${app.catalog.list(bucket).size}" }
            sendStatus(0, Bundle().apply { putString("stream", "Installed catalog: $counts\nMissing configured entries: ${app.catalog.missing().joinToString { it.packageName }.ifEmpty { "none" }}\n") })
            app.catalog.entries.filter { it.packageName in setOf("com.cincsystems.ppm1", "com.comit.caregiver") }.forEach {
                sendStatus(0, Bundle().apply { putString("stream", "Resolved installed label: ${it.packageName} = ${it.label}\n") })
            }
            finish(0, Bundle().apply { putString("stream", "PASS: App permission writes monochromacy settings and restores the exact original baseline on this Pixel. Visual appearance still requires observation.\n") })
        } catch (e: Throwable) {
            runCatching { app.display.restore() }
            finish(1, Bundle().apply { putString("stream", "FAIL: ${e.message}\n") })
        }
    }
}
