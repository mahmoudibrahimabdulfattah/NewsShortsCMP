package com.mk.newsshorts.feature.search

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.domain.model.ArticleAuthor
import com.mk.newsshorts.domain.model.ArticleContent
import com.mk.newsshorts.domain.model.ArticleDescription
import com.mk.newsshorts.domain.model.ArticleId
import com.mk.newsshorts.domain.model.ArticleTitle
import com.mk.newsshorts.domain.model.ArticleUrl
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsError
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.model.NewsSource
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.domain.model.SourceId
import com.mk.newsshorts.domain.model.SourceName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @Test
    fun `a query settles with results for the opened language`() = runTest {
        val result = article("Cairo museum reopens")
        val requests = mutableListOf<SearchNewsRequest>()
        val viewModel = viewModel(
            searchNews = SearchNews { request ->
                requests += request
                NewsResult.Success(listOf(result))
            }
        )

        viewModel.processEvent(SearchUiEvent.Opened("ar"))
        viewModel.processEvent(SearchUiEvent.QueryChanged("cairo"))
        advanceUntilIdle()

        assertEquals(listOf(SearchNewsRequest("cairo", "ar")), requests)
        assertEquals(listOf(result), viewModel.uiState.value.results)
        assertTrue(viewModel.uiState.value.isSettled)
        assertFalse(viewModel.uiState.value.isSearching)
        assertFalse(viewModel.uiState.value.hasFailed)
    }

    @Test
    fun `a failed query settles in the unavailable state`() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(
            searchNews = SearchNews { NewsResult.Error(NewsError.NetworkError) },
            analytics = analytics,
        )

        viewModel.processEvent(SearchUiEvent.Submitted("cairo"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSettled)
        assertTrue(viewModel.uiState.value.hasFailed)
        assertEquals(emptyList(), viewModel.uiState.value.results)
        assertEquals(1, analytics.errors.size)
    }

    @Test
    fun `closing search cancels the in-flight query and clears the visit`() = runTest {
        var started = false
        var cancelled = false
        val viewModel = viewModel(
            searchNews = SearchNews {
                started = true
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        )

        viewModel.processEvent(SearchUiEvent.QueryChanged("cairo"))
        advanceTimeBy(300)
        runCurrent()
        assertTrue(started)

        viewModel.processEvent(SearchUiEvent.Closed)
        runCurrent()

        assertTrue(cancelled)
        assertEquals(SearchUiState(), viewModel.uiState.value)
    }

    @Test
    fun `opening a result records the current query as recent`() = runTest {
        val recent = FakeRecentSearches()
        val result = article("Cairo museum reopens")
        val viewModel = viewModel(
            searchNews = SearchNews { NewsResult.Success(listOf(result)) },
            recentSearches = recent,
        )

        viewModel.processEvent(SearchUiEvent.QueryChanged("  cairo  "))
        advanceUntilIdle()
        viewModel.processEvent(SearchUiEvent.ResultOpened(result))

        assertEquals(listOf("cairo"), recent.added)
        assertEquals(listOf("cairo"), viewModel.uiState.value.recentSearches)
    }

    @Test
    fun `Arabic folding finds a headline through the ViewModel`() = runTest {
        val result = article("قصف جديد على غزة")
        val index = SearchIndex.from(listOf(result))
        val viewModel = viewModel(
            searchNews = SearchNews { request ->
                NewsResult.Success(index.search(request.query))
            }
        )

        viewModel.processEvent(SearchUiEvent.Submitted("غزه"))
        advanceUntilIdle()

        assertEquals(listOf(result), viewModel.uiState.value.results)
        assertTrue(viewModel.uiState.value.isSettled)
    }

    private fun TestScope.viewModel(
        searchNews: SearchNews,
        recentSearches: FakeRecentSearches = FakeRecentSearches(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): SearchViewModel = SearchViewModel(
        searchNews = searchNews,
        recentSearches = recentSearches,
        analytics = analytics,
        scopeOverride = this,
    )

    private class FakeRecentSearches(
        private var values: List<String> = emptyList(),
    ) : RecentSearches {
        val added = mutableListOf<String>()

        override fun load(): List<String> = values

        override fun add(query: String): List<String> {
            added += query
            values = listOf(query) + values.filterNot { it == query }
            return values
        }

        override fun remove(query: String): List<String> {
            values = values.filterNot { it == query }
            return values
        }

        override fun clear() {
            values = emptyList()
        }
    }

    private class RecordingAnalytics : AnalyticsReporter {
        val events = mutableListOf<AnalyticsEvent>()
        val errors = mutableListOf<String>()

        override fun logEvent(event: AnalyticsEvent) {
            events += event
        }

        override fun setProperty(name: String, value: String) = Unit

        override fun recordError(message: String, cause: Throwable?) {
            errors += message
        }
    }

    private fun article(title: String): NewsArticle = NewsArticle(
        id = ArticleId(title),
        title = ArticleTitle(title),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor(""),
        source = NewsSource(SourceId("source"), SourceName("Source")),
        imageUrl = null,
        articleUrl = ArticleUrl("https://example.com/${title.length}"),
        publishedAt = PublishedTimestamp(0L),
        category = NewsCategory.GENERAL,
    )
}
