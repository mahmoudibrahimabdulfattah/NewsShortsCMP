package org.example.newsshorts.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.domain.model.NewsError
import org.example.newsshorts.domain.model.NewsResult

class NewsApiClient(
    private val httpClient: HttpClient
) {
    suspend fun fetchTopHeadlines(
        category: NewsCategory?,
        country: String
    ): NewsResult<NewsApiResponse> {
        return executeRequest {
            httpClient.get(ApiConfig.getTopHeadlinesUrl()) {
                parameter(PARAM_API_KEY, API_KEY)
                parameter(PARAM_COUNTRY, country)
                if (category != null) {
                    parameter(PARAM_CATEGORY, category.apiValue)
                }
                parameter(PARAM_PAGE_SIZE, DEFAULT_PAGE_SIZE)
            }.body()
        }
    }

    suspend fun fetchTopHeadlinesByCountry(
        country: String
    ): NewsResult<NewsApiResponse> {
        return executeRequest {
            httpClient.get(ApiConfig.getTopHeadlinesUrl()) {
                parameter(PARAM_API_KEY, API_KEY)
                parameter(PARAM_COUNTRY, country)
                parameter(PARAM_PAGE_SIZE, DEFAULT_PAGE_SIZE)
            }.body()
        }
    }

    suspend fun fetchNewsByLanguage(
        category: NewsCategory,
        language: String
    ): NewsResult<NewsApiResponse> {
        val query: String = getCategoryQuery(category)
        return executeRequest {
            httpClient.get(ApiConfig.getEverythingUrl()) {
                parameter(PARAM_API_KEY, API_KEY)
                parameter(PARAM_QUERY, query)
                parameter(PARAM_LANGUAGE, language)
                parameter(PARAM_PAGE_SIZE, DEFAULT_PAGE_SIZE)
                parameter(PARAM_SORT_BY, SORT_BY_PUBLISHED_AT)
            }.body()
        }
    }

    suspend fun fetchNewsByQuery(query: String): NewsResult<NewsApiResponse> {
        return executeRequest {
            httpClient.get(ApiConfig.getEverythingUrl()) {
                parameter(PARAM_API_KEY, API_KEY)
                parameter(PARAM_QUERY, query)
                parameter(PARAM_PAGE_SIZE, DEFAULT_PAGE_SIZE)
                parameter(PARAM_SORT_BY, SORT_BY_PUBLISHED_AT)
            }.body()
        }
    }

    suspend fun fetchNewsByCountryAndLanguage(
        countryName: String,
        language: String
    ): NewsResult<NewsApiResponse> {
        val query: String = getCountryQuery(countryName)
        return executeRequest {
            httpClient.get(ApiConfig.getEverythingUrl()) {
                parameter(PARAM_API_KEY, API_KEY)
                parameter(PARAM_QUERY, query)
                parameter(PARAM_LANGUAGE, language)
                parameter(PARAM_PAGE_SIZE, DEFAULT_PAGE_SIZE)
                parameter(PARAM_SORT_BY, SORT_BY_PUBLISHED_AT)
            }.body()
        }
    }

    private fun getCountryQuery(countryName: String): String {
        return when (countryName.lowercase()) {
            "united states" -> "USA OR \"United States\" OR America OR Washington"
            "united kingdom" -> "UK OR \"United Kingdom\" OR Britain OR London"
            "egypt" -> "Egypt OR Cairo OR مصر"
            "saudi arabia" -> "\"Saudi Arabia\" OR Riyadh OR السعودية"
            "uae" -> "UAE OR \"United Arab Emirates\" OR Dubai OR الإمارات"
            "germany" -> "Germany OR Berlin OR Deutschland"
            "france" -> "France OR Paris"
            "india" -> "India OR Delhi OR Mumbai"
            "china" -> "China OR Beijing OR 中国"
            "japan" -> "Japan OR Tokyo OR 日本"
            "australia" -> "Australia OR Sydney OR Melbourne"
            "canada" -> "Canada OR Toronto OR Ottawa"
            "brazil" -> "Brazil OR Brasilia OR Brasil"
            else -> countryName
        }
    }

    private fun getCategoryQuery(category: NewsCategory): String {
        return when (category) {
            NewsCategory.GENERAL -> "news OR world OR breaking"
            NewsCategory.TECHNOLOGY -> "technology OR tech OR software OR AI"
            NewsCategory.BUSINESS -> "business OR economy OR finance OR market"
            NewsCategory.SPORTS -> "sports OR football OR basketball OR soccer"
            NewsCategory.ENTERTAINMENT -> "entertainment OR movies OR music OR celebrity"
            NewsCategory.HEALTH -> "health OR medical OR wellness OR fitness"
            NewsCategory.SCIENCE -> "science OR research OR discovery OR space"
        }
    }

    private suspend fun executeRequest(
        request: suspend () -> NewsApiResponse
    ): NewsResult<NewsApiResponse> {
        return try {
            val response: NewsApiResponse = request()
            if (response.status == STATUS_OK) {
                NewsResult.Success(response)
            } else {
                NewsResult.Error(NewsError.ServerError)
            }
        } catch (exception: Exception) {
            val error: NewsError = when {
                exception.message?.contains("Unable to resolve host") == true -> NewsError.NetworkError
                exception.message?.contains("timeout") == true -> NewsError.NetworkError
                exception.message?.contains("connect") == true -> NewsError.NetworkError
                else -> NewsError.UnknownError(exception.message ?: "Unknown error occurred")
            }
            NewsResult.Error(error)
        }
    }

    companion object {
        // Note: In production, this should be stored securely using BuildConfig or environment variables
        private const val API_KEY: String = "22e6a1a962eb42519f47eb018cc95bc9"
        private const val PARAM_API_KEY: String = "apiKey"
        private const val PARAM_COUNTRY: String = "country"
        private const val PARAM_CATEGORY: String = "category"
        private const val PARAM_LANGUAGE: String = "language"
        private const val PARAM_QUERY: String = "q"
        private const val PARAM_PAGE_SIZE: String = "pageSize"
        private const val PARAM_SORT_BY: String = "sortBy"
        private const val DEFAULT_PAGE_SIZE: Int = 20
        private const val SORT_BY_PUBLISHED_AT: String = "publishedAt"
        private const val STATUS_OK: String = "ok"
    }
}

