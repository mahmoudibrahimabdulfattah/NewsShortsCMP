package org.example.newsshorts.domain.use_case

import org.example.newsshorts.domain.model.NewsArticle
import org.example.newsshorts.domain.model.NewsResult
import org.example.newsshorts.domain.repository.NewsRepository

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

