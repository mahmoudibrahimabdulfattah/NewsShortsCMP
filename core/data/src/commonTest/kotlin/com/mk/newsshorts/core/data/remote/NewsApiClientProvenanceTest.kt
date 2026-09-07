package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.contract.feed.FeedArticleDto
import com.mk.newsshorts.core.contract.feed.FeedResponse
import com.mk.newsshorts.core.domain.OriginPreferenceStore
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsApiClientProvenanceTest {

    @Test
    fun `maps supplied feed provenance without author fallback`() {
        runTest {
            val client = clientFor(
                FeedArticleDto(
                    id = 1L,
                    title = "Headline",
                    summary = "Summary",
                    url = "https://example.com/story",
                    imageUrl = null,
                    sourceName = "Example News",
                    language = "en",
                    category = "general",
                    publishedAt = 1_700_000_000_000L,
                    author = "Jane Reporter",
                    licenseName = "CC BY 4.0",
                    licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
                    textAttribution = "AI-generated summary.",
                )
            )

            try {
                val result = client.api.fetchTopHeadlines(NewsCategory.GENERAL, "us")
                assertTrue(result is NewsResult.Success)
                val article = result.data.articles.single()
                assertEquals("Jane Reporter", article.author)
                assertEquals("CC BY 4.0", article.licenseName)
                assertEquals("https://creativecommons.org/licenses/by/4.0/", article.licenseUrl)
                assertEquals("AI-generated summary.", article.textAttribution)
            } finally {
                client.httpClient.close()
            }
        }
    }

    @Test
    fun `does not use source name when feed author is absent`() {
        runTest {
            val client = clientFor(
                FeedArticleDto(
                    id = 1L,
                    title = "Headline",
                    summary = "Summary",
                    url = "https://example.com/story",
                    imageUrl = null,
                    sourceName = "Example News",
                    language = "en",
                    category = "general",
                    publishedAt = 1_700_000_000_000L,
                )
            )

            try {
                val result = client.api.fetchTopHeadlines(NewsCategory.GENERAL, "us")
                assertTrue(result is NewsResult.Success)
                val article = result.data.articles.single()
                assertEquals("Example News", article.source.name)
                assertNull(article.author)
            } finally {
                client.httpClient.close()
            }
        }
    }

    private fun clientFor(article: FeedArticleDto): TestClient {
        val body = Json { encodeDefaults = true }.encodeToString(
            FeedResponse(articles = listOf(article), total = 1)
        )
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = ApiConfig(listOf("https://primary.test"))
        return TestClient(
            httpClient = httpClient,
            api = NewsApiClient(
                originClient = OriginFailoverClient(httpClient, config, InMemoryOriginPreferenceStore()),
                apiConfig = config,
            ),
        )
    }

    private data class TestClient(
        val httpClient: HttpClient,
        val api: NewsApiClient,
    )

    private class InMemoryOriginPreferenceStore : OriginPreferenceStore {
        override fun preferredOrigin(): String? = null
        override fun savePreferredOrigin(origin: String) = Unit
    }
}
