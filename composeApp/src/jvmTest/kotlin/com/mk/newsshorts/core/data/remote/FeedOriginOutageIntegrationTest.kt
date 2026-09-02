package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.domain.OriginPreferenceStore
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeedOriginOutageIntegrationTest {

    @Test
    fun `news remains available while the primary is down`() = runTest {
        val primaryRequests = AtomicInteger()
        val mirrorRequests = AtomicInteger()
        val primary = server(status = 503, body = "unavailable", requests = primaryRequests)
        val mirror = server(status = 200, body = healthyFeed(), requests = mirrorRequests)
        primary.start()
        mirror.start()
        val httpClient = createHttpClient()

        try {
            val config = ApiConfig(
                listOf(
                    "http://127.0.0.1:${primary.address.port}",
                    "http://127.0.0.1:${mirror.address.port}",
                ),
            )
            val api = NewsApiClient(
                originClient = OriginFailoverClient(
                    httpClient = httpClient,
                    apiConfig = config,
                    preferenceStore = MemoryOriginPreferenceStore(),
                    nowMillis = { 0L },
                ),
                apiConfig = config,
            )

            repeat(2) {
                val result = assertIs<NewsResult.Success<NewsApiResponse>>(
                    api.fetchTopHeadlines(NewsCategory.GENERAL, "us"),
                )
                assertEquals("Mirror headline", result.data.articles.single().title)
            }

            assertEquals(1, primaryRequests.get())
            assertEquals(2, mirrorRequests.get())
        } finally {
            httpClient.close()
            primary.stop(0)
            mirror.stop(0)
        }
    }

    private fun server(status: Int, body: String, requests: AtomicInteger): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                val bytes = body.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { response -> response.write(bytes) }
            }
        }

    private fun healthyFeed(): String = Json.encodeToString(
        BackendFeedResponse(
            articles = listOf(
                BackendArticleDto(
                    id = 1L,
                    title = "Mirror headline",
                    summary = "The mirror kept the feed readable.",
                    url = "https://example.com/mirror-story",
                    imageUrl = null,
                    sourceName = "Drill source",
                    language = "en",
                    category = "general",
                    publishedAt = 1_700_000_000_000L,
                ),
            ),
            total = 1,
        ),
    )

    private class MemoryOriginPreferenceStore : OriginPreferenceStore {
        private var origin: String? = null

        override fun preferredOrigin(): String? = origin

        override fun savePreferredOrigin(origin: String) {
            this.origin = origin
        }
    }
}
