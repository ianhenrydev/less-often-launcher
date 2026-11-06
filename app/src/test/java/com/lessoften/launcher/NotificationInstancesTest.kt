package com.lessoften.launcher

import org.junit.Assert.*
import org.junit.Test
import android.app.Notification

class NotificationInstancesTest {
    @Test fun baselineUpdatesRemovalAndReconnect() {
        val instances = NotificationInstances()
        instances.connect(listOf("existing"))
        assertFalse(instances.posted("existing", true))
        assertTrue(instances.posted("new", true))
        assertFalse(instances.posted("new", true))
        instances.removed("new")
        assertTrue(instances.posted("new", true))
        instances.disconnect()
        assertFalse(instances.posted("missed", true))
        instances.connect(listOf("new", "missed"))
        assertFalse(instances.posted("missed", true))
    }
    @Test fun ineligibleInstancesAreStillDeduplicated() {
        val instances = NotificationInstances(); instances.connect(emptyList())
        assertFalse(instances.posted("ongoing", false))
        assertFalse(instances.posted("ongoing", true))
        instances.removed("ongoing")
        assertTrue(instances.posted("ongoing", true))
    }
    @Test fun excludesOwnPersistentAndGroupSummaryNotifications() {
        assertTrue(notificationEligible("other", "self", 0))
        assertFalse(notificationEligible("self", "self", 0))
        listOf(Notification.FLAG_ONGOING_EVENT, Notification.FLAG_NO_CLEAR, Notification.FLAG_FOREGROUND_SERVICE, Notification.FLAG_GROUP_SUMMARY).forEach {
            assertFalse(notificationEligible("other", "self", it))
        }
    }
}
