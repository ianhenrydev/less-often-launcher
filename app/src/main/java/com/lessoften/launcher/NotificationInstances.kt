package com.lessoften.launcher

import android.app.Notification

internal fun notificationEligible(packageName: String, ownPackage: String, flags: Int): Boolean =
    packageName != ownPackage && flags and Notification.FLAG_ONGOING_EVENT == 0 &&
        flags and Notification.FLAG_NO_CLEAR == 0 && flags and Notification.FLAG_FOREGROUND_SERVICE == 0 &&
        flags and Notification.FLAG_GROUP_SUMMARY == 0

/** Tracks notification instances, including ineligible active keys, so updates never become new posts. */
class NotificationInstances {
    private val active = mutableSetOf<String>()
    private var connected = false
    fun connect(keys: Collection<String>) { active.clear(); active.addAll(keys); connected = true }
    fun disconnect() { connected = false; active.clear() }
    fun posted(key: String, eligible: Boolean): Boolean {
        if (!connected) return false
        val fresh = active.add(key)
        return fresh && eligible
    }
    fun removed(key: String) { active.remove(key) }
}
