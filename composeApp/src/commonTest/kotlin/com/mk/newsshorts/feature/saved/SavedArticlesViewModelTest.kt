package com.mk.newsshorts.feature.saved

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.data.repository.DefaultSavedArticlesRepository
import com.mk.newsshorts.data.repository.SavedArticles
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
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.sync.SyncPublisher
import com.mk.newsshorts.sync.SyncedSettings
import com.mk.newsshorts.testing.FakeSavedArticlesLocalStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SavedArticlesViewModelTest {

    private val first = article("first")
    private val second = article("second")

    @Test
    fun `saving and unsaving a story immediately changes the saved screen`() = runTest {
        val store = FakeSavedArticlesLocalStore()
        val syncPublisher = RecordingSyncPublisher()
        val viewModel = viewModel(store, syncPublisher)
        val effects = collectEffects(viewModel)

        viewModel.processEvent(SavedArticlesUiEvent.Toggle(first))
        runCurrent()

        assertEquals(listOf(first), viewModel.uiState.value.articles)
        assertEquals(listOf(first), store.contents)
        assertEquals(listOf(first), syncPublisher.publishedSavedArticles.last())
        assertEquals(
            listOf<SavedArticlesUiEffect>(
                SavedArticlesUiEffect.ShowToast(getStrings(AppLocale.ENGLISH).articleSaved),
            ),
            effects,
        )

        viewModel.processEvent(SavedArticlesUiEvent.Toggle(first))
        runCurrent()

        assertEquals(emptyList<NewsArticle>(), viewModel.uiState.value.articles)
        assertEquals(emptyList<NewsArticle>(), store.contents)
        assertEquals(emptyList<NewsArticle>(), syncPublisher.publishedSavedArticles.last())
        assertEquals(
            listOf<SavedArticlesUiEffect>(
                SavedArticlesUiEffect.ShowToast(getStrings(AppLocale.ENGLISH).articleSaved),
                SavedArticlesUiEffect.ShowToast(getStrings(AppLocale.ENGLISH).articleRemoved),
            ),
            effects,
        )
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
        val syncPublisher = RecordingSyncPublisher()
        val viewModel = viewModel(store, syncPublisher)
        val effects = collectEffects(viewModel)
        viewModel.load()
        runCurrent()
        val writesBeforeRemoval = store.saveCount

        viewModel.processEvent(SavedArticlesUiEvent.Remove(second))
        runCurrent()

        assertEquals(listOf(first), viewModel.uiState.value.articles)
        assertEquals(listOf(first), store.contents)
        assertEquals(writesBeforeRemoval, store.saveCount)
        assertEquals(emptyList(), syncPublisher.publishedSavedArticles)
        assertEquals(emptyList(), effects)
    }

    private fun TestScope.viewModel(
        store: FakeSavedArticlesLocalStore,
        syncPublisher: RecordingSyncPublisher = RecordingSyncPublisher(),
    ): SavedArticlesViewModel = viewModel(
        repository = DefaultSavedArticlesRepository(store),
        syncPublisher = syncPublisher,
    )

    private fun TestScope.viewModel(
        repository: SavedArticles,
        syncPublisher: RecordingSyncPublisher = RecordingSyncPublisher(),
    ): SavedArticlesViewModel = SavedArticlesViewModel(
        repository = repository,
        analytics = RecordingAnalytics(),
        syncPublisher = syncPublisher,
        strings = { getStrings(AppLocale.ENGLISH) },
        scopeOverride = backgroundScope,
    ).also { runCurrent() }

    private fun TestScope.collectEffects(
        viewModel: SavedArticlesViewModel,
    ): MutableList<SavedArticlesUiEffect> {
        val effects = mutableListOf<SavedArticlesUiEffect>()
        backgroundScope.launch {
            viewModel.uiEffect.collect { effects += it }
        }
        runCurrent()
        return effects
    }

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

    private class RecordingSyncPublisher : SyncPublisher {
        val publishedSavedArticles = mutableListOf<List<NewsArticle>>()

        override fun publishSavedArticles(articles: List<NewsArticle>) {
            publishedSavedArticles += articles
        }

        override suspend fun publishSavedArticlesNow(articles: List<NewsArticle>) {
            publishSavedArticles(articles)
        }

        override fun publishSettings(settings: SyncedSettings) = Unit

        override suspend fun publishSettingsNow(settings: SyncedSettings) = Unit

        override fun discardQueued() = Unit
    }
}
