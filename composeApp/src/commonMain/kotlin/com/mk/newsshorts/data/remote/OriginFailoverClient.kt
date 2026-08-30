package com.mk.newsshorts.data.remote

import com.mk.newsshorts.data.local.OriginPreferenceStore
import com.mk.newsshorts.data.local.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException

/**
 * Routes one backend resource across equivalent static origins.
 *
 * Only an unreachable host or a server failure is ambiguous enough to try a
 * mirror. A 4xx response is the host's answer and returns immediately, so a
 * missing publish cannot be hidden by a different host's stale copy.
 */
class OriginFailoverClient(
    private val httpClient: HttpClient,
    private val apiConfig: ApiConfig,
    private val preferenceStore: OriginPreferenceStore,
    private val nowMillis: () -> Long = ::currentTimeMillis,
    private val primaryRetryIntervalMillis: Long = DEFAULT_PRIMARY_RETRY_INTERVAL_MILLIS,
) {
    private var activeOrigin: String = preferenceStore.preferredOrigin()
        ?.trimEnd('/')
        ?.takeIf(apiConfig.origins::contains)
        ?: apiConfig.primaryOrigin

    // A remembered mirror gets the first call next launch. The primary earns
    // another attempt later, after the reader has already received fresh news.
    private var lastPrimaryAttemptAtMillis: Long = nowMillis()

    suspend fun get(path: String): HttpResponse {
        val requestStartedAt = nowMillis()
        val primaryIsDue = activeOrigin != apiConfig.primaryOrigin &&
            requestStartedAt - lastPrimaryAttemptAtMillis >= primaryRetryIntervalMillis
        val candidates = orderedCandidates(primaryIsDue)
        var lastServerFailure: HttpResponse? = null
        var lastConnectionFailure: Exception? = null

        for (origin in candidates) {
            if (origin == apiConfig.primaryOrigin) {
                lastPrimaryAttemptAtMillis = requestStartedAt
            }
            try {
                val response = httpClient.get(apiConfig.url(origin, path))
                if (response.status.value >= 500) {
                    lastServerFailure = response
                    continue
                }
                remember(origin)
                return response
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                lastConnectionFailure = exception
            }
        }

        return lastServerFailure ?: throw lastConnectionFailure
            ?: IllegalStateException("No backend origin was attempted")
    }

    private fun orderedCandidates(primaryIsDue: Boolean): List<String> = when {
        activeOrigin == apiConfig.primaryOrigin -> apiConfig.origins
        primaryIsDue -> listOf(apiConfig.primaryOrigin, activeOrigin) +
            apiConfig.origins.filter { it != apiConfig.primaryOrigin && it != activeOrigin }
        else -> listOf(activeOrigin) + apiConfig.origins.filter { it != activeOrigin }
    }

    private fun remember(origin: String) {
        if (origin == activeOrigin) return
        activeOrigin = origin
        // Losing this hint is harmless; it must never turn delivered news into
        // a failed request on a platform whose settings write is unavailable.
        runCatching { preferenceStore.savePreferredOrigin(origin) }
    }

    companion object {
        const val DEFAULT_PRIMARY_RETRY_INTERVAL_MILLIS: Long = 15 * 60 * 1000L
    }
}
