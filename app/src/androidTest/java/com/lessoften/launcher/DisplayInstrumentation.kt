package com.lessoften.launcher

import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings

/** Feasibility check on the real phone, using the app's actual ADB-granted permission. */
class DisplayInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val app = targetContext.state
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!app.initialized && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(20)
        check(app.initialized) { "Application initialization did not complete" }
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
