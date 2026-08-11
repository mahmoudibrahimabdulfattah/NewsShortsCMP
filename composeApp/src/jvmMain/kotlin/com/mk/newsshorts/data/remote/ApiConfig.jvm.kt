package com.mk.newsshorts.data.remote

actual fun isWebPlatform(): Boolean = false


actual fun adjustBaseUrlForPlatform(url: String): String = url
