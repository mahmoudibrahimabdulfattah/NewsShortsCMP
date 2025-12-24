package org.example.newsshorts.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.newsshorts.data.remote.ArticleDto
import org.example.newsshorts.data.remote.NewsApiResponse
import org.example.newsshorts.data.remote.SourceDto

@Serializable
data class CachedNewsData(
    val cacheKey: String,
    val articles: List<CachedArticle>,
    val timestamp: Long
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

class NewsLocalDataSource {
    private val cache: MutableMap<String, CachedNewsData> = mutableMapOf()
    private val cacheFlow: MutableStateFlow<Map<String, CachedNewsData>> = MutableStateFlow(emptyMap())
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getCachedNews(cacheKey: String): NewsApiResponse? {
        val cachedData = cache[cacheKey] ?: return null
        val isCacheValid = isCacheValid(cachedData.timestamp)
        if (!isCacheValid) {
            cache.remove(cacheKey)
            return null
        }
        return convertToApiResponse(cachedData)
    }

    fun saveNewsToCache(cacheKey: String, response: NewsApiResponse) {
        val cachedArticles: List<CachedArticle> = response.articles.map { article ->
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
        val cachedData = CachedNewsData(
            cacheKey = cacheKey,
            articles = cachedArticles,
            timestamp = currentTimeMillis()
        )
        cache[cacheKey] = cachedData
        cacheFlow.value = cache.toMap()
    }

    fun hasAnyCachedNews(): Boolean {
        return cache.isNotEmpty()
    }

    fun getAllCachedNews(): List<CachedNewsData> {
        return cache.values.toList()
    }

    fun clearCache() {
        cache.clear()
        cacheFlow.value = emptyMap()
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
        val currentTime = currentTimeMillis()
        val cacheAge = currentTime - timestamp
        return cacheAge < CACHE_DURATION_MS
    }

    companion object {
        private const val CACHE_DURATION_MS: Long = 30 * 60 * 1000L // 30 minutes
        
        fun createCacheKey(type: String, identifier: String): String {
            return "${type}_$identifier"
        }
    }
}

expect fun currentTimeMillis(): Long

