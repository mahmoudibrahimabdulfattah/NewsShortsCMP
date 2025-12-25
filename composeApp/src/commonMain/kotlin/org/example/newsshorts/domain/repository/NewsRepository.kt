package org.example.newsshorts.domain.repository

import org.example.newsshorts.domain.model.NewsArticle
import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.domain.model.NewsResult

interface NewsRepository {
    suspend fun fetchTopHeadlines(
        category: NewsCategory,
        country: String = "us"
    ): NewsResult<List<NewsArticle>>

    suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<List<NewsArticle>>

    suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<List<NewsArticle>>

    suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<List<NewsArticle>>

    suspend fun fetchNewsByQuery(query: String): NewsResult<List<NewsArticle>>

    fun getCachedTopHeadlines(
        category: NewsCategory,
        country: String
    ): NewsResult<List<NewsArticle>>?

    fun getCachedNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<List<NewsArticle>>?

    fun getCachedNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<List<NewsArticle>>?

    fun hasCachedNews(cacheKey: String): Boolean
}
