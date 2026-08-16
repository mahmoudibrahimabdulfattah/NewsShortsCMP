package com.mk.newsshorts.data.repository

import com.mk.newsshorts.data.local.NewsLocalDataSource
import com.mk.newsshorts.data.local.currentTimeMillis
import com.mk.newsshorts.data.mapper.NewsMapper
import com.mk.newsshorts.data.remote.NewsApiClient
import com.mk.newsshorts.domain.model.FeedPage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsError
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.repository.NewsRepository
import com.mk.newsshorts.domain.search.SearchIndex

class NewsRepositoryImpl(
    private val newsApiClient: NewsApiClient,
    private val localDataSource: NewsLocalDataSource
) : NewsRepository {

    override suspend fun fetchTopHeadlines(
        category: NewsCategory,
        country: String
    ): NewsResult<FeedPage> {
        val cacheKey: String = createHeadlinesCacheKey(category, country)
        return fetchAndCache(cacheKey, category) {
            newsApiClient.fetchTopHeadlines(category, country)
        }
    }

    override suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<FeedPage> {
        val cacheKey: String = NewsLocalDataSource.createCacheKey("country", country)
        return fetchAndCache(cacheKey, NewsCategory.GENERAL) {
            newsApiClient.fetchTopHeadlinesByCountry(country)
        }
    }

    override suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<FeedPage> {
        val cacheKey: String = createLanguageCacheKey(category, language)
        return fetchAndCache(cacheKey, category) {
            newsApiClient.fetchNewsByLanguage(category, language)
        }
    }

    override suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<FeedPage> {
        val cacheKey: String = createCountryLanguageCacheKey(countryName, language)
        return fetchAndCache(cacheKey, NewsCategory.GENERAL) {
            newsApiClient.fetchNewsByCountryAndLanguage(countryName, language)
        }
    }

    /**
     * Straight to the network and never to the cache — see
     * [NewsRepository.fetchFeedPage]. An empty page is an error rather than an
     * empty success so the caller can offer a retry: the alternative reads to a
     * scrolling reader as the feed having quietly ended.
     *
     * A page that is *not published* is the opposite case and really is the end
     * of the feed — retention emptied it and the publish that followed stopped
     * writing it — so it comes back as a page with nothing on it and nothing
     * after it, which is exactly what the end of a feed looks like.
     */
    override suspend fun fetchFeedPage(pageFile: String): NewsResult<FeedPage> {
        return when (val result = newsApiClient.fetchFeedPage(pageFile)) {
            is NewsResult.Success -> {
                val articles = NewsMapper.mapToDomain(result.data, NewsCategory.GENERAL)
                if (articles.isEmpty()) {
                    NewsResult.Error(NewsError.NoDataError)
                } else {
                    NewsResult.Success(FeedPage(articles, result.data.nextPage))
                }
            }
            is NewsResult.Error -> endOfFeedFor(result.error)?.let { NewsResult.Success(it) } ?: result
        }
    }

    /**
     * The search corpus for one language, downloaded once and folded once.
     *
     * Held in memory rather than in [localDataSource]: the corpus is an order
     * of magnitude larger than a feed page, and the settings-backed cache is
     * sized for pages (and on the JVM target is backed by `java.util.prefs`,
     * which throws past a few kilobytes per value). The cost of that choice is
     * that search needs the network once per session and does not work offline
     * at all, which is the honest trade for not writing a megabyte of article
     * text to disk for a feature most sessions never open.
     *
     * A failed refresh serves the corpus already held rather than an error:
     * results a publish or two old are worth far more to someone typing a name
     * than an empty screen is.
     */
    override suspend fun searchIndex(language: String): NewsResult<SearchIndex> {
        val cached = cachedSearchIndex?.takeIf { it.language == language }
        if (cached != null && currentTimeMillis() - cached.builtAtMillis < SEARCH_INDEX_TTL_MILLIS) {
            return NewsResult.Success(cached.index)
        }
        return when (val result = newsApiClient.fetchSearchIndex(language)) {
            is NewsResult.Success -> {
                val articles: List<NewsArticle> = NewsMapper.mapToDomain(
                    result.data,
                    // Only the fallback: the corpus spans every category, and
                    // each article carries its own — see NewsMapper.
                    NewsCategory.GENERAL,
                )
                if (articles.isEmpty()) {
                    cached?.let { NewsResult.Success(it.index) } ?: NewsResult.Error(NewsError.NoDataError)
                } else {
                    val index = SearchIndex.from(articles)
                    cachedSearchIndex = CachedSearchIndex(language, index, currentTimeMillis())
                    NewsResult.Success(index)
                }
            }
            is NewsResult.Error -> cached?.let { NewsResult.Success(it.index) } ?: result
        }
    }

    private var cachedSearchIndex: CachedSearchIndex? = null

    private data class CachedSearchIndex(
        val language: String,
        val index: SearchIndex,
        val builtAtMillis: Long,
    )

    override fun getCachedTopHeadlines(
        category: NewsCategory,
        country: String
    ): NewsResult<FeedPage>? {
        val cacheKey: String = createHeadlinesCacheKey(category, country)
        return loadFromCache(cacheKey, category)
    }

    override fun getCachedNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<FeedPage>? {
        val cacheKey: String = createLanguageCacheKey(category, language)
        return loadFromCache(cacheKey, category)
    }

    override fun getCachedNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<FeedPage>? {
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
        networkCall: suspend () -> NewsResult<com.mk.newsshorts.data.remote.NewsApiResponse>
    ): NewsResult<FeedPage> {
        return when (val result = networkCall()) {
            is NewsResult.Success -> {
                val articles: List<NewsArticle> = NewsMapper.mapToDomain(result.data, category)
                if (articles.isNotEmpty()) {
                    localDataSource.saveNewsToCache(cacheKey, result.data)
                    NewsResult.Success(FeedPage(articles, result.data.nextPage))
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
    ): NewsResult<FeedPage>? {
        val cachedResponse = localDataSource.getCachedNewsSync(cacheKey) ?: return null
        val articles: List<NewsArticle> = NewsMapper.mapToDomain(cachedResponse, category)
        return if (articles.isNotEmpty()) {
            NewsResult.Success(FeedPage(articles, cachedResponse.nextPage))
        } else {
            null
        }
    }

    private fun tryLoadFromCache(
        cacheKey: String,
        category: NewsCategory
    ): NewsResult<FeedPage> {
        val cachedResponse = localDataSource.getCachedNews(cacheKey)
        return if (cachedResponse != null) {
            val articles: List<NewsArticle> = NewsMapper.mapToDomain(cachedResponse, category)
            if (articles.isNotEmpty()) {
                NewsResult.Success(FeedPage(articles, cachedResponse.nextPage))
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

    private companion object {
        /**
         * How long a downloaded corpus is reused before it is fetched again.
         * Matched to how often the feed is published — refetching more often
         * than the backend publishes would spend a reader's data on a file
         * they already have.
         */
        const val SEARCH_INDEX_TTL_MILLIS: Long = 30 * 60 * 1000L
    }
}

/**
 * The page to serve instead of a failure, or null to keep the failure.
 *
 * Only a page that is *not published* ends a feed. Everything else — no
 * network, a timeout, a server having a bad day — has to stay an error the
 * reader can retry, because silently ending the feed on a flaky connection
 * would look exactly like running out of news.
 */
internal fun endOfFeedFor(error: NewsError): FeedPage? =
    if (error is NewsError.NotFound) FeedPage(articles = emptyList(), nextPage = null) else null
