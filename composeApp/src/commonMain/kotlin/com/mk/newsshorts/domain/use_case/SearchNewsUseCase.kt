package com.mk.newsshorts.domain.use_case

import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.repository.NewsRepository
import com.mk.newsshorts.domain.search.isSearchable

/**
 * Answers a query against the corpus the backend publishes for one language.
 *
 * The matching is here rather than behind the repository because there is no
 * search endpoint to put it behind: the backend is static JSON on a CDN, so the
 * repository's job ends at handing over a
 * [com.mk.newsshorts.domain.search.SearchIndex] and this decides what is in it.
 *
 * That the query never crosses this boundary is the point, not a side effect —
 * the privacy policy promises analytics that carry a story's category, source
 * and language and nothing a reader typed, so the query text exists only in
 * this call and in the field it came from.
 */
class SearchNewsUseCase(
    private val newsRepository: NewsRepository
) {
    suspend fun execute(request: SearchNewsRequest): NewsResult<List<NewsArticle>> {
        // Too short to mean anything is not a failure and not a network call:
        // it is a reader half way through their first word.
        if (!isSearchable(request.query)) {
            return NewsResult.Success(emptyList())
        }
        return when (val index = newsRepository.searchIndex(request.language)) {
            is NewsResult.Success -> NewsResult.Success(index.data.search(request.query))
            is NewsResult.Error -> index
        }
    }
}

/**
 * [language] is the reader's news language, already narrowed to one the backend
 * publishes — a corpus exists per published language and for nothing else.
 */
data class SearchNewsRequest(
    val query: String,
    val language: String
)
