package com.lessoften.launcher

import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings

class PreferencesStore(context: Context) : DurableStore {
    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
    override fun get(key: String) = prefs.getString(key, null)
    @Synchronized override fun write(values: Map<String, String?>) {
        val edit = prefs.edit()
        values.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }
        check(edit.commit()) { "Unable to persist runtime state" }
    }
}
class LessOftenApp : Application() {
    @Volatile var initialized = false; private set
    lateinit var store: PreferencesStore; private set
    lateinit var policy: BucketPolicy; private set
    lateinit var sessions: Sessions; private set
    lateinit var quotes: Quotes; private set
    lateinit var display: DisplayJournal; private set
    lateinit var catalog: AppCatalog; private set
    var failure: String? = null
    override fun onCreate() {
        super.onCreate()
        store = PreferencesStore(this)
        policy = BucketPolicy.parse(assets.open("app-buckets.json").bufferedReader().use { it.readText() })
        quotes = Quotes(Quotes.parse(assets.open("quotes.json").bufferedReader().use { it.readText() }), store)
        sessions = Sessions(store, object : ElapsedClock {
            override fun now() = SystemClock.elapsedRealtime()
            override fun boot(): String? = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
                .takeIf { it >= 0 }?.toString()
        }, policy.durationMillis)
        display = DisplayJournal(store, object : ColorSettings {
            override fun read(key: String) = Settings.Secure.getString(contentResolver, key)
            override fun put(key: String, value: String?) {
                check(Settings.Secure.putString(contentResolver, key, value)) { "Secure setting write failed: $key" }
            }
        })
        catalog = AppCatalog(this, policy)
        initialized = true
    }
    fun securePermission() = checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    fun capabilityProblem(): String? = when {
        GateService.current == null -> "Accessibility service is disconnected. App entry and expiry gates are inactive."
        !securePermission() -> "WRITE_SECURE_SETTINGS is missing. Grayscale cannot be applied or repaired."
        failure != null -> failure
        display.problem != null -> display.problem
        store.get("colorConflict") != null -> store.get("colorConflict")
        else -> null
    }
    fun repairDisplay() {
        try { display.restore(); failure = null } catch (e: Exception) { failure = "Display recovery failed: ${e.message}" }
    }
}
val Context.state: LessOftenApp get() = applicationContext as LessOftenApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val service = GateService.current
            if (service != null) service.reconcileNow() else context.state.repairDisplay()
        }
    }
}
