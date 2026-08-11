package com.mk.newsshorts.data.remote

actual fun isWebPlatform(): Boolean = true


actual fun adjustBaseUrlForPlatform(url: String): String = url
