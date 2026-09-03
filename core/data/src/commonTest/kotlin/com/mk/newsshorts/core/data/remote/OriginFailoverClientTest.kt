package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.domain.OriginPreferenceStore
import com.mk.newsshorts.core.model.NewsError
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OriginFailoverClientTest {

    @Test
    fun `a server failure falls through to the mirror`() = runTest {
        val requestedHosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedHosts += request.url.host
            if (request.url.host == PRIMARY_HOST) {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond("healthy", HttpStatusCode.OK)
            }
        }
        val client = HttpClient(engine)

        val response = failoverClient(client).get("/v1/app.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(PRIMARY_HOST, MIRROR_HOST), requestedHosts)
        client.close()
    }

    @Test
    fun `a connection failure falls through to the mirror`() = runTest {
        val requestedHosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedHosts += request.url.host
            if (request.url.host == PRIMARY_HOST) {
                error("primary connect failed")
            }
            respond("healthy", HttpStatusCode.OK)
        }
        val client = HttpClient(engine)

        val response = failoverClient(client).get("/v1/app.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(PRIMARY_HOST, MIRROR_HOST), requestedHosts)
        client.close()
    }

    @Test
    fun `a missing resource does not consult the mirror`() = runTest {
        val requestedHosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedHosts += request.url.host
            respond("missing", HttpStatusCode.NotFound)
        }
        val client = HttpClient(engine)

        val response = failoverClient(client).get("/v1/feed/missing.json")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(listOf(PRIMARY_HOST), requestedHosts)
        client.close()
    }

    @Test
    fun `the working mirror is first after a new launch`() = runTest {
        val store = FakeOriginPreferenceStore()
        val firstHosts = mutableListOf<String>()
        val firstEngine = MockEngine { request ->
            firstHosts += request.url.host
            if (request.url.host == PRIMARY_HOST) {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond("healthy", HttpStatusCode.OK)
            }
        }
        val firstHttpClient = HttpClient(firstEngine)
        failoverClient(firstHttpClient, store).get("/v1/app.json")
        firstHttpClient.close()

        val nextLaunchHosts = mutableListOf<String>()
        val nextEngine = MockEngine { request ->
            nextLaunchHosts += request.url.host
            respond("healthy", HttpStatusCode.OK)
        }
        val nextHttpClient = HttpClient(nextEngine)
        failoverClient(nextHttpClient, store).get("/v1/app.json")

        assertEquals(MIRROR_ORIGIN, store.origin)
        assertEquals(listOf(PRIMARY_HOST, MIRROR_HOST), firstHosts)
        assertEquals(listOf(MIRROR_HOST), nextLaunchHosts)
        nextHttpClient.close()
    }

    @Test
    fun `the primary is reclaimed after its retry interval`() = runTest {
        var now = 0L
        val store = FakeOriginPreferenceStore(MIRROR_ORIGIN)
        val requestedHosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedHosts += request.url.host
            respond("healthy", HttpStatusCode.OK)
        }
        val client = HttpClient(engine)
        val failover = failoverClient(
            client = client,
            store = store,
            nowMillis = { now },
            retryIntervalMillis = 100L,
        )

        failover.get("/v1/app.json")
        now = 100L
        failover.get("/v1/app.json")
        failover.get("/v1/app.json")

        assertEquals(listOf(MIRROR_HOST, PRIMARY_HOST, PRIMARY_HOST), requestedHosts)
        assertEquals(PRIMARY_ORIGIN, store.origin)
        client.close()
    }

    @Test
    fun `all unreachable origins keep the existing offline result`() = runTest {
        val engine = MockEngine { request -> error("${request.url.host} connect failed") }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = config()
        val api = NewsApiClient(
            originClient = OriginFailoverClient(httpClient, config, FakeOriginPreferenceStore()),
            apiConfig = config,
        )

        val result = api.fetchTopHeadlines(category = null, country = "us")

        val failure = assertIs<NewsResult.Error>(result)
        assertEquals(NewsError.NetworkError, failure.error)
        httpClient.close()
    }

    private fun failoverClient(
        client: HttpClient,
        store: FakeOriginPreferenceStore = FakeOriginPreferenceStore(),
        nowMillis: () -> Long = { 0L },
        retryIntervalMillis: Long = 100L,
    ): OriginFailoverClient = OriginFailoverClient(
        httpClient = client,
        apiConfig = config(),
        preferenceStore = store,
        nowMillis = nowMillis,
        primaryRetryIntervalMillis = retryIntervalMillis,
    )

    private fun config(): ApiConfig = ApiConfig(listOf(PRIMARY_ORIGIN, MIRROR_ORIGIN))

    private class FakeOriginPreferenceStore(
        var origin: String? = null,
    ) : OriginPreferenceStore {
        override fun preferredOrigin(): String? = origin

        override fun savePreferredOrigin(origin: String) {
            this.origin = origin
        }
    }

    private companion object {
        const val PRIMARY_HOST = "primary.test"
        const val MIRROR_HOST = "mirror.test"
        const val PRIMARY_ORIGIN = "https://$PRIMARY_HOST"
        const val MIRROR_ORIGIN = "https://$MIRROR_HOST"
    }
}
