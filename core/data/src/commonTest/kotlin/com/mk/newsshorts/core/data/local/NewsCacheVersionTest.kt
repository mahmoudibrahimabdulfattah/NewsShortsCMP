package com.mk.newsshorts.core.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewsCacheVersionTest {

    @Test
    fun `category cache keys leave the contaminated namespace behind`() {
        assertEquals(
            "v2_language_ar_sports",
            NewsLocalDataSource.createCacheKey("language", "ar_sports"),
        )
    }

    @Test
    fun `the caches from before the category fix are deleted`() {
        val store = mutableMapOf(
            "news_cache_index" to
                """{"keys":[{"key":"language_ar_sports","lastAccessTime":1}]}""",
            "news_cache_language_ar_sports" to """{"cacheKey":"language_ar_sports"}""",
            "news_cache_index_v2" to """{"keys":[]}""",
        )

        purgeSupersededCaches(
            currentVersion = 2,
            getString = { key, fallback -> store[key] ?: fallback },
            putString = { key, value -> store[key] = value },
        )

        // Nothing reads these once the key scheme changes, and eviction only
        // ever counts the keys the current index names — so unless they are
        // deleted here they outlive the install.
        assertEquals("", store["news_cache_language_ar_sports"])
        assertEquals("", store["news_cache_index"])
        assertTrue(store["news_cache_index_v2"]!!.isNotEmpty())
    }

    @Test
    fun `an unreadable legacy index is still retired`() {
        val store = mutableMapOf("news_cache_index" to "not json")

        purgeSupersededCaches(
            currentVersion = 2,
            getString = { key, fallback -> store[key] ?: fallback },
            putString = { key, value -> store[key] = value },
        )

        assertEquals("", store["news_cache_index"])
    }
}
