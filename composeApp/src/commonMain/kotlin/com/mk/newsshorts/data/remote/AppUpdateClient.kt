package com.mk.newsshorts.data.remote

import com.mk.newsshorts.config.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/**
 * Asks the backend whether this build is still allowed to run.
 *
 * The rule lives on the server because the app cannot be changed once it is
 * installed: a build that breaks against a new feed shape has to be stopped
 * from outside itself.
 */
class AppUpdateClient(
    private val httpClient: HttpClient,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) {

    /**
     * Null when nothing is required — including every failure case.
     *
     * A reader offline, or on a flaky connection, or hitting a half-published
     * file must not be locked out of the app they already have. This gate only
     * ever closes on a clear answer from the server.
     */
    suspend fun requiredUpdate(): RequiredUpdate? {
        val config = runCatching { httpClient.get(ApiConfig.appConfigUrl()).body<AppConfigDto>() }
            .getOrNull() ?: return null
        return requiredUpdateFor(config, currentVersionCode)
    }
}

/**
 * The decision itself, kept free of the network so the rule that can lock every
 * reader out of the app is testable on its own.
 */
fun requiredUpdateFor(config: AppConfigDto, currentVersionCode: Int): RequiredUpdate? {
    if (currentVersionCode >= config.minSupportedVersionCode) return null
    // Blocking with no way forward would be worse than the outdated build, so
    // an unusable store link means no gate.
    val storeUrl = config.storeUrl.takeIf { it.startsWith("https://") } ?: return null
    return RequiredUpdate(storeUrl = storeUrl)
}

/** Present only when this build is below the minimum the backend still serves. */
data class RequiredUpdate(val storeUrl: String)

@Serializable
data class AppConfigDto(
    val minSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val storeUrl: String = "",
)
