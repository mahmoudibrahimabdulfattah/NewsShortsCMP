package com.mk.newsshorts.data.remote

import com.mk.newsshorts.config.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/**
 * The few decisions the backend keeps control of: whether this build may still
 * run, and what a compromised device should do.
 *
 * They live on the server because an installed app cannot be changed from here.
 * One request covers both, fetched once per launch.
 */
class RemoteConfigClient(
    private val httpClient: HttpClient,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) {

    /** Null on any failure — see [requiredUpdateFor] for why that matters. */
    suspend fun fetch(): AppConfigDto? =
        runCatching { httpClient.get(ApiConfig.appConfigUrl()).body<AppConfigDto>() }.getOrNull()

    suspend fun requiredUpdate(): RequiredUpdate? =
        fetch()?.let { config -> requiredUpdateFor(config, currentVersionCode) }
}

/** Present only when this build is below the minimum the backend still serves. */
data class RequiredUpdate(val storeUrl: String)

/**
 * The decision itself, kept free of the network so the rule that can lock every
 * reader out of the app is testable on its own.
 *
 * Returns null in every ambiguous case. A reader offline, on a flaky
 * connection, or hitting a half-published file must not lose the app they
 * already have — this gate only closes on a clear answer from the server.
 */
fun requiredUpdateFor(config: AppConfigDto, currentVersionCode: Int): RequiredUpdate? {
    if (currentVersionCode >= config.minSupportedVersionCode) return null
    // Blocking with no way forward would be worse than the outdated build, so
    // an unusable store link means no gate.
    val storeUrl = config.storeUrl.takeIf { it.startsWith("https://") } ?: return null
    return RequiredUpdate(storeUrl = storeUrl)
}

@Serializable
data class AppConfigDto(
    val minSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val storeUrl: String = "",
    /** "allow", "warn" or "block" — see [com.mk.newsshorts.security.IntegrityPolicy]. */
    val rootPolicy: String = "warn",
    /**
     * Same values, for emulators and phones with developer options on. Defaults
     * to blocking, which is what a shipped build should do with a copy of
     * itself running in an emulator.
     */
    val emulatorPolicy: String = "block",
)
