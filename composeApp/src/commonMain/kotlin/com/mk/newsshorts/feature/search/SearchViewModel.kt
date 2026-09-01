package com.mk.newsshorts.feature.search

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.domain.model.FeedLanguage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    /** Exactly what the reader typed; folding is only for comparison. */
    val query: String = "",
    val results: List<NewsArticle> = emptyList(),
    val isSearching: Boolean = false,
    /** Distinguishes the untouched field from a completed empty result. */
    val isSettled: Boolean = false,
    /** The corpus could not be reached and no older result can answer instead. */
    val hasFailed: Boolean = false,
    /** Most recent first, and held only on this device. */
    val recentSearches: List<String> = emptyList(),
) {
    val hasNoResults: Boolean
        get() = isSettled && !hasFailed && results.isEmpty()
}

sealed interface SearchUiEvent {
    /** Starts a fresh search visit using the corpus for [language]. */
    data class Opened(val language: String) : SearchUiEvent
    /** Ends the visit, including work still waiting on the corpus. */
    data object Closed : SearchUiEvent
    /** A keystroke; useful queries run after typing pauses. */
    data class QueryChanged(val query: String) : SearchUiEvent
    /** Runs immediately and records the deliberate query. */
    data class Submitted(val query: String) : SearchUiEvent
    data object QueryCleared : SearchUiEvent
    data class RecentSearchRemoved(val query: String) : SearchUiEvent
    data object RecentSearchesCleared : SearchUiEvent
    /** A result was useful enough to open, so its query is worth remembering. */
    data class ResultOpened(val article: NewsArticle) : SearchUiEvent
}

class SearchViewModel(
    private val searchNews: SearchNews,
    private val recentSearches: RecentSearches,
    private val analytics: AnalyticsReporter,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val searchScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private var language: String = FeedLanguage.DEFAULT

    /** In flight or waiting out the debounce; the next intent replaces it. */
    private var searchJob: Job? = null

    fun processEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.Opened -> open(event.language)
            SearchUiEvent.Closed -> close()
            is SearchUiEvent.QueryChanged -> queryChanged(event.query)
            is SearchUiEvent.Submitted -> submit(event.query)
            SearchUiEvent.QueryCleared -> queryChanged("")
            is SearchUiEvent.RecentSearchRemoved -> removeRecent(event.query)
            SearchUiEvent.RecentSearchesCleared -> clearRecent()
            is SearchUiEvent.ResultOpened -> rememberCurrentQuery()
        }
    }

    private fun open(selectedLanguage: String) {
        language = FeedLanguage.resolve(selectedLanguage)
        mutableState.update { it.copy(recentSearches = recentSearches.load()) }
    }

    private fun close() {
        searchJob?.cancel()
        searchJob = null
        mutableState.update {
            it.copy(
                query = "",
                results = emptyList(),
                isSearching = false,
                isSettled = false,
                hasFailed = false,
            )
        }
    }

    private fun queryChanged(query: String) {
        searchJob?.cancel()
        mutableState.update { it.copy(query = query) }
        if (!isSearchable(query)) {
            // A shortened query returns to the blank state instead of showing
            // answers to a question the reader has already deleted.
            mutableState.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    isSettled = false,
                    hasFailed = false,
                )
            }
            return
        }
        searchJob = searchScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            search(query)
        }
    }

    private fun submit(query: String) {
        searchJob?.cancel()
        mutableState.update { it.copy(query = query) }
        rememberCurrentQuery()
        if (!isSearchable(query)) return
        searchJob = searchScope.launch { search(query) }
    }

    private suspend fun search(query: String) {
        mutableState.update { it.copy(isSearching = true, hasFailed = false) }
        val result = searchNews.execute(SearchNewsRequest(query = query, language = language))
        // A slow corpus response must not replace results for newer typing.
        if (mutableState.value.query != query) return
        when (result) {
            is NewsResult.Success -> {
                analytics.logEvent(
                    AnalyticsEvent.SearchPerformed(
                        resultCount = result.data.size,
                        queryLength = query.trim().length,
                        language = language,
                    )
                )
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        results = result.data,
                        isSettled = true,
                        hasFailed = false,
                    )
                }
            }
            is NewsResult.Error -> {
                // This carries the network's message and never text the reader typed.
                analytics.recordError("Search failed: ${result.error.message}")
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        results = emptyList(),
                        isSettled = true,
                        hasFailed = true,
                    )
                }
            }
        }
    }

    private fun rememberCurrentQuery() {
        val query = mutableState.value.query.trim()
        if (!isSearchable(query)) return
        mutableState.update { it.copy(recentSearches = recentSearches.add(query)) }
    }

    private fun removeRecent(query: String) {
        mutableState.update { it.copy(recentSearches = recentSearches.remove(query)) }
    }

    private fun clearRecent() {
        recentSearches.clear()
        mutableState.update { it.copy(recentSearches = emptyList()) }
    }

    private companion object {
        /** Long enough to avoid searching every prefix while still feeling immediate. */
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300
    }
}
