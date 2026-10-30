package com.lessoften.launcher

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MemoryStore : DurableStore {
    val values = mutableMapOf<String, String>()
    var fail = false
    override fun get(key: String) = values[key]
    override fun write(values: Map<String, String?>) {
        check(!fail)
        values.forEach { (k, v) -> if (v == null) this.values.remove(k) else this.values[k] = v }
    }
}
class FakeClock(var elapsed: Long = 1000, var bootId: String? = "boot1") : ElapsedClock {
    override fun now() = elapsed
    override fun boot() = bootId
}
class PolicyTest {
    private fun asset(name: String) = File("src/main/assets/$name").readText()
    @Test fun exactApprovedBucketsAndCaseSensitiveDefaults() {
        val policy = BucketPolicy.parse(asset("app-buckets.json"))
        assertEquals(listOf(6, 57, 40), Bucket.entries.map { bucket -> policy.entries.count { it.bucket == bucket } })
        assertEquals(listOf("Phone", "Messages", "Camera", "Maps", "Clock", "Settings"), policy.entries.filter { it.bucket == Bucket.Primary }.map { it.name })
        assertEquals(Bucket.Secondary, policy.bucket("com.Avelo.app"))
        assertEquals(Bucket.Restricted, policy.bucket("com.avelo.app"))
        assertEquals(Bucket.Restricted, policy.bucket("new.benign.app"))
        assertEquals(Bucket.Restricted, policy.bucket("com.beforesoft.launcher"))
        assertEquals(Bucket.Restricted, policy.bucket("com.android.vending"))
        assertEquals(Bucket.Secondary, policy.bucket("com.cincsystems.ppm1"))
        assertEquals(Bucket.Secondary, policy.bucket("com.comit.caregiver"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateAssignmentsFailClosed() {
        BucketPolicy(listOf(ConfiguredApp("a", "One", Bucket.Primary), ConfiguredApp("a", "Two", Bucket.Secondary)))
    }
    @Test fun independentDurableDeadlinesNeverExtendOnRead() {
        val store = MemoryStore(); val clock = FakeClock()
        var sessions = Sessions(store, clock, 600_000)
        sessions.grant("sleeper"); clock.elapsed += 100_000; sessions.grant("chrome")
        assertEquals(500_000, sessions.remaining("sleeper")); assertEquals(600_000, sessions.remaining("chrome"))
        sessions = Sessions(store, clock, 600_000) // Same-boot process recreation
        assertEquals(500_000, sessions.remaining("sleeper"))
        clock.elapsed += 500_000
        assertEquals(0, sessions.remaining("sleeper")); assertEquals(100_000, sessions.remaining("chrome"))
        clock.elapsed += 100_000; assertEquals(0, sessions.remaining("chrome"))
    }
    @Test fun bootAndCorruptStateNeverManufactureAccess() {
        val store = MemoryStore(); val clock = FakeClock(); val sessions = Sessions(store, clock, 600_000)
        sessions.grant("a"); clock.bootId = "boot2"; assertEquals(0, sessions.remaining("a"))
        clock.bootId = null; assertEquals(0, sessions.remaining("a"))
        clock.bootId = "boot1"; store.values["session:a"] = "boot1|9999999999999"; assertEquals(0, sessions.remaining("a"))
        store.values["session:a"] = "bad"; assertEquals(0, sessions.remaining("a"))
    }
    @Test(expected = IllegalStateException::class) fun failedCommitDoesNotGrant() {
        val store = MemoryStore().apply { fail = true }
        Sessions(store, FakeClock(), 600_000).grant("a")
    }
    @Test fun quotesKeepPunctuationButIgnoreCaseAndWhitespace() {
        val quote = Quotes.parse(asset("quotes.json")).first()
        assertTrue(Quotes.matches("  A QUIET\n pause can   make room for a better choice.  ", quote))
        assertTrue(Quotes.matches("\u00A0A\u2003quiet\tpause can make room for a better choice.\u00A0", quote))
        assertFalse(Quotes.matches("A quiet pause can make room for a better choice", quote))
        assertFalse(Quotes.matches("A quick pause can make room for a better choice.", quote))
    }
    @Test fun canceledAttemptsAndProcessRecreationDoNotRepeatQuotes() {
        val store = MemoryStore(); val corpus = Quotes.parse(asset("quotes.json"))
        var previous = ""
        repeat(500) {
            val current = Quotes(corpus, store).next()
            assertNotEquals(previous, current.id); previous = current.id
            assertTrue(current.text.split(' ').size in 8..16)
        }
    }
}
