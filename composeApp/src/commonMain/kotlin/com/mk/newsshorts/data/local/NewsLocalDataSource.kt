package com.mk.newsshorts.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.mk.newsshorts.data.remote.ArticleDto
import com.mk.newsshorts.data.remote.NewsApiResponse
import com.mk.newsshorts.data.remote.SourceDto

@Serializable
data class CachedNewsData(
    val cacheKey: String,
    val articles: List<CachedArticle>,
    val timestamp: Long,
    val lastAccessTime: Long = 0L
)

@Serializable
data class CachedArticle(
    val sourceId: String?,
    val sourceName: String,
    val author: String?,
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?
)

@Serializable
private data class CacheIndex(
    val keys: List<CacheKeyInfo>
)

@Serializable
private data class CacheKeyInfo(
    val key: String,
    val lastAccessTime: Long
)

class NewsLocalDataSource(
    private val settingsStorage: SettingsStorage
) {
    private val memoryCache: MutableMap<String, CachedNewsData> = mutableMapOf()
    private val cacheStateFlow: MutableStateFlow<Map<String, CachedNewsData>> = MutableStateFlow(emptyMap())
    val cacheState: StateFlow<Map<String, CachedNewsData>> = cacheStateFlow.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        loadCacheIndex()
    }

    private fun loadCacheIndex() {
        val indexJson: String = settingsStorage.getString(CACHE_INDEX_KEY, "")
        if (indexJson.isNotEmpty()) {
            try {
                val index: CacheIndex = json.decodeFromString(indexJson)
                index.keys.forEach { keyInfo ->
                    loadCacheEntry(keyInfo.key)
                }
            } catch (exception: Exception) {
                clearAllCache()
            }
        }
    }

    private fun loadCacheEntry(cacheKey: String) {
        val cacheJson: String = settingsStorage.getString(CACHE_PREFIX + cacheKey, "")
        if (cacheJson.isNotEmpty()) {
            try {
                val cachedData: CachedNewsData = json.decodeFromString(cacheJson)
                if (isCacheValid(cachedData.timestamp)) {
                    memoryCache[cacheKey] = cachedData
                } else {
                    removeCacheEntry(cacheKey)
                }
            } catch (exception: Exception) {
                removeCacheEntry(cacheKey)
            }
        }
    }

    fun getCachedNews(cacheKey: String): NewsApiResponse? {
        val cachedData: CachedNewsData = memoryCache[cacheKey] ?: return null
        if (!isCacheValid(cachedData.timestamp)) {
            removeCacheEntry(cacheKey)
            return null
        }
        updateLastAccessTime(cacheKey)
        return convertToApiResponse(cachedData)
    }

    fun getCachedNewsSync(cacheKey: String): NewsApiResponse? {
        return getCachedNews(cacheKey)
    }

    fun saveNewsToCache(cacheKey: String, response: NewsApiResponse) {
        val truncatedArticles: List<ArticleDto> = response.articles.take(MAX_ARTICLES_PER_CACHE)
        val cachedArticles: List<CachedArticle> = truncatedArticles.map { article ->
            CachedArticle(
                sourceId = article.source.id,
                sourceName = article.source.name,
                author = article.author,
                title = article.title,
                description = article.description,
                url = article.url,
                urlToImage = article.urlToImage,
                publishedAt = article.publishedAt,
                content = article.content
            )
        }
        val currentTime: Long = currentTimeMillis()
        val cachedData = CachedNewsData(
            cacheKey = cacheKey,
            articles = cachedArticles,
            timestamp = currentTime,
            lastAccessTime = currentTime
        )
        evictIfNeeded()
        memoryCache[cacheKey] = cachedData
        cacheStateFlow.value = memoryCache.toMap()
        persistCacheEntry(cacheKey, cachedData)
        updateCacheIndex()
    }

    fun hasAnyCachedNews(): Boolean {
        return memoryCache.isNotEmpty()
    }

    fun hasCachedNewsForKey(cacheKey: String): Boolean {
        val cachedData: CachedNewsData? = memoryCache[cacheKey]
        return cachedData != null && isCacheValid(cachedData.timestamp)
    }

    fun getAllCachedNews(): List<CachedNewsData> {
        return memoryCache.values.filter { isCacheValid(it.timestamp) }
    }

    fun clearCache() {
        memoryCache.keys.toList().forEach { key ->
            removeCacheEntry(key)
        }
        memoryCache.clear()
        cacheStateFlow.value = emptyMap()
        settingsStorage.putString(CACHE_INDEX_KEY, "")
    }

    private fun clearAllCache() {
        memoryCache.clear()
        cacheStateFlow.value = emptyMap()
        settingsStorage.putString(CACHE_INDEX_KEY, "")
    }

    private fun evictIfNeeded() {
        if (memoryCache.size >= MAX_CACHE_ENTRIES) {
            val oldestEntry: Map.Entry<String, CachedNewsData>? = memoryCache.entries
                .minByOrNull { it.value.lastAccessTime }
            oldestEntry?.let { entry ->
                removeCacheEntry(entry.key)
            }
        }
    }

    private fun updateLastAccessTime(cacheKey: String) {
        val cachedData: CachedNewsData = memoryCache[cacheKey] ?: return
        val updatedData: CachedNewsData = cachedData.copy(lastAccessTime = currentTimeMillis())
        memoryCache[cacheKey] = updatedData
        persistCacheEntry(cacheKey, updatedData)
        updateCacheIndex()
    }

    private fun persistCacheEntry(cacheKey: String, data: CachedNewsData) {
        try {
            val cacheJson: String = json.encodeToString(data)
            settingsStorage.putString(CACHE_PREFIX + cacheKey, cacheJson)
        } catch (exception: Exception) {
            // Silently fail persistence
        }
    }

    private fun removeCacheEntry(cacheKey: String) {
        memoryCache.remove(cacheKey)
        settingsStorage.putString(CACHE_PREFIX + cacheKey, "")
        updateCacheIndex()
    }

    private fun updateCacheIndex() {
        val keyInfoList: List<CacheKeyInfo> = memoryCache.map { entry ->
            CacheKeyInfo(
                key = entry.key,
                lastAccessTime = entry.value.lastAccessTime
            )
        }
        val index = CacheIndex(keys = keyInfoList)
        try {
            val indexJson: String = json.encodeToString(index)
            settingsStorage.putString(CACHE_INDEX_KEY, indexJson)
        } catch (exception: Exception) {
            // Silently fail
        }
    }

    private fun convertToApiResponse(cachedData: CachedNewsData): NewsApiResponse {
        val articles: List<ArticleDto> = cachedData.articles.map { cached ->
            ArticleDto(
                source = SourceDto(id = cached.sourceId, name = cached.sourceName),
                author = cached.author,
                title = cached.title,
                description = cached.description,
                url = cached.url,
                urlToImage = cached.urlToImage,
                publishedAt = cached.publishedAt,
                content = cached.content
            )
        }
        return NewsApiResponse(
            status = "ok",
            totalResults = articles.size,
            articles = articles
        )
    }

    private fun isCacheValid(timestamp: Long): Boolean {
        val currentTime: Long = currentTimeMillis()
        val cacheAge: Long = currentTime - timestamp
        return cacheAge < CACHE_EXPIRY_MS
    }

    companion object {
        private const val MAX_ARTICLES_PER_CACHE: Int = 20
        private const val MAX_CACHE_ENTRIES: Int = 10
        private const val CACHE_EXPIRY_MS: Long = 24 * 60 * 60 * 1000L // 24 hours
        private const val CACHE_PREFIX: String = "news_cache_"
        private const val CACHE_INDEX_KEY: String = "news_cache_index"

        fun createCacheKey(type: String, identifier: String): String {
            return "${type}_$identifier"
        }
    }
}

expect fun currentTimeMillis(): Long
