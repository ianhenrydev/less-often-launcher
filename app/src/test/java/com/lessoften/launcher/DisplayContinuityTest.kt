package com.lessoften.launcher

import org.junit.Assert.*
import org.junit.Test

class DisplayContinuityTest {
    @Test fun activityRootGapDoesNotRestoreColorOrRecaptureBaseline() {
        val store = MemoryStore()
        val colors = mutableMapOf("accessibility_display_daltonizer" to "12", "accessibility_display_daltonizer_enabled" to "0")
        var writes = 0
        val journal = DisplayJournal(store, object : ColorSettings {
            override fun read(key: String) = colors[key]
            override fun put(key: String, value: String?) { writes++; if (value == null) colors.remove(key) else colors[key] = value }
        })
        val context = DisplayContinuity()
        for ((kind, now) in listOf(DisplayContext.Restricted to 0L, DisplayContext.Uncertain to 20L,
                DisplayContext.Uncertain to 350L, DisplayContext.Restricted to 450L, DisplayContext.Restricted to 500L)) {
            if (context.useGrayscale(kind, now)) journal.apply() else journal.restore()
        }
        assertEquals(2, writes)
        assertEquals("v12", store.get("baseline:accessibility_display_daltonizer"))
        assertEquals("v0", store.get("baseline:accessibility_display_daltonizer_enabled"))
        if (!context.useGrayscale(DisplayContext.Permitted, 510)) journal.restore()
        assertEquals(mapOf("accessibility_display_daltonizer" to "12", "accessibility_display_daltonizer_enabled" to "0"), colors)
    }
    @Test fun homeOrPermittedAppRestoresImmediatelyEvenWithinGap() {
        val context = DisplayContinuity()
        assertTrue(context.useGrayscale(DisplayContext.Restricted, 0))
        assertTrue(context.useGrayscale(DisplayContext.Uncertain, 1))
        assertFalse(context.useGrayscale(DisplayContext.Permitted, 2))
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 3))
        assertNull(context.recheckDelay(3))
    }
    @Test fun duplicateAmbiguousEventsCannotExtendBoundedGrace() {
        val context = DisplayContinuity()
        context.useGrayscale(DisplayContext.Restricted, 0)
        assertTrue(context.useGrayscale(DisplayContext.Uncertain, 100))
        assertEquals(1000L, context.recheckDelay(100))
        assertTrue(context.useGrayscale(DisplayContext.Uncertain, 1099))
        assertEquals(1L, context.recheckDelay(1099))
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 1100))
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 2000))
        assertNull(context.recheckDelay(2000))
    }
    @Test fun missingWindowNeverCreatesRestrictedContext() {
        val context = DisplayContinuity()
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 0))
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 500))
    }
    @Test fun lockAndServiceResetDoNotRetainGrayscaleOnWake() {
        val context = DisplayContinuity(initiallyRestricted = true)
        assertFalse(context.useGrayscale(DisplayContext.Inactive, 0))
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 100))
        context.useGrayscale(DisplayContext.Restricted, 200)
        context.reset()
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 201))
    }
    @Test fun restartCanReconcilePendingJournalWithoutFlashingAndStillRepairStaleJournal() {
        val context = DisplayContinuity(initiallyRestricted = true)
        assertTrue(context.useGrayscale(DisplayContext.Uncertain, 0))
        assertTrue(context.useGrayscale(DisplayContext.Restricted, 500))
        assertTrue(context.useGrayscale(DisplayContext.Uncertain, 1000))
        assertFalse(context.useGrayscale(DisplayContext.Uncertain, 2000))
    }
}
