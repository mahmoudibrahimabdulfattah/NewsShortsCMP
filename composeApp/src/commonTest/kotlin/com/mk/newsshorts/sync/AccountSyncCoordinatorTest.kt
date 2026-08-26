package com.mk.newsshorts.sync

import com.mk.newsshorts.data.repository.SavedArticlesRepository
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
import com.mk.newsshorts.testing.FakeRemoteSyncClient
import com.mk.newsshorts.testing.FakeSavedArticlesLocalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Every test here is a way bookmarks used to be able to disappear without
 * anything failing, so they are written as the sequence that caused it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSyncCoordinatorTest {

    private fun article(id: String, url: String) = NewsArticle(
        id = ArticleId(id),
        title = ArticleTitle("Headline $id"),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor(""),
        source = NewsSource(SourceId("s"), SourceName("Source")),
        imageUrl = null,
        articleUrl = ArticleUrl(url),
        publishedAt = PublishedTimestamp(0L),
        category = NewsCategory.GENERAL,
    )

    private val local = article("local", "https://example.com/local")
    private val remote = article("remote", "https://example.com/remote")

    private val settings = SyncedSettings(
        newsLanguage = "ar",
        appLocale = "ar",
        selectedCountry = "eg",
        themeMode = "dark",
        notificationsEnabled = true,
        notifyBreaking = true,
        notifyTopStory = true,
        notifyReminder = true,
    )

    private fun urls(articles: List<NewsArticle>) = articles.map { it.articleUrl.value }.sorted()

    private class Fixture(
        val sync: FakeRemoteSyncClient,
        val repository: SavedArticlesRepository,
        val coordinator: AccountSyncCoordinator,
        val applied: MutableList<SyncedSettings>,
    )

    private fun fixture(
        localArticles: List<NewsArticle> = emptyList(),
        settings: SyncedSettings,
    ): Fixture {
        val sync = FakeRemoteSyncClient()
        val repository = SavedArticlesRepository(FakeSavedArticlesLocalStore(localArticles))
        val applied = mutableListOf<SyncedSettings>()
        val coordinator = AccountSyncCoordinator(
            remoteSyncClient = sync,
            savedArticlesRepository = repository,
            currentSettings = { settings },
            applyRemoteSettings = { applied += it },
        )
        return Fixture(sync, repository, coordinator, applied)
    }

    @Test
    fun `a restored session waits for the local list instead of merging against nothing`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.sync.savedArticles = SyncFetch.Found(listOf(remote))

        // Firebase hands back the session before the disk read has happened,
        // which is the cold-start ordering that used to lose local bookmarks.
        f.coordinator.onUserChanged(this, "reader-1")
        runCurrent()
        assertTrue(f.sync.pushedSavedArticles.isEmpty(), "hydration ran before the list was loaded")

        f.repository.load()
        advanceUntilIdle()

        val pushed = f.sync.pushedSavedArticles.single().second
        assertEquals(urls(listOf(local, remote)), urls(pushed), "a local-only bookmark was lost")
        assertEquals(urls(listOf(local, remote)), urls(f.repository.saved.value))
    }

    @Test
    fun `switching accounts does not let the first one write into the second`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.Found(listOf(remote))
        f.sync.fetchSavedArticlesDelayMs = 1_000

        f.coordinator.onUserChanged(this, "reader-1")
        runCurrent()
        f.coordinator.onUserChanged(this, "reader-2")
        advanceUntilIdle()

        val uids = f.sync.pushedSavedArticles.map { it.first }
        assertEquals(listOf("reader-2"), uids, "the abandoned account still wrote")
    }

    @Test
    fun `signing out cancels a hydration already in flight`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.Found(listOf(remote))
        f.sync.fetchSavedArticlesDelayMs = 1_000

        f.coordinator.onUserChanged(this, "reader-1")
        runCurrent()
        f.coordinator.onUserChanged(this, null)
        advanceUntilIdle()

        assertTrue(f.sync.pushedSavedArticles.isEmpty(), "a signed-out reader still had data pushed")
        assertTrue(f.applied.isEmpty(), "settings were applied for a signed-out reader")
    }

    @Test
    fun `the same account arriving twice does not hydrate twice`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound

        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()

        assertEquals(1, f.sync.pushedSavedArticles.size)
    }

    @Test
    fun `an account with nothing on the server is seeded with the real local data`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.NotFound

        f.coordinator.onUserChanged(this, "reader-1")
        runCurrent()
        f.repository.load()
        advanceUntilIdle()

        assertEquals(urls(listOf(local)), urls(f.sync.pushedSavedArticles.single().second))
        assertEquals(settings, f.sync.pushedSettings.single().second)
    }

    @Test
    fun `an unavailable server leaves both sides alone`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.Unavailable
        f.sync.settings = SyncFetch.Unavailable

        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()

        assertTrue(f.sync.pushedSavedArticles.isEmpty())
        assertTrue(f.sync.pushedSettings.isEmpty())
        assertTrue(f.applied.isEmpty())
        assertEquals(urls(listOf(local)), urls(f.repository.saved.value))
    }

    @Test
    fun `remote settings are applied when the server has them`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        val fromServer = settings.copy(newsLanguage = "en", themeMode = "light")
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.Found(fromServer)

        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()

        assertEquals(listOf(fromServer), f.applied)
        assertTrue(f.sync.pushedSettings.isEmpty(), "remote-wins still pushed back")
    }

    @Test
    fun `a sign-out between the two fetches stops the settings half`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.Found(settings)
        f.sync.fetchSettingsDelayMs = 1_000

        f.coordinator.onUserChanged(this, "reader-1")
        launch {
            // Land the sign-out while the settings fetch is still outstanding.
            f.coordinator.onUserChanged(this, null)
        }
        runCurrent()
        advanceUntilIdle()

        assertTrue(f.applied.isEmpty(), "settings from an abandoned account were applied")
    }

    @Test
    fun `a slow older write cannot put back a bookmark a newer one removed`() = runTest {
        val f = fixture(localArticles = listOf(local, remote), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.sync.pushedSavedArticles.clear()

        // Every write is the whole document, so if the first one finishes last
        // the server keeps the list that still contains the removed bookmark.
        // The first write is made slow and the second fast, which is the only
        // arrangement where the bug can actually show itself.
        f.sync.pushSavedArticlesDelayMs = 1_000
        f.coordinator.pushSavedArticles(this, listOf(local, remote))
        runCurrent()
        f.sync.pushSavedArticlesDelayMs = 0
        f.coordinator.pushSavedArticles(this, listOf(local))
        advanceUntilIdle()

        val finalOnServer = f.sync.pushedSavedArticles.last().second
        assertEquals(urls(listOf(local)), urls(finalOnServer), "the removed bookmark came back")
    }

    @Test
    fun `queued writes are conflated rather than all sent`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.sync.pushedSavedArticles.clear()

        f.sync.pushSavedArticlesDelayMs = 1_000
        f.coordinator.pushSavedArticles(this, listOf(local))
        // Let the writer actually pick that one up and block on it, so the next
        // two arrive with a write genuinely in flight.
        runCurrent()
        f.coordinator.pushSavedArticles(this, listOf(local, remote))
        f.coordinator.pushSavedArticles(this, listOf(remote))
        advanceUntilIdle()

        // The one in flight, then a single write for the two behind it: an
        // older snapshot has nothing the newer one does not already carry.
        assertEquals(2, f.sync.pushedSavedArticles.size)
        assertEquals(urls(listOf(remote)), urls(f.sync.pushedSavedArticles.last().second))
    }

    @Test
    fun `a signed-out reader queues nothing`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()

        f.coordinator.pushSavedArticles(this, listOf(local))
        advanceUntilIdle()

        assertTrue(f.sync.pushedSavedArticles.isEmpty())
    }

    @Test
    fun `a write queued for the previous account never reaches the new one`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.sync.pushedSavedArticles.clear()

        f.sync.pushSavedArticlesDelayMs = 1_000
        f.coordinator.pushSavedArticles(this, listOf(local, remote))
        runCurrent()
        f.coordinator.onUserChanged(this, "reader-2")
        advanceUntilIdle()

        assertTrue(
            f.sync.pushedSavedArticles.none { it.first == "reader-1" },
            "the abandoned account's queued write still went out",
        )
    }

    @Test
    fun `a slow older settings write cannot undo a newer change`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.NotFound
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.sync.pushedSettings.clear()

        // The reader switches to dark, changes their mind, switches to light —
        // and the first request is the slow one.
        f.sync.pushSettingsDelayMs = 1_000
        f.coordinator.pushSettings(this, settings.copy(themeMode = "dark"))
        runCurrent()
        f.sync.pushSettingsDelayMs = 0
        f.coordinator.pushSettings(this, settings.copy(themeMode = "light"))
        advanceUntilIdle()

        assertEquals("light", f.sync.pushedSettings.last().second.themeMode)
    }

    @Test
    fun `a burst of settings changes finishes on the newest snapshot`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.NotFound
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.sync.pushedSettings.clear()

        f.sync.pushSettingsDelayMs = 1_000
        f.coordinator.pushSettings(this, settings.copy(themeMode = "dark"))
        runCurrent()
        f.coordinator.pushSettings(this, settings.copy(themeMode = "light"))
        f.coordinator.pushSettings(this, settings.copy(themeMode = "system", newsLanguage = "en"))
        advanceUntilIdle()

        assertEquals(2, f.sync.pushedSettings.size)
        val last = f.sync.pushedSettings.last().second
        assertEquals("system", last.themeMode)
        assertEquals("en", last.newsLanguage)
    }

    @Test
    fun `settings queued for the previous account never reach the new one`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.NotFound
        f.coordinator.onUserChanged(this, "reader-1")
        advanceUntilIdle()
        f.sync.pushedSettings.clear()

        f.sync.pushSettingsDelayMs = 1_000
        f.coordinator.pushSettings(this, settings.copy(themeMode = "dark"))
        runCurrent()
        f.coordinator.onUserChanged(this, "reader-2")
        advanceUntilIdle()

        assertTrue(
            f.sync.pushedSettings.none { it.first == "reader-1" },
            "the abandoned account's queued settings still went out",
        )
    }

    @Test
    fun `a signed-out reader queues no settings`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()

        f.coordinator.pushSettings(this, settings)
        advanceUntilIdle()

        assertTrue(f.sync.pushedSettings.isEmpty())
    }
}
