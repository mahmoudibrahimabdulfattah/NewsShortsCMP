package com.mk.newsshorts.domain.use_case

import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.repository.NewsRepository

class SearchNewsUseCase(
    private val newsRepository: NewsRepository
) {
    suspend fun execute(request: SearchNewsRequest): NewsResult<List<NewsArticle>> {
        if (request.query.isBlank()) {
            return NewsResult.Success(emptyList())
        }
        return newsRepository.fetchNewsByQuery(query = request.query)
    }
}

data class SearchNewsRequest(
    val query: String
)

