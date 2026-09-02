package com.mk.newsshorts.core.domain.config

import com.mk.newsshorts.core.contract.config.AppConfigDto
import com.mk.newsshorts.core.model.config.RequiredUpdate

interface RemoteConfigClient {
    suspend fun fetch(): AppConfigDto?
    suspend fun requiredUpdate(): RequiredUpdate?
}
