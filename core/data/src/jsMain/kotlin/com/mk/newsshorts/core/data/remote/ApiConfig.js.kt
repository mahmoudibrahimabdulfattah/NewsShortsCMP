package com.mk.newsshorts.core.data.remote

actual fun isWebPlatform(): Boolean = true


actual fun adjustBaseUrlForPlatform(url: String): String = url
