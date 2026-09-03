package com.mk.newsshorts.core.data.remote

actual fun isWebPlatform(): Boolean = false


// Android emulator maps the host machine's localhost to 10.0.2.2.
actual fun adjustBaseUrlForPlatform(url: String): String =
    url.replace("://localhost", "://10.0.2.2").replace("://127.0.0.1", "://10.0.2.2")
