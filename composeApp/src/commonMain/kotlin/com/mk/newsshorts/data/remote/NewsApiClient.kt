package com.mk.newsshorts.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import com.mk.newsshorts.domain.model.FeedLanguage
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsError
import com.mk.newsshorts.domain.model.NewsResult

/**
 * Client for the News Shorts backend. The backend aggregates RSS sources and
 * serves AI-summarized articles — no third-party API keys on the client.
 *
 * Responses are adapted to [NewsApiResponse] so the existing cache, mapper,
 * and ViewModel layers stay unchanged.
 */
class NewsApiClient(
    private val httpClient: HttpClient
) {
    suspend fun fetchTopHeadlines(
        category: NewsCategory?,
        country: String
    ): NewsResult<NewsApiResponse> = fetchFeed(
        language = FeedLanguage.DEFAULT,
        category = category?.apiValue,
    )

    suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<NewsApiResponse> = fetchCountry(country, FeedLanguage.DEFAULT)

    suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<NewsApiResponse> = fetchFeed(
        language = supportedLanguage(language),
        category = category.apiValue,
    )

    suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<NewsApiResponse> = fetchCountry(countryName, language)

    /**
     * A country's news in the language the reader picked. Languages the backend
     * doesn't publish fall back to English rather than to the country's own
     * language, so the feed never mixes scripts.
     */
    private suspend fun fetchCountry(country: String, language: String): NewsResult<NewsApiResponse> {
        val code = countryToCode(country) ?: return fetchFeed(supportedLanguage(language), category = null)
        return fetchUrl(ApiConfig.countryFeedUrl(code, supportedLanguage(language)))
    }

    private fun supportedLanguage(language: String): String = FeedLanguage.resolve(language)

    suspend fun fetchNewsByQuery(query: String): NewsResult<NewsApiResponse> =
        fetchFeed(language = null, category = null)

    private suspend fun fetchFeed(
        language: String?,
        category: String?,
    ): NewsResult<NewsApiResponse> = fetchUrl(ApiConfig.feedUrl(language, category))

    private suspend fun fetchUrl(url: String): NewsResult<NewsApiResponse> {
        return try {
            val response: BackendFeedResponse = httpClient.get(url).body()
            NewsResult.Success(response.toNewsApiResponse())
        } catch (exception: Exception) {
            val error: NewsError = when {
                exception.message?.contains("Unable to resolve host") == true -> NewsError.NetworkError
                exception.message?.contains("timeout") == true -> NewsError.NetworkError
                exception.message?.contains("connect", ignoreCase = true) == true -> NewsError.NetworkError
                else -> NewsError.UnknownError(exception.message ?: "Unknown error occurred")
            }
            NewsResult.Error(error)
        }
    }

    private fun BackendFeedResponse.toNewsApiResponse(): NewsApiResponse =
        NewsApiResponse(
            status = "ok",
            totalResults = total.toInt(),
            articles = articles.map { article ->
                ArticleDto(
                    source = SourceDto(
                        id = article.sourceName.lowercase().replace(" ", "-"),
                        name = article.sourceName,
                    ),
                    author = article.sourceName,
                    title = article.title,
                    description = article.summary,
                    url = article.url,
                    urlToImage = article.imageUrl,
                    publishedAt = epochMillisToIso8601(article.publishedAt),
                    content = article.summary,
                )
            },
        )

    /**
     * Countries with dedicated sources in the backend catalog. Anything else
     * returns null and falls back to the language-wide feed.
     */
    private fun countryToCode(country: String): String? =
        when (country.lowercase()) {
            "eg", "egypt" -> "eg"
            "sa", "saudi arabia" -> "sa"
            "ae", "uae" -> "ae"
            "us", "united states" -> "us"
            "gb", "uk", "united kingdom" -> "gb"
            "de", "germany" -> "de"
            "fr", "france" -> "fr"
            "in", "india" -> "in"
            "cn", "china" -> "cn"
            "jp", "japan" -> "jp"
            "au", "australia" -> "au"
            "ca", "canada" -> "ca"
            "br", "brazil" -> "br"
            else -> null
        }

    private fun epochMillisToIso8601(epochMillis: Long): String {
        var remainingDays: Long = epochMillis / 86_400_000L
        val secondsOfDay: Long = (epochMillis % 86_400_000L) / 1000L
        var year = 1970
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366 else 365
            if (remainingDays < daysInYear) break
            remainingDays -= daysInYear
            year++
        }
        val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var month = 1
        for (m in 0 until 12) {
            var dim = daysInMonth[m]
            if (m == 1 && isLeapYear(year)) dim++
            if (remainingDays < dim) break
            remainingDays -= dim
            month++
        }
        val day = remainingDays + 1
        val hour = secondsOfDay / 3600
        val minute = (secondsOfDay % 3600) / 60
        val second = secondsOfDay % 60
        return "$year-${pad(month.toLong())}-${pad(day)}T${pad(hour)}:${pad(minute)}:${pad(second)}Z"
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun pad(value: Long): String = if (value < 10) "0$value" else "$value"
}
