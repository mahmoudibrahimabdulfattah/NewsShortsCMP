package com.mk.newsshorts.core.domain.use_case

import com.mk.newsshorts.core.model.FeedPage
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsResult
import com.mk.newsshorts.core.domain.repository.NewsRepository

class GetTopHeadlinesUseCase(
    private val newsRepository: NewsRepository
) {
    suspend fun execute(
        request: GetTopHeadlinesRequest = GetTopHeadlinesRequest()
    ): NewsResult<FeedPage> {
        return when {
            request.useCountry && request.language != null && request.language != DEFAULT_LANGUAGE -> {
                newsRepository.fetchNewsByCountryAndLanguage(
                    countryName = request.countryName,
                    language = request.language
                )
            }
            request.useCountry -> {
                newsRepository.fetchTopHeadlinesByCountry(
                    country = request.country
                )
            }
            request.language != null && request.language != DEFAULT_LANGUAGE -> {
                newsRepository.fetchNewsByLanguage(
                    category = request.category,
                    language = request.language
                )
            }
            else -> {
                newsRepository.fetchTopHeadlines(
                    category = request.category,
                    country = request.country
                )
            }
        }
    }

    /**
     * The page after [pageFile]'s own, wherever the reader currently is. The
     * feed names its own next page, so this needs no request: which feed it
     * belongs to is already baked into the name.
     */
    suspend fun nextPage(pageFile: String): NewsResult<FeedPage> =
        newsRepository.fetchFeedPage(pageFile)

    fun getCached(
        request: GetTopHeadlinesRequest = GetTopHeadlinesRequest()
    ): NewsResult<FeedPage>? {
        return when {
            request.useCountry && request.language != null && request.language != DEFAULT_LANGUAGE -> {
                newsRepository.getCachedNewsByCountryAndLanguage(
                    countryName = request.countryName,
                    language = request.language
                )
            }
            request.useCountry -> {
                newsRepository.getCachedNewsByCountryAndLanguage(
                    countryName = request.countryName,
                    language = DEFAULT_LANGUAGE
                )
            }
            request.language != null && request.language != DEFAULT_LANGUAGE -> {
                newsRepository.getCachedNewsByLanguage(
                    category = request.category,
                    language = request.language
                )
            }
            else -> {
                newsRepository.getCachedTopHeadlines(
                    category = request.category,
                    country = request.country
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_LANGUAGE: String = "en"
    }
}

data class GetTopHeadlinesRequest(
    val category: NewsCategory = NewsCategory.GENERAL,
    val country: String = "us",
    val countryName: String = "United States",
    val language: String? = null,
    val useCountry: Boolean = false
)
