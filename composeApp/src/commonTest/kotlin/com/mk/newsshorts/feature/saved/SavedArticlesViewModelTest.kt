package com.mk.newsshorts.feature.saved

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.data.repository.SavedArticlesRepository
import com.mk.newsshorts.data.repository.ToggleResult
import com.mk.newsshorts.domain.model.ArticleAuthor
import com.mk.newsshorts.domain.model.ArticleContent
import com.mk.newsshorts.domain.model.ArticleDescription
import com.mk.newsshorts.domain.model.ArticleId
import com.mk.newsshorts.domain.model.ArticleTitle
import com.mk.newsshorts.domain.model.ArticleUrl
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsSource
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.domain.model.SourceId
import com.mk.newsshorts.domain.model.SourceName
import com.mk.newsshorts.sync.AccountSyncCoordinator
import com.mk.newsshorts.sync.SyncFetch
import com.mk.newsshorts.sync.SyncedSettings
import com.mk.newsshorts.testing.FakeRemoteSyncClient
import com.mk.newsshorts.testing.FakeSavedArticlesLocalStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SavedArticlesViewModelTest {

    private val first = article("first")
    private val second = article("second")

    @Test
    fun `saving and unsaving a story immediately changes the saved screen`() = runTest {
        val store = FakeSavedArticlesLocalStore()
        val viewModel = viewModel(store)

        val saved = viewModel.processEvent(SavedArticlesUiEvent.Toggle(first))
        runCurrent()

        assertEquals(ToggleResult.SAVED, assertIs<SavedArticlesMutation.Changed>(saved).result)
        assertEquals(listOf(first), viewModel.uiState.value.articles)
        assertEquals(listOf(first), store.contents)

        val removed = viewModel.processEvent(SavedArticlesUiEvent.Toggle(first))
        runCurrent()

        assertEquals(ToggleResult.REMOVED, assertIs<SavedArticlesMutation.Changed>(removed).result)
        assertEquals(emptyList(), viewModel.uiState.value.articles)
        assertEquals(emptyList(), store.contents)
    }

    @Test
    fun `a saved story is still there after the list reloads`() = runTest {
        val store = FakeSavedArticlesLocalStore()
        val firstVisit = viewModel(store)
        firstVisit.processEvent(SavedArticlesUiEvent.Toggle(first))
        runCurrent()

        val reopened = viewModel(store)
        reopened.load()
        runCurrent()

        assertEquals(listOf(first), reopened.uiState.value.articles)
    }

    @Test
    fun `removing a story that was never saved leaves the list untouched`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(first))
        val viewModel = viewModel(store)
        viewModel.load()
        runCurrent()
        val writesBeforeRemoval = store.saveCount

        val result = viewModel.processEvent(SavedArticlesUiEvent.Remove(second))
        runCurrent()

        assertEquals(SavedArticlesMutation.Unchanged, result)
        assertEquals(listOf(first), viewModel.uiState.value.articles)
        assertEquals(listOf(first), store.contents)
        assertEquals(writesBeforeRemoval, store.saveCount)
    }

    @Test
    fun `signing out leaves this device's saved stories available to the guest`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(first))
        val repository = SavedArticlesRepository(store)
        val viewModel = viewModel(repository)
        viewModel.load()
        runCurrent()
        val remote = FakeRemoteSyncClient().apply {
            savedArticles = SyncFetch.Found(listOf(second))
            fetchSavedArticlesDelayMs = 1_000
        }
        val coordinator = AccountSyncCoordinator(
            remoteSyncClient = remote,
            savedArticlesRepository = repository,
            currentSettings = { syncedSettings() },
            applyRemoteSettings = {},
        )

        coordinator.onUserChanged(this, "reader-1")
        runCurrent()
        coordinator.onUserChanged(this, null)
        advanceUntilIdle()

        assertEquals(listOf(first), viewModel.uiState.value.articles)
        assertEquals(listOf(first), store.contents)
    }

    private fun TestScope.viewModel(
        store: FakeSavedArticlesLocalStore,
    ): SavedArticlesViewModel = viewModel(SavedArticlesRepository(store))

    private fun TestScope.viewModel(
        repository: SavedArticlesRepository,
    ): SavedArticlesViewModel = SavedArticlesViewModel(
        repository = repository,
        analytics = RecordingAnalytics(),
        scopeOverride = backgroundScope,
    ).also { runCurrent() }

    private class RecordingAnalytics : AnalyticsReporter {
        override fun logEvent(event: AnalyticsEvent) = Unit
        override fun setProperty(name: String, value: String) = Unit
        override fun recordError(message: String, cause: Throwable?) = Unit
    }

    private fun article(id: String): NewsArticle = NewsArticle(
        id = ArticleId(id),
        title = ArticleTitle("Headline $id"),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor(""),
        source = NewsSource(SourceId("source"), SourceName("Source")),
        imageUrl = null,
        articleUrl = ArticleUrl("https://example.com/$id"),
        publishedAt = PublishedTimestamp(0L),
        category = NewsCategory.GENERAL,
    )

    private fun syncedSettings() = SyncedSettings(
        newsLanguage = "en",
        appLocale = "en",
        selectedCountry = "us",
        themeMode = "system",
        notificationsEnabled = true,
        notifyBreaking = true,
        notifyTopStory = true,
        notifyReminder = true,
    )
}
