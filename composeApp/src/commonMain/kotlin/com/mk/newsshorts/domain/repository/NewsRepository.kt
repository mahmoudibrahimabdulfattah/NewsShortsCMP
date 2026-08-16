package com.mk.newsshorts.domain.repository

import com.mk.newsshorts.domain.model.FeedPage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsResult

interface NewsRepository {
    suspend fun fetchTopHeadlines(
        category: NewsCategory,
        country: String = "us"
    ): NewsResult<FeedPage>

    suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<FeedPage>

    suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<FeedPage>

    suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<FeedPage>

    /**
     * A later page, named by the page before it.
     *
     * Not cached: the cache exists so a cold start has something to show, and
     * only the first page is ever shown at a cold start. A reader who was five
     * pages deep yesterday starts at the top today.
     */
    suspend fun fetchFeedPage(pageFile: String): NewsResult<FeedPage>

    suspend fun fetchNewsByQuery(query: String): NewsResult<List<NewsArticle>>

    fun getCachedTopHeadlines(
        category: NewsCategory,
        country: String
    ): NewsResult<FeedPage>?

    fun getCachedNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<FeedPage>?

    fun getCachedNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<FeedPage>?

    fun hasCachedNews(cacheKey: String): Boolean
}
