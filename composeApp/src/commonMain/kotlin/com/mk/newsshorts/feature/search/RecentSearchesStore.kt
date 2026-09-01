package com.mk.newsshorts.feature.search

import com.mk.newsshorts.data.local.SettingsStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last few things this reader searched for, kept on this device.
 *
 * Deliberately not part of the account sync that carries saved articles and
 * settings, and deliberately not reported anywhere: search history is the one
 * thing in this app that is a record of what someone was thinking about, and
 * the privacy policy's promise is that no text a reader types leaves the phone.
 * Sync would break that promise as thoroughly as analytics would.
 */
@Serializable
private data class RecentSearchesDto(val queries: List<String> = emptyList())

interface RecentSearches {
    fun load(): List<String>
    fun add(query: String): List<String>
    fun remove(query: String): List<String>
    fun clear()
}

class RecentSearchesStore(
    private val settingsStorage: SettingsStorage
) : RecentSearches {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<String> {
        val raw = settingsStorage.getString(KEY_RECENT_SEARCHES, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<RecentSearchesDto>(raw).queries
        }.getOrElse {
            // A shape change or a truncated write costs the history, not every
            // launch from here on.
            emptyList()
        }
    }

    /**
     * Records [query] at the top and returns the list as it now stands.
     *
     * De-duplicated on the folded form, so searching غزه after غزة moves the
     * one entry rather than creating a second one that looks identical in the
     * list. The text stored is what the reader actually typed — the folded form
     * is for comparing, never for showing back.
     */
    override fun add(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return load()
        val folded = normalizeForSearch(trimmed)
        val updated = (listOf(trimmed) + load().filter { normalizeForSearch(it) != folded })
            .take(MAX_RECENT)
        persist(updated)
        return updated
    }

    override fun remove(query: String): List<String> {
        val folded = normalizeForSearch(query)
        val updated = load().filter { normalizeForSearch(it) != folded }
        persist(updated)
        return updated
    }

    override fun clear() {
        persist(emptyList())
    }

    private fun persist(queries: List<String>) {
        runCatching {
            settingsStorage.putString(
                KEY_RECENT_SEARCHES,
                json.encodeToString(RecentSearchesDto(queries)),
            )
        }
    }

    private companion object {
        const val KEY_RECENT_SEARCHES: String = "recent_searches"

        /** A short list a reader can read at a glance, not a search log. */
        const val MAX_RECENT: Int = 8
    }
}
