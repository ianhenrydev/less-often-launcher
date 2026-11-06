package com.lessoften.launcher

import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.app.AppOpsManager
import java.time.LocalDate
import java.time.ZoneId

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
    internal lateinit var gateCoverage: GateCoverage; private set
    var failure: String? = null
    internal var processStartedAt = 0L; private set
    private var usageObserver: AppOpsManager.OnOpChangedListener? = null
    override fun onCreate() {
        super.onCreate()
        processStartedAt = System.currentTimeMillis()
        store = PreferencesStore(this)
        runCatching {
            store.get("grantHistory")?.let { old ->
                val history = org.json.JSONObject(old)
                val migrated = history.keys().asSequence().mapNotNull { day ->
                    val key = "grant:$day"
                    if (store.get(key) == null) key to history.getJSONObject(day).toString() else null
                }.toMap()
                store.write(migrated + mapOf("grantHistory" to null))
            }
            if (store.get("grantCountingStart") == null) store.write(mapOf("grantCountingStart" to "${LocalDate.now()}|${ZoneId.systemDefault().id}"))
        }
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
        gateCoverage = GateCoverage(this)
        runCatching {
            val appOps = getSystemService(AppOpsManager::class.java)
            usageObserver = AppOpsManager.OnOpChangedListener { _, changedPackage ->
                if (changedPackage == packageName) runCatching { invalidateUsageDay(comparisonOnly = true) }
            }.also { appOps.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, packageName, it) }
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { runCatching { invalidateUsageDay() } }
            }, IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }, RECEIVER_NOT_EXPORTED)
        }.onFailure { processStartedAt = Long.MAX_VALUE }
        initialized = true
    }
    private fun invalidateUsageDay(comparisonOnly: Boolean = false) {
        val zone = ZoneId.systemDefault()
        val prefs = getSharedPreferences("usage_partial", Context.MODE_PRIVATE)
        val day = "${LocalDate.now(zone)}|${zone.id}"
        val edit = prefs.edit().putBoolean("${if (comparisonOnly) "comparison-invalid" else "invalid"}:$day", true)
        if (!comparisonOnly) edit.putInt("revision:$day", prefs.getInt("revision:$day", 0) + 1)
        edit.apply()
        if (!comparisonOnly) runCatching { store.write(mapOf("grantCountingStart" to "${LocalDate.now(zone)}|${zone.id}")) }
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
