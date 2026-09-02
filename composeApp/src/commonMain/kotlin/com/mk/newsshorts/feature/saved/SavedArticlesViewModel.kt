package com.mk.newsshorts.feature.saved

import com.mk.newsshorts.core.model.analytics.AnalyticsEvent
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.core.model.ToggleResult
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import com.mk.newsshorts.core.domain.sync.SyncPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed interface SavedArticlesUiEffect {
    data class ShowToast(val message: String) : SavedArticlesUiEffect
}

class SavedArticlesViewModel(
    private val repository: SavedArticles,
    private val analytics: AnalyticsReporter,
    private val syncPublisher: SyncPublisher,
    private val strings: () -> AppStrings,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(SavedArticlesUiState())
    val uiState: StateFlow<SavedArticlesUiState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<SavedArticlesUiEffect>()
    val uiEffect: SharedFlow<SavedArticlesUiEffect> = mutableEffect.asSharedFlow()

    private val savedScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    init {
        savedScope.launch {
            repository.saved.collect { articles ->
                mutableState.update { it.copy(articles = articles) }
            }
        }
    }

    suspend fun load() {
        repository.load()
    }

    fun processEvent(event: SavedArticlesUiEvent) {
        when (event) {
            is SavedArticlesUiEvent.Toggle -> toggle(event.article)
            is SavedArticlesUiEvent.Remove -> remove(event.article)
        }
    }

    fun findByUrl(url: String): NewsArticle? =
        repository.saved.value.firstOrNull { it.articleUrl.value == url }

    private fun toggle(article: NewsArticle) {
        val result = repository.toggle(article)
        if (result == ToggleResult.SAVED) {
            analytics.logEvent(AnalyticsEvent.ArticleSaved(article.category.apiValue))
        }
        syncPublisher.publishSavedArticles(repository.saved.value)
        val message = when (result) {
            ToggleResult.SAVED -> strings().articleSaved
            ToggleResult.REMOVED -> strings().articleRemoved
        }
        savedScope.launch {
            mutableEffect.emit(SavedArticlesUiEffect.ShowToast(message))
        }
    }

    private fun remove(article: NewsArticle) {
        if (!repository.remove(article)) return
        syncPublisher.publishSavedArticles(repository.saved.value)
        savedScope.launch {
            mutableEffect.emit(SavedArticlesUiEffect.ShowToast(strings().articleRemoved))
        }
    }
}
