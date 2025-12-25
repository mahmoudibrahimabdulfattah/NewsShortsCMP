package org.example.newsshorts.data.repository

import org.example.newsshorts.data.local.NewsLocalDataSource
import org.example.newsshorts.data.mapper.NewsMapper
import org.example.newsshorts.data.remote.NewsApiClient
import org.example.newsshorts.domain.model.NewsArticle
import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.domain.model.NewsError
import org.example.newsshorts.domain.model.NewsResult
import org.example.newsshorts.domain.repository.NewsRepository

class NewsRepositoryImpl(
    private val newsApiClient: NewsApiClient,
    private val localDataSource: NewsLocalDataSource
) : NewsRepository {

    override suspend fun fetchTopHeadlines(
        category: NewsCategory,
        country: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey: String = createHeadlinesCacheKey(category, country)
        return fetchAndCache(cacheKey, category) {
            newsApiClient.fetchTopHeadlines(category, country)
        }
    }

    override suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey: String = NewsLocalDataSource.createCacheKey("country", country)
        return fetchAndCache(cacheKey, NewsCategory.GENERAL) {
            newsApiClient.fetchTopHeadlinesByCountry(country)
        }
    }

    override suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey: String = createLanguageCacheKey(category, language)
        return fetchAndCache(cacheKey, category) {
            newsApiClient.fetchNewsByLanguage(category, language)
        }
    }

    override suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey: String = createCountryLanguageCacheKey(countryName, language)
        return fetchAndCache(cacheKey, NewsCategory.GENERAL) {
            newsApiClient.fetchNewsByCountryAndLanguage(countryName, language)
        }
    }

    override suspend fun fetchNewsByQuery(query: String): NewsResult<List<NewsArticle>> {
        return when (val result = newsApiClient.fetchNewsByQuery(query)) {
            is NewsResult.Success -> {
                val articles: List<NewsArticle> = NewsMapper.mapToDomain(
                    result.data,
                    NewsCategory.GENERAL
                )
                if (articles.isEmpty()) {
                    NewsResult.Error(NewsError.NoDataError)
                } else {
                    NewsResult.Success(articles)
                }
            }
            is NewsResult.Error -> result
        }
    }

    override fun getCachedTopHeadlines(
        category: NewsCategory,
        country: String
    ): NewsResult<List<NewsArticle>>? {
        val cacheKey: String = createHeadlinesCacheKey(category, country)
        return loadFromCache(cacheKey, category)
    }

    override fun getCachedNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<List<NewsArticle>>? {
        val cacheKey: String = createLanguageCacheKey(category, language)
        return loadFromCache(cacheKey, category)
    }

    override fun getCachedNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<List<NewsArticle>>? {
        val cacheKey: String = createCountryLanguageCacheKey(countryName, language)
        return loadFromCache(cacheKey, NewsCategory.GENERAL)
    }

    override fun hasCachedNews(cacheKey: String): Boolean {
        return localDataSource.hasCachedNewsForKey(cacheKey)
    }

    fun hasAnyCachedNews(): Boolean {
        return localDataSource.hasAnyCachedNews()
    }

    private suspend fun fetchAndCache(
        cacheKey: String,
        category: NewsCategory,
        networkCall: suspend () -> NewsResult<org.example.newsshorts.data.remote.NewsApiResponse>
    ): NewsResult<List<NewsArticle>> {
        return when (val result = networkCall()) {
            is NewsResult.Success -> {
                val articles: List<NewsArticle> = NewsMapper.mapToDomain(result.data, category)
                if (articles.isNotEmpty()) {
                    localDataSource.saveNewsToCache(cacheKey, result.data)
                    NewsResult.Success(articles)
                } else {
                    tryLoadFromCache(cacheKey, category)
                }
            }
            is NewsResult.Error -> {
                tryLoadFromCache(cacheKey, category)
            }
        }
    }

    private fun loadFromCache(
        cacheKey: String,
        category: NewsCategory
    ): NewsResult<List<NewsArticle>>? {
        val cachedResponse = localDataSource.getCachedNewsSync(cacheKey) ?: return null
        val articles: List<NewsArticle> = NewsMapper.mapToDomain(cachedResponse, category)
        return if (articles.isNotEmpty()) {
            NewsResult.Success(articles)
        } else {
            null
        }
    }

    private fun tryLoadFromCache(
        cacheKey: String,
        category: NewsCategory
    ): NewsResult<List<NewsArticle>> {
        val cachedResponse = localDataSource.getCachedNews(cacheKey)
        return if (cachedResponse != null) {
            val articles: List<NewsArticle> = NewsMapper.mapToDomain(cachedResponse, category)
            if (articles.isNotEmpty()) {
                NewsResult.Success(articles)
            } else {
                NewsResult.Error(NewsError.NoDataError)
            }
        } else {
            NewsResult.Error(NewsError.NetworkError)
        }
    }

    private fun createHeadlinesCacheKey(category: NewsCategory, country: String): String {
        return NewsLocalDataSource.createCacheKey("headlines", "${country}_${category.apiValue}")
    }

    private fun createLanguageCacheKey(category: NewsCategory, language: String): String {
        return NewsLocalDataSource.createCacheKey("language", "${language}_${category.apiValue}")
    }

    private fun createCountryLanguageCacheKey(countryName: String, language: String): String {
        return NewsLocalDataSource.createCacheKey("country_lang", "${countryName}_$language")
    }
}
