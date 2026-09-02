package com.mk.newsshorts.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.mk.newsshorts.data.mapper.NewsMapper
import com.mk.newsshorts.data.remote.ArticleDto
import com.mk.newsshorts.data.remote.NewsApiResponse
import com.mk.newsshorts.data.remote.SourceDto
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.repository.ArticleLookup

@Serializable
data class CachedNewsData(
    val cacheKey: String,
    val articles: List<CachedArticle>,
    val timestamp: Long,
    val lastAccessTime: Long = 0L,
    /**
     * The next page's file name, cached with the page it came from. Without it
     * a reader who opened the app offline would reach the end of the cached
     * page with nowhere to go, even once the network came back. Defaulted so
     * entries written before pagination still deserialize.
     */
    val nextPage: String? = null
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
internal data class CacheIndex(
    val keys: List<CacheKeyInfo>
)

@Serializable
internal data class CacheKeyInfo(
    val key: String,
    val lastAccessTime: Long
)

class NewsLocalDataSource(
    private val settingsStorage: SettingsStorage
) : ArticleLookup {
    private val memoryCache: MutableMap<String, CachedNewsData> = mutableMapOf()
    private val cacheStateFlow: MutableStateFlow<Map<String, CachedNewsData>> = MutableStateFlow(emptyMap())
    val cacheState: StateFlow<Map<String, CachedNewsData>> = cacheStateFlow.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        purgeSupersededCaches(
            currentVersion = CACHE_SCHEMA_VERSION,
            getString = settingsStorage::getString,
            putString = settingsStorage::putString,
        )
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
            lastAccessTime = currentTime,
            nextPage = response.nextPage
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

    override suspend fun find(url: String): NewsArticle? {
        val target = url.trim()
        if (target.isBlank()) return null
        return memoryCache.values
            .asSequence()
            .filter { isCacheValid(it.timestamp) }
            .flatMap { cachedData ->
                NewsMapper.mapToDomain(
                    response = convertToApiResponse(cachedData),
                    category = categoryFor(cachedData.cacheKey),
                ).asSequence()
            }
            .firstOrNull { it.articleUrl.value == target }
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
            articles = articles,
            nextPage = cachedData.nextPage
        )
    }

    private fun isCacheValid(timestamp: Long): Boolean {
        val currentTime: Long = currentTimeMillis()
        val cacheAge: Long = currentTime - timestamp
        return cacheAge < CACHE_EXPIRY_MS
    }

    private fun categoryFor(cacheKey: String): NewsCategory =
        NewsCategory.entries.firstOrNull { cacheKey.endsWith("_${it.apiValue}") }
            ?: NewsCategory.GENERAL

    companion object {
        /**
         * A whole page, never part of one. The cached copy is served with the
         * page's own `nextPage`, so truncating it would leave the articles
         * between the cut and the page boundary unreachable: the reader would
         * scroll off the end of the cached twenty straight into the page that
         * starts at forty. Kept above the backend's page size so a page still
         * fits after it grows.
         */
        private const val MAX_ARTICLES_PER_CACHE: Int = 60
        private const val MAX_CACHE_ENTRIES: Int = 10
        private const val CACHE_EXPIRY_MS: Long = 24 * 60 * 60 * 1000L // 24 hours
        internal const val CACHE_PREFIX: String = "news_cache_"
        private const val CACHE_SCHEMA_VERSION: Int = 2
        private val CACHE_INDEX_KEY: String = cacheIndexKey(CACHE_SCHEMA_VERSION)

        /** Version 1 predates the scheme and is the bare, unsuffixed key. */
        internal fun cacheIndexKey(version: Int): String =
            if (version <= 1) "news_cache_index" else "news_cache_index_v$version"

        fun createCacheKey(type: String, identifier: String): String {
            return "v${CACHE_SCHEMA_VERSION}_${type}_$identifier"
        }
    }
}

expect fun currentTimeMillis(): Long

/**
 * Deletes the caches written under an earlier schema.
 *
 * A new cache version renames both the index and every entry key, so the old
 * entries stop being read the moment the app updates — but they are never
 * overwritten either, because eviction only counts the keys the current index
 * names. Left alone they sit in the reader's settings store for the life of the
 * install, holding articles filed under the categories that made this version
 * necessary.
 *
 * Takes accessors rather than the storage itself so it can be exercised without
 * a platform implementation.
 */
internal fun purgeSupersededCaches(
    currentVersion: Int,
    getString: (String, String) -> String,
    putString: (String, String) -> Unit,
) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    for (version in 1 until currentVersion) {
        val indexKey = NewsLocalDataSource.cacheIndexKey(version)
        val indexJson: String = getString(indexKey, "")
        if (indexJson.isEmpty()) continue
        try {
            val index: CacheIndex = json.decodeFromString(indexJson)
            index.keys.forEach { putString(NewsLocalDataSource.CACHE_PREFIX + it.key, "") }
        } catch (exception: Exception) {
            // An index that will not parse names no entries to delete. The
            // index key itself is still cleared, so the attempt is not repeated
            // on every launch for the life of the install.
        }
        putString(indexKey, "")
    }
}
