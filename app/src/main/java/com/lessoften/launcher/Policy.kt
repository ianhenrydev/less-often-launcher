package com.lessoften.launcher

import java.util.Locale
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

enum class Bucket { Primary, Secondary, Restricted }
data class ConfiguredApp(val packageName: String, val name: String, val bucket: Bucket)
class BucketPolicy(val entries: List<ConfiguredApp>, val durationMillis: Long = 600_000) {
    init {
        require(entries.map { it.packageName }.distinct().size == entries.size) { "Duplicate package assignment" }
        require(durationMillis == 600_000L) { "The approved access window is 600 seconds" }
    }
    private val byPackage = entries.associateBy { it.packageName }
    fun bucket(packageName: String) = byPackage[packageName]?.bucket ?: Bucket.Restricted
    fun configured(packageName: String) = byPackage[packageName]
    companion object {
        fun parse(text: String): BucketPolicy {
            val json = JSONObject(text.removePrefix("\uFEFF"))
            require(json.getInt("schemaVersion") == 1)
            require(json.getString("defaultBucket") == "Restricted")
            val entries = Bucket.entries.flatMap { bucket ->
                val array = json.getJSONArray(bucket.name.lowercase(Locale.ROOT))
                (0 until array.length()).map { i -> array.getJSONObject(i).let {
                    ConfiguredApp(it.getString("package"), it.getString("name"), bucket)
                } }
            }
            return BucketPolicy(entries, json.getLong("sessionDurationSeconds") * 1000)
        }
    }
}

interface DurableStore {
    fun get(key: String): String?
    /** Synchronous, atomic, durable update. Throw on failure before exposing any grant. */
    fun write(values: Map<String, String?>)
}
interface ElapsedClock { fun now(): Long; fun boot(): String? }
class Sessions(private val store: DurableStore, private val clock: ElapsedClock, private val duration: Long) {
    @Synchronized fun remaining(pkg: String): Long {
        val boot = clock.boot() ?: return 0
        val parts = store.get("session:$pkg")?.split('|') ?: return 0
        val deadline = parts.getOrNull(1)?.toLongOrNull() ?: return 0
        val now = clock.now()
        if (parts[0] != boot || deadline <= now || deadline - now > duration) return 0
        return deadline - now
    }
    @Synchronized fun grant(pkg: String) {
        val boot = requireNotNull(clock.boot()) { "Cannot determine boot identity; access remains locked" }
        store.write(mapOf("session:$pkg" to "$boot|${clock.now() + duration}"))
    }
}
data class Quote(val id: String, val text: String, val attribution: String, val license: String, val source: String)
class Quotes(private val corpus: List<Quote>, private val store: DurableStore) {
    init {
        require(corpus.size >= 2)
        require(corpus.map { it.id }.distinct().size == corpus.size)
        require(corpus.map { normalize(it.text) }.distinct().size == corpus.size)
        require(corpus.all { normalize(it.text).split(' ').size in 8..16 && it.license.isNotBlank() && it.source.isNotBlank() })
    }
    @Synchronized fun next(): Quote {
        val choices = corpus.filter { it.id != store.get("lastQuote") }
        val quote = choices[Random.nextInt(choices.size)]
        store.write(mapOf("lastQuote" to quote.id))
        return quote
    }
    companion object {
        fun normalize(text: String) = buildString {
            var whitespace = false
            text.forEach { character ->
                if (character.isWhitespace()) { if (isNotEmpty()) whitespace = true }
                else {
                    if (whitespace) { append(' '); whitespace = false }
                    append(character)
                }
            }
        }.lowercase(Locale.ROOT)
        fun matches(input: String, quote: Quote) = normalize(input) == normalize(quote.text)
        fun parse(text: String): List<Quote> = JSONArray(text).let { array ->
            (0 until array.length()).map { i -> array.getJSONObject(i).let {
                Quote(it.getString("id"), it.getString("text"), it.getString("attribution"), it.getString("license"), it.getString("source"))
            } }
        }
    }
}

/** Null is an absent secure setting, not a default value. Journal each intent before writing. */
interface ColorSettings { fun read(key: String): String?; fun put(key: String, value: String?) }
class DisplayJournal(private val store: DurableStore, private val settings: ColorSettings) {
    companion object {
        val transform = linkedMapOf("accessibility_display_daltonizer" to "0", "accessibility_display_daltonizer_enabled" to "1")
    }
    var problem: String? = null
        private set
    private fun encode(value: String?) = value?.let { "v$it" } ?: "absent"
    private fun decode(value: String) = if (value == "absent") null else value.removePrefix("v")
    fun active() = store.get("colorJournal") == "active"
    @Synchronized fun apply() {
        if (!active()) {
            store.write(transform.keys.associate { "baseline:$it" to encode(settings.read(it)) } + mapOf("colorJournal" to "active"))
        }
        if (store.get("colorConflict") != null) { problem = store.get("colorConflict"); return }
        for ((key, value) in transform) {
            val current = settings.read(key)
            val baseline = decode(requireNotNull(store.get("baseline:$key")))
            val confirmed = store.get("confirmed:$key")
            if (confirmed != null && current != value) { conflict(); return }
            if (confirmed == null && current != value && current != baseline) { conflict(); return }
            if (current != value) {
                store.write(mapOf("planned:$key" to value))
                settings.put(key, value)
            }
            // Confirm also when the baseline already equals the transform: later changes still
            // belong to the user, and must not be overwritten on every foreground event.
            if (confirmed == null) store.write(mapOf("confirmed:$key" to value))
        }
        problem = null
    }
    private fun conflict() {
        val message = "Color correction changed outside Less Often. Automatic writes paused; review the saved baseline in setup/recovery."
        store.write(mapOf("colorConflict" to message)); problem = message
    }
    @Synchronized fun restore() {
        if (!active()) return
        for ((key, owned) in transform.entries.reversed()) {
            val baseline = decode(requireNotNull(store.get("baseline:$key")))
            val current = settings.read(key)
            if (current == baseline) continue
            if (current != owned || store.get("planned:$key") == null) { conflict(); return }
            settings.put(key, baseline)
        }
        store.write(transform.keys.flatMap { listOf("baseline:$it" to null, "planned:$it" to null, "confirmed:$it" to null) }.toMap() +
            mapOf("colorJournal" to null, "colorConflict" to null))
        problem = null
    }
}
