package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.config.FeedCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleCategoryInferenceTest {

    @Test
    fun `RSS taxonomy from a general feed can supply specialised evidence`() {
        assertEquals(
            setOf("sports", "technology"),
            inferArticleCategories(
                labels = listOf("الرياضة", "Technology"),
                articleUrl = "https://example.com/articles/opaque-id",
            ),
        )
    }

    @Test
    fun `a complete category path segment supplies evidence`() {
        assertEquals(
            setOf("science", "health"),
            inferArticleCategories(
                labels = emptyList(),
                articleUrl = "https://example.com/science_and_health/article-id",
            ),
        )
    }

    @Test
    fun `the combined CNN feed supplies both science and health candidates`() {
        val combined = FeedCatalog.sources.single { "science_and_health" in it.url }

        assertEquals(setOf("health", "science"), combined.categories)
    }

    @Test
    fun `every category has at least two source feeds`() {
        val sourceCountByCategory = FeedCatalog.sources
            .flatMap { it.categories }
            .groupingBy { it }
            .eachCount()

        assertTrue(
            FeedCatalog.categories.all { sourceCountByCategory.getOrDefault(it, 0) >= 2 },
            "Source coverage: $sourceCountByCategory",
        )
    }

    @Test
    fun `a topic word inside a slug is not treated as source taxonomy`() {
        assertTrue(
            inferArticleCategories(
                labels = emptyList(),
                articleUrl = "https://example.com/business/sports-car-sales-rise",
            ) == setOf("business")
        )
    }
}
