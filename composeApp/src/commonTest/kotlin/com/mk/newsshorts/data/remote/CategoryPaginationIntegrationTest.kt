package com.mk.newsshorts.data.remote

import com.mk.newsshorts.data.mapper.NewsMapper
import com.mk.newsshorts.data.local.OriginPreferenceStore
import com.mk.newsshorts.domain.feed.appendPage
import com.mk.newsshorts.domain.feed.shouldLoadNextPage
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsResult
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
import kotlin.test.assertTrue

class CategoryPaginationIntegrationTest {

    @Test
    fun `an eleven article category head grows by its next page`() = runTest {
        val json = Json { encodeDefaults = true }
        val head = response(0 until 11, nextPage = "ar-sports-p2.json")
        val second = response(11 until 51, nextPage = null)
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("ar-sports-p2.json")) second else head
            respond(
                content = json.encodeToString(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = ApiConfig(listOf("https://primary.test"))
        val api = NewsApiClient(
            originClient = OriginFailoverClient(client, config, InMemoryOriginPreferenceStore()),
            apiConfig = config,
        )

        val firstResult = api.fetchNewsByLanguage(NewsCategory.SPORTS, "ar")
        assertTrue(firstResult is NewsResult.Success)
        val first = firstResult.data
        val loaded = NewsMapper.mapToDomain(first, NewsCategory.SPORTS)
        assertEquals(11, loaded.size)
        assertTrue(shouldLoadNextPage(6, loaded.size, first.nextPage != null, false, false))

        val nextResult = api.fetchFeedPage(first.nextPage!!)
        assertTrue(nextResult is NewsResult.Success)
        val grown = appendPage(
            loaded,
            NewsMapper.mapToDomain(nextResult.data, NewsCategory.SPORTS),
        )

        assertEquals(51, grown.size)
        assertEquals(51, grown.map { it.articleUrl.value }.toSet().size)
        assertTrue(grown.all { it.category == NewsCategory.SPORTS })
        client.close()
    }

    private fun response(range: IntRange, nextPage: String?) = BackendFeedResponse(
        articles = range.map { index ->
            BackendArticleDto(
                id = index.toLong(),
                title = "Sport $index",
                summary = "Summary $index",
                url = "https://example.com/sport/$index",
                imageUrl = null,
                sourceName = "Sports Source",
                language = "ar",
                category = "sports",
                publishedAt = 1_700_000_000_000L + index,
            )
        },
        total = 51,
        nextPage = nextPage,
    )

    private class InMemoryOriginPreferenceStore : OriginPreferenceStore {
        override fun preferredOrigin(): String? = null
        override fun savePreferredOrigin(origin: String) = Unit
    }
}
