package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.contract.config.AppConfigDto
import com.mk.newsshorts.core.domain.config.RemoteConfigClient
import com.mk.newsshorts.core.model.config.RequiredUpdate
import com.mk.newsshorts.core.model.config.requiredUpdateFor
import com.mk.newsshorts.config.BuildConfig
import io.ktor.client.call.body

/**
 * The few decisions the backend keeps control of: whether this build may still
 * run, and what a compromised device should do.
 *
 * They live on the server because an installed app cannot be changed from here.
 * One request covers both, fetched once per launch.
 */
class DefaultRemoteConfigClient(
    private val originClient: OriginFailoverClient,
    private val apiConfig: ApiConfig,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : RemoteConfigClient {

    /** Null on any failure — see [requiredUpdateFor] for why that matters. */
    override suspend fun fetch(): AppConfigDto? =
        runCatching { originClient.get(apiConfig.appConfigPath()).body<AppConfigDto>() }.getOrNull()

    override suspend fun requiredUpdate(): RequiredUpdate? =
        fetch()?.let { config -> requiredUpdateFor(config, currentVersionCode) }
}
