package com.mk.newsshorts.core.data.remote

actual fun isWebPlatform(): Boolean = false


actual fun adjustBaseUrlForPlatform(url: String): String = url
