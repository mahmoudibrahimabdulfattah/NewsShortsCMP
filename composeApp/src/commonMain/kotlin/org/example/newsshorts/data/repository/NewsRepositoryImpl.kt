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
        val cacheKey = NewsLocalDataSource.createCacheKey("headlines", "${country}_${category.apiValue}")
        return when (val result = newsApiClient.fetchTopHeadlines(category, country)) {
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

    override suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey = NewsLocalDataSource.createCacheKey("country", country)
        return when (val result = newsApiClient.fetchTopHeadlinesByCountry(country)) {
            is NewsResult.Success -> {
                val articles: List<NewsArticle> = NewsMapper.mapToDomain(result.data, NewsCategory.GENERAL)
                if (articles.isNotEmpty()) {
                    localDataSource.saveNewsToCache(cacheKey, result.data)
                    NewsResult.Success(articles)
                } else {
                    tryLoadFromCache(cacheKey, NewsCategory.GENERAL)
                }
            }
            is NewsResult.Error -> {
                tryLoadFromCache(cacheKey, NewsCategory.GENERAL)
            }
        }
    }

    override suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey = NewsLocalDataSource.createCacheKey("language", "${language}_${category.apiValue}")
        return when (val result = newsApiClient.fetchNewsByLanguage(category, language)) {
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

    override suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<List<NewsArticle>> {
        val cacheKey = NewsLocalDataSource.createCacheKey("country_lang", "${countryName}_$language")
        return when (val result = newsApiClient.fetchNewsByCountryAndLanguage(countryName, language)) {
            is NewsResult.Success -> {
                val articles: List<NewsArticle> = NewsMapper.mapToDomain(result.data, NewsCategory.GENERAL)
                if (articles.isNotEmpty()) {
                    localDataSource.saveNewsToCache(cacheKey, result.data)
                    NewsResult.Success(articles)
                } else {
                    tryLoadFromCache(cacheKey, NewsCategory.GENERAL)
                }
            }
            is NewsResult.Error -> {
                tryLoadFromCache(cacheKey, NewsCategory.GENERAL)
            }
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
            is NewsResult.Error -> {
                result
            }
        }
    }

    fun hasAnyCachedNews(): Boolean {
        return localDataSource.hasAnyCachedNews()
    }

    private fun tryLoadFromCache(
        cacheKey: String,
        category: NewsCategory
    ): NewsResult<List<NewsArticle>> {
        val cachedResponse = localDataSource.getCachedNews(cacheKey)
        return if (cachedResponse != null) {
            val articles = NewsMapper.mapToDomain(cachedResponse, category)
            if (articles.isNotEmpty()) {
                NewsResult.Success(articles)
            } else {
                NewsResult.Error(NewsError.NoDataError)
            }
        } else {
            NewsResult.Error(NewsError.NetworkError)
        }
    }
}
