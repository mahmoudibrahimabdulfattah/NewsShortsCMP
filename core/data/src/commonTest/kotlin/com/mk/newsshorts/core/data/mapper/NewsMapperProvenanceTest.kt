package com.mk.newsshorts.core.data.mapper

import com.mk.newsshorts.core.data.remote.ArticleDto
import com.mk.newsshorts.core.data.remote.NewsApiResponse
import com.mk.newsshorts.core.data.remote.SourceDto
import com.mk.newsshorts.core.model.NewsCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewsMapperProvenanceTest {

    @Test
    fun `maps supplied author and provenance to the article`() {
        val article = NewsMapper.mapToDomain(
            response = response(
                author = "Jane Reporter",
                licenseName = "CC BY 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
                textAttribution = "AI-generated summary.",
            ),
            category = NewsCategory.GENERAL,
        ).single()

        assertEquals("Jane Reporter", article.author?.value)
        assertEquals("CC BY 4.0", article.license?.name)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", article.license?.url)
        assertEquals("AI-generated summary.", article.textAttribution)
    }

    @Test
    fun `does not map source name as an absent author`() {
        val article = NewsMapper.mapToDomain(
            response = response(author = null),
            category = NewsCategory.GENERAL,
        ).single()

        assertEquals("Example News", article.source.name.value)
        assertNull(article.author)
    }

    private fun response(
        author: String?,
        licenseName: String? = null,
        licenseUrl: String? = null,
        textAttribution: String? = null,
    ) = NewsApiResponse(
        status = "ok",
        totalResults = 1,
        articles = listOf(
            ArticleDto(
                source = SourceDto(id = "example", name = "Example News"),
                author = author,
                title = "Headline",
                description = "Summary",
                url = "https://example.com/story",
                urlToImage = null,
                publishedAt = "2026-01-01T00:00:00Z",
                content = "Summary",
                category = "general",
                licenseName = licenseName,
                licenseUrl = licenseUrl,
                textAttribution = textAttribution,
            )
        ),
    )
}
