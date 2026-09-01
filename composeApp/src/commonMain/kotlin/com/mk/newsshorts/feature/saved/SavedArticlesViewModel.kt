package com.mk.newsshorts.feature.saved

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.data.repository.SavedArticlesRepository
import com.mk.newsshorts.data.repository.ToggleResult
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedArticlesUiState(
    val articles: List<NewsArticle> = emptyList(),
) {
    val hasArticles: Boolean get() = articles.isNotEmpty()
}

sealed interface SavedArticlesUiEvent {
    data class Toggle(val article: NewsArticle) : SavedArticlesUiEvent
    data class Remove(val article: NewsArticle) : SavedArticlesUiEvent
}

sealed interface SavedArticlesMutation {
    data class Changed(
        val result: ToggleResult,
        val articles: List<NewsArticle>,
    ) : SavedArticlesMutation

    data object Unchanged : SavedArticlesMutation
}

class SavedArticlesViewModel(
    private val repository: SavedArticlesRepository,
    private val analytics: AnalyticsReporter,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(SavedArticlesUiState())
    val uiState: StateFlow<SavedArticlesUiState> = mutableState.asStateFlow()

    init {
        (scopeOverride ?: viewModelScope).launch {
            repository.saved.collect { articles ->
                mutableState.update { it.copy(articles = articles) }
            }
        }
    }

    suspend fun load() {
        repository.load()
    }

    fun processEvent(event: SavedArticlesUiEvent): SavedArticlesMutation = when (event) {
        is SavedArticlesUiEvent.Toggle -> toggle(event.article)
        is SavedArticlesUiEvent.Remove -> remove(event.article)
    }

    fun findByUrl(url: String): NewsArticle? =
        repository.saved.value.firstOrNull { it.articleUrl.value == url }

    private fun toggle(article: NewsArticle): SavedArticlesMutation.Changed {
        val result = repository.toggle(article)
        if (result == ToggleResult.SAVED) {
            analytics.logEvent(AnalyticsEvent.ArticleSaved(article.category.apiValue))
        }
        return SavedArticlesMutation.Changed(result, repository.saved.value)
    }

    private fun remove(article: NewsArticle): SavedArticlesMutation {
        if (!repository.remove(article)) return SavedArticlesMutation.Unchanged
        return SavedArticlesMutation.Changed(ToggleResult.REMOVED, repository.saved.value)
    }
}
