package com.mk.newsshorts.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which articles this reader has already read, so a returning reader is shown
 * new stories first instead of the same cards from this morning.
 *
 * Stores a hash of the URL rather than the URL itself — `SavedArticlesStore`
 * already treats `url.hashCode()` as a stable identity, and 400 ints encode to
 * a few kilobytes where 400 URLs would not: the JVM target's
 * `java.util.prefs.Preferences` throws past roughly 8 KB in a single value.
 */
class SeenArticlesStore(
    private val settingsStorage: SettingsStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Set<Int> {
        val raw = settingsStorage.getString(KEY_SEEN_ARTICLES, "")
        return decodeSeenHashes(raw, json)
    }

    /**
     * Records one more read. Reads the whole set back rather than keeping it in
     * memory: this is called once per article read, not on a hot path, and it
     * keeps the store as the single source of truth.
     */
    fun markSeen(url: String) {
        val current = load()
        val hash = url.hashCode()
        if (hash in current) return
        val updated = orderedWithCap(newHash = hash, existing = current)
        runCatching {
            settingsStorage.putString(KEY_SEEN_ARTICLES, json.encodeToString(SeenArticlesDto(updated)))
        }
    }

    private companion object {
        const val KEY_SEEN_ARTICLES: String = "seen_articles"
    }
}

@Serializable
internal data class SeenArticlesDto(val hashes: List<Int> = emptyList())

/**
 * A shape change or a truncated write costs the seen list, not every launch
 * from here on — the feed just looks fresh again. Free of `SettingsStorage` so
 * the failure handling is testable without a real backing store.
 */
internal fun decodeSeenHashes(raw: String, json: Json): Set<Int> {
    if (raw.isBlank()) return emptySet()
    return runCatching {
        json.decodeFromString<SeenArticlesDto>(raw).hashes.toSet()
    }.getOrElse { emptySet() }
}

/**
 * Newest first, capped at [MAX_SEEN]. The cap only ever needs to matter here,
 * on write, so a read never has to know about it. ~4.5 KB as JSON at the cap —
 * well inside the JVM target's `java.util.prefs` per-value limit.
 */
internal fun orderedWithCap(newHash: Int, existing: Set<Int>): List<Int> =
    (listOf(newHash) + existing.toList()).take(MAX_SEEN)

internal const val MAX_SEEN: Int = 400
