package org.example.newsshorts.data.remote

expect fun isWebPlatform(): Boolean

object ApiConfig {
    private const val NEWS_API_BASE: String = "https://newsapi.org/v2"
    // Using thingproxy as CORS proxy - it's simple and reliable
    private const val CORS_PROXY: String = "https://thingproxy.freeboard.io/fetch/"
    
    fun getTopHeadlinesUrl(): String {
        val baseUrl: String = "${NEWS_API_BASE}/top-headlines"
        return if (isWebPlatform()) {
            "${CORS_PROXY}${baseUrl}"
        } else {
            baseUrl
        }
    }
    
    fun getEverythingUrl(): String {
        val baseUrl: String = "${NEWS_API_BASE}/everything"
        return if (isWebPlatform()) {
            "${CORS_PROXY}${baseUrl}"
        } else {
            baseUrl
        }
    }
}

