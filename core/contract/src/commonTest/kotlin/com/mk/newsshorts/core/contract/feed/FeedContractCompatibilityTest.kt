package com.mk.newsshorts.core.contract.feed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedContractCompatibilityTest {

    @Test
    fun `feed article json without provenance still deserializes`() {
        val json = """
            {
              "articles": [
                {
                  "id": 1,
                  "title": "Headline",
                  "summary": "Summary",
                  "url": "https://example.com/story",
                  "imageUrl": null,
                  "sourceName": "Example News",
                  "language": "en",
                  "category": "general",
                  "publishedAt": 1700000000000
                }
              ],
              "total": 1
            }
        """.trimIndent()

        val response = Json.decodeFromString<FeedResponse>(json)
        val article = response.articles.single()

        assertEquals("Example News", article.sourceName)
        assertNull(article.author)
        assertNull(article.licenseName)
        assertNull(article.licenseUrl)
        assertNull(article.textAttribution)
    }
}
