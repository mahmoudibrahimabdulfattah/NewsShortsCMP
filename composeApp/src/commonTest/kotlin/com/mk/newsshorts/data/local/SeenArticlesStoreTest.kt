package com.mk.newsshorts.data.local

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This is what stops a returning reader from seeing yesterday's top story
 * again. It has to survive a malformed value on disk without losing the whole
 * list, and it has to stay under the JVM target's per-value size limit
 * (`java.util.prefs`, ~8 KB) forever, not just on day one.
 */
class SeenArticlesStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an empty value decodes to an empty set`() {
        assertEquals(emptySet(), decodeSeenHashes("", json))
    }

    @Test
    fun `a malformed blob decodes to an empty set rather than throwing`() {
        assertEquals(emptySet(), decodeSeenHashes("{not valid json", json))
    }

    @Test
    fun `a well-formed blob round-trips`() {
        val encoded = json.encodeToString(SeenArticlesDto(listOf(3, 2, 1)))
        assertEquals(setOf(1, 2, 3), decodeSeenHashes(encoded, json))
    }

    @Test
    fun `the new hash goes first`() {
        val result = orderedWithCap(newHash = 99, existing = setOf(1, 2, 3))
        assertEquals(99, result.first())
    }

    @Test
    fun `growth stays capped at 400`() {
        val existing = (1..500).toSet()
        val result = orderedWithCap(newHash = 0, existing = existing)
        assertEquals(MAX_SEEN, result.size)
    }

    @Test
    fun `the cap keeps the newest rather than an arbitrary subset`() {
        // Newest-first order, as markSeen always writes it: the most recently
        // seen article must never be the one the cap drops.
        val existing = (1..450).toList()
        val result = orderedWithCap(newHash = 0, existing = existing.toSet())
        assertTrue(0 in result)
        assertTrue(1 in result, "the most recently seen entry before this call was dropped")
    }
}
