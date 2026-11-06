package com.lessoften.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import android.os.SystemClock

/** Stores counts only. Notification contents never enter app storage. */
class UsageNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile internal var connection: Connection? = null
        internal data class Connection(val wall: Long, val elapsed: Long, val zone: String)
    }
    private val instances = NotificationInstances()
    private val lock = Any()
    private val prefs by lazy { getSharedPreferences("notification_counts", MODE_PRIVATE) }
    override fun onListenerConnected() {
        synchronized(lock) {
            val keys = runCatching { activeNotifications.map { it.key } }.getOrNull()
            if (keys == null) { instances.disconnect(); return }
            instances.connect(keys)
            runCatching {
                prefs.getString("days", null)?.let { previous ->
                    val history = JSONObject(previous)
                    val edit = prefs.edit().remove("days")
                    history.keys().asSequence().forEach { day -> if (!prefs.contains("day:$day")) edit.putInt("day:$day", history.getInt(day)) }
                    edit.apply()
                }
            }
            connection = Connection(System.currentTimeMillis(), SystemClock.elapsedRealtime(), ZoneId.systemDefault().id)
            // A connection cannot recover missed notifications, so this day is partial.
            runCatching { prefs.edit().putBoolean("connected", true).apply() }
            runCatching { record(increment = false) }.onFailure { connection = null }
        }
    }
    override fun onListenerDisconnected() {
        synchronized(lock) { connection = null; instances.disconnect(); runCatching { prefs.edit().putBoolean("connected", false).apply() } }
    }
    override fun onDestroy() { connection = null; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        synchronized(lock) {
            val flags = sbn.notification.flags
            val eligible = notificationEligible(sbn.packageName, packageName, flags)
            if (instances.posted(sbn.key, eligible)) runCatching { record(increment = true) }.onFailure { connection = null }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) synchronized(lock) { instances.removed(sbn.key) }
    }
    private fun record(increment: Boolean) {
        val date = LocalDate.now()
        val key = "$date|${ZoneId.systemDefault().id}"
        val prefKey = "day:$key"
        val edit = prefs.edit().putInt(prefKey, prefs.getInt(prefKey, 0) + if (increment) 1 else 0)
        val connected = connection
        val shifted = connected != null && (connected.zone != ZoneId.systemDefault().id ||
            kotlin.math.abs((System.currentTimeMillis() - connected.wall) - (SystemClock.elapsedRealtime() - connected.elapsed)) > 3_000)
        val invalid = getSharedPreferences("usage_partial", MODE_PRIVATE).getBoolean("invalid:$key", false)
        if (!shifted && !invalid) prefs.all.keys.filter { it.startsWith("day:") }.forEach { old ->
            if (Retention.expired(old, "day:", date)) edit.remove(old)
        }
        edit.apply()
    }
}
