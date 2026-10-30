package com.lessoften.launcher

import org.junit.Assert.*
import org.junit.Test

private class FakeColors : ColorSettings {
    val values = mutableMapOf<String, String>()
    var writes = 0
    var failKey: String? = null
    override fun read(key: String) = values[key]
    override fun put(key: String, value: String?) {
        check(key != failKey)
        writes++
        if (value == null) values.remove(key) else values[key] = value
    }
}
class DisplayJournalTest {
    private val mode = "accessibility_display_daltonizer"
    private val enabled = "accessibility_display_daltonizer_enabled"
    @Test fun absentAndNonDefaultBaselinesRestoreExactly() {
        for (initial in listOf(emptyMap(), mapOf(mode to "12", enabled to "1"), mapOf(mode to "0", enabled to "0"))) {
            val store = MemoryStore(); val colors = FakeColors().apply { values.putAll(initial) }
            val display = DisplayJournal(store, colors)
            display.apply(); assertEquals(mapOf(mode to "0", enabled to "1"), colors.values)
            display.restore(); assertEquals(initial, colors.values); assertFalse(display.active())
        }
    }
    @Test fun duplicateEventsAndRestrictedToRestrictedKeepOriginalBaseline() {
        val store = MemoryStore(); val colors = FakeColors().apply { values[mode] = "12"; values[enabled] = "0" }
        var display = DisplayJournal(store, colors)
        display.apply(); val writes = colors.writes
        repeat(20) { display.apply() }; assertEquals(writes, colors.writes)
        display = DisplayJournal(store, colors); display.apply() // Process restart while Restricted
        display.restore(); assertEquals(mapOf(mode to "12", enabled to "0"), colors.values)
    }
    @Test fun interruptedPartialWriteCanRecoverWithoutInventingDefaults() {
        val store = MemoryStore(); val colors = FakeColors().apply { failKey = enabled }
        runCatching { DisplayJournal(store, colors).apply() }
        assertEquals("active", store.get("colorJournal"))
        colors.failKey = null
        DisplayJournal(store, colors).restore()
        assertTrue(colors.values.isEmpty())
    }
    @Test fun concurrentChangeStopsRepeatedWritesAndDoesNotClobberUser() {
        val store = MemoryStore(); val colors = FakeColors()
        val display = DisplayJournal(store, colors); display.apply()
        colors.values[mode] = "11"
        val writes = colors.writes
        repeat(10) { display.apply() }; display.restore()
        assertEquals("11", colors.values[mode]); assertNotNull(display.problem)
        // Restore may repair the still-owned enable key but must retain the unresolved journal.
        assertTrue(display.active()); assertTrue(colors.writes <= writes + 1)
    }
    @Test fun baselineCommitFailurePreventsAnyDisplayWrite() {
        val store = MemoryStore().apply { fail = true }; val colors = FakeColors()
        runCatching { DisplayJournal(store, colors).apply() }
        assertEquals(0, colors.writes)
    }
    @Test fun pendingWriteCanResumeWhileRestrictedAfterProcessDeath() {
        val store = MemoryStore(); val colors = FakeColors().apply { failKey = enabled }
        runCatching { DisplayJournal(store, colors).apply() }
        colors.failKey = null
        val recovered = DisplayJournal(store, colors); recovered.apply()
        assertNull(recovered.problem)
        assertEquals(mapOf(mode to "0", enabled to "1"), colors.values)
        recovered.restore(); assertTrue(colors.values.isEmpty())
    }
    @Test fun externallyChangingAnInitiallyMatchingKeyIsAlsoAConflict() {
        val store = MemoryStore(); val colors = FakeColors().apply { values[mode] = "0"; values[enabled] = "0" }
        val display = DisplayJournal(store, colors); display.apply()
        colors.values[mode] = "12"
        val writes = colors.writes
        repeat(5) { display.apply() }
        assertEquals("12", colors.values[mode]); assertEquals(writes, colors.writes)
        assertNotNull(display.problem)
    }
}
