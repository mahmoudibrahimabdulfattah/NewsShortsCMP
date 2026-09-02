package com.mk.newsshorts.core.domain.sync

import com.mk.newsshorts.core.domain.auth.AuthSession
import com.mk.newsshorts.core.domain.auth.DefaultAuthSession
import com.mk.newsshorts.core.model.auth.AuthUser
import com.mk.newsshorts.core.model.settings.AppPreferences
import com.mk.newsshorts.core.model.sync.SyncFetch
import com.mk.newsshorts.core.model.sync.SyncedSettings
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import com.mk.newsshorts.core.data.repository.DefaultSavedArticlesRepository
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.core.model.ArticleAuthor
import com.mk.newsshorts.core.model.ArticleContent
import com.mk.newsshorts.core.model.ArticleDescription
import com.mk.newsshorts.core.model.ArticleId
import com.mk.newsshorts.core.model.ArticleTitle
import com.mk.newsshorts.core.model.ArticleUrl
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsSource
import com.mk.newsshorts.core.model.PublishedTimestamp
import com.mk.newsshorts.core.model.SourceId
import com.mk.newsshorts.core.model.SourceName
import com.mk.newsshorts.testing.FakeAuthClient
import com.mk.newsshorts.testing.FakeRemoteSyncClient
import com.mk.newsshorts.testing.FakeSavedArticlesLocalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Every test here is a way bookmarks used to be able to disappear without
 * anything failing, so they are written as the sequence that caused it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSyncUseCaseTest {

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
        val auth: FakeAuthClient,
        val sync: FakeRemoteSyncClient,
        val repository: SavedArticles,
        val syncPublisher: DefaultSyncPublisher,
        val applied: MutableList<SyncedSettings>,
        val caller: SyncCaller,
    )

    private fun TestScope.fixture(
        localArticles: List<NewsArticle> = emptyList(),
        settings: SyncedSettings,
    ): Fixture {
        val auth = FakeAuthClient()
        val authSession = DefaultAuthSession(auth)
        val sync = FakeRemoteSyncClient()
        val repository = DefaultSavedArticlesRepository(FakeSavedArticlesLocalStore(localArticles))
        val syncPublisher = DefaultSyncPublisher(
            authSession = authSession,
            remoteSyncClient = sync,
            syncScope = this,
        )
        val useCase = AccountSyncUseCase(
            remoteSyncClient = sync,
            savedArticles = repository,
            settingsManager = RecordingSettingsPersistence(settings.toPreferences()),
            syncPublisher = syncPublisher,
            authSession = authSession,
        )
        val applied = mutableListOf<SyncedSettings>()
        val caller = SyncCaller(
            scope = this,
            auth = auth,
            authSession = authSession,
            syncPublisher = syncPublisher,
            savedArticles = repository,
            useCase = useCase,
            applied = applied,
        )
        return Fixture(auth, sync, repository, syncPublisher, applied, caller)
    }

    private fun TestScope.publisherFixture(): Fixture =
        fixture(settings = settings).also { fixture ->
            fixture.auth.setUser(authUser("reader-1"))
            runCurrent()
        }

    @Test
    fun `a restored session waits for the local list instead of merging against nothing`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.sync.savedArticles = SyncFetch.Found(listOf(remote))

        // Firebase hands back the session before the disk read has happened,
        // which is the cold-start ordering that used to lose local bookmarks.
        f.caller.onUserChanged("reader-1")
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

        f.caller.onUserChanged("reader-1")
        runCurrent()
        f.caller.onUserChanged("reader-2")
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

        f.caller.onUserChanged("reader-1")
        runCurrent()
        f.caller.onUserChanged(null)
        advanceUntilIdle()

        assertTrue(f.sync.pushedSavedArticles.isEmpty(), "a signed-out reader still had data pushed")
        assertTrue(f.applied.isEmpty(), "settings were applied for a signed-out reader")
    }

    @Test
    fun `the same account arriving twice does not hydrate twice`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.repository.load()
        f.sync.savedArticles = SyncFetch.NotFound

        f.caller.onUserChanged("reader-1")
        advanceUntilIdle()
        f.caller.onUserChanged("reader-1")
        advanceUntilIdle()

        assertEquals(1, f.sync.pushedSavedArticles.size)
    }

    @Test
    fun `an account with nothing on the server is seeded with the real local data`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        f.sync.savedArticles = SyncFetch.NotFound
        f.sync.settings = SyncFetch.NotFound

        f.caller.onUserChanged("reader-1")
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

        f.caller.onUserChanged("reader-1")
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

        f.caller.onUserChanged("reader-1")
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

        f.caller.onUserChanged("reader-1")
        runCurrent()
        f.caller.onUserChanged(null)
        advanceUntilIdle()

        assertTrue(f.applied.isEmpty(), "settings from an abandoned account were applied")
    }

    @Test
    fun `a slow older write cannot put back a bookmark a newer one removed`() = runTest {
        val f = publisherFixture()

        // Every write is the whole document, so if the first one finishes last
        // the server keeps the list that still contains the removed bookmark.
        // The first write is made slow and the second fast, which is the only
        // arrangement where the bug can actually show itself.
        f.sync.pushSavedArticlesDelayMs = 1_000
        f.syncPublisher.publishSavedArticles(listOf(local, remote))
        runCurrent()
        f.sync.pushSavedArticlesDelayMs = 0
        f.syncPublisher.publishSavedArticles(listOf(local))
        advanceUntilIdle()

        val finalOnServer = f.sync.pushedSavedArticles.last().second
        assertEquals(urls(listOf(local)), urls(finalOnServer), "the removed bookmark came back")
    }

    @Test
    fun `queued writes are conflated rather than all sent`() = runTest {
        val f = publisherFixture()

        f.sync.pushSavedArticlesDelayMs = 1_000
        f.syncPublisher.publishSavedArticles(listOf(local))
        // Let the writer actually pick that one up and block on it, so the next
        // two arrive with a write genuinely in flight.
        runCurrent()
        f.syncPublisher.publishSavedArticles(listOf(local, remote))
        f.syncPublisher.publishSavedArticles(listOf(remote))
        advanceUntilIdle()

        // The one in flight, then a single write for the two behind it: an
        // older snapshot has nothing the newer one does not already carry.
        assertEquals(2, f.sync.pushedSavedArticles.size)
        assertEquals(urls(listOf(remote)), urls(f.sync.pushedSavedArticles.last().second))
    }

    @Test
    fun `a write submitted exactly as the writer drains is still sent`() = runTest {
        val written = mutableListOf<Int>()
        lateinit var writer: ConflatedRemoteWriter<Int>
        writer = ConflatedRemoteWriter(
            scope = this,
            write = { _, value ->
                written += value
                if (value == 1) {
                    writer.submit(
                        uid = "reader-1",
                        stillCurrent = { true },
                        value = 2,
                    )
                }
            },
        )

        writer.submit(
            uid = "reader-1",
            stillCurrent = { true },
            value = 1,
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 2), written)
    }

    @Test
    fun `a signed-out reader queues nothing`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)

        f.syncPublisher.publishSavedArticles(listOf(local))
        advanceUntilIdle()

        assertTrue(f.sync.pushedSavedArticles.isEmpty())
    }

    @Test
    fun `a write queued for the previous account never reaches the new one`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        // The switch below goes through the caller, which hydrates the account
        // it switches to, and hydration waits for the local list first.
        f.repository.load()
        f.auth.setUser(authUser("reader-1"))
        runCurrent()

        f.sync.pushSavedArticlesDelayMs = 1_000
        f.syncPublisher.publishSavedArticles(listOf(local, remote))
        runCurrent()
        f.caller.onUserChanged("reader-2")
        runCurrent()
        advanceUntilIdle()

        assertTrue(
            f.sync.pushedSavedArticles.none { it.first == "reader-1" },
            "the abandoned account's queued write still went out",
        )
    }

    @Test
    fun `a slow older settings write cannot undo a newer change`() = runTest {
        val f = publisherFixture()

        // The reader switches to dark, changes their mind, switches to light —
        // and the first request is the slow one.
        f.sync.pushSettingsDelayMs = 1_000
        f.syncPublisher.publishSettings(settings.copy(themeMode = "dark"))
        runCurrent()
        f.sync.pushSettingsDelayMs = 0
        f.syncPublisher.publishSettings(settings.copy(themeMode = "light"))
        advanceUntilIdle()

        assertEquals("light", f.sync.pushedSettings.last().second.themeMode)
    }

    @Test
    fun `a burst of settings changes finishes on the newest snapshot`() = runTest {
        val f = publisherFixture()

        f.sync.pushSettingsDelayMs = 1_000
        f.syncPublisher.publishSettings(settings.copy(themeMode = "dark"))
        runCurrent()
        f.syncPublisher.publishSettings(settings.copy(themeMode = "light"))
        f.syncPublisher.publishSettings(settings.copy(themeMode = "system", newsLanguage = "en"))
        advanceUntilIdle()

        assertEquals(2, f.sync.pushedSettings.size)
        val last = f.sync.pushedSettings.last().second
        assertEquals("system", last.themeMode)
        assertEquals("en", last.newsLanguage)
    }

    @Test
    fun `settings queued for the previous account never reach the new one`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)
        // The switch below goes through the caller, which hydrates the account
        // it switches to, and hydration waits for the local list first.
        f.repository.load()
        f.auth.setUser(authUser("reader-1"))
        runCurrent()

        f.sync.pushSettingsDelayMs = 1_000
        f.syncPublisher.publishSettings(settings.copy(themeMode = "dark"))
        runCurrent()
        f.caller.onUserChanged("reader-2")
        runCurrent()
        advanceUntilIdle()

        assertTrue(
            f.sync.pushedSettings.none { it.first == "reader-1" },
            "the abandoned account's queued settings still went out",
        )
    }

    @Test
    fun `a signed-out reader queues no settings`() = runTest {
        val f = fixture(localArticles = listOf(local), settings = settings)

        f.syncPublisher.publishSettings(settings)
        advanceUntilIdle()

        assertTrue(f.sync.pushedSettings.isEmpty())
    }

    private class SyncCaller(
        private val scope: CoroutineScope,
        private val auth: FakeAuthClient,
        private val authSession: AuthSession,
        private val syncPublisher: SyncPublisher,
        private val savedArticles: SavedArticles,
        private val useCase: AccountSyncUseCase,
        private val applied: MutableList<SyncedSettings>,
    ) {
        private var activeUid: String? = null
        private var job: Job? = null

        fun onUserChanged(uid: String?) {
            auth.setUser(uid?.let(::authUser))
            if (uid != null && uid == activeUid) return
            job?.cancel()
            syncPublisher.discardQueued()
            activeUid = uid
            job = uid?.let { launchedUid ->
                scope.launch {
                    val outcome = useCase()
                    if (authSession.user.value?.uid == launchedUid) {
                        apply(outcome)
                    }
                }
            }
        }

        private fun apply(outcome: SyncOutcome) {
            if (outcome.saved != savedArticles.saved.value) {
                savedArticles.replaceAll(outcome.saved)
            }
            outcome.settings?.let { applied += it }
        }
    }

    private class RecordingSettingsPersistence(
        initial: AppPreferences,
    ) : SettingsPersistence {
        private val mutablePreferences = MutableStateFlow(initial)
        override val preferences: StateFlow<AppPreferences> = mutablePreferences.asStateFlow()

        override suspend fun saveAppLocale(localeCode: String) {
            mutablePreferences.update { it.copy(appLocale = localeCode) }
        }

        override suspend fun saveThemeMode(mode: String) {
            mutablePreferences.update { it.copy(themeMode = mode) }
        }

        override suspend fun saveTextScale(scale: String) {
            mutablePreferences.update { it.copy(textScale = scale) }
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            mutablePreferences.update { it.copy(notificationsEnabled = enabled) }
        }

        override suspend fun setNotifyBreaking(enabled: Boolean) {
            mutablePreferences.update { it.copy(notifyBreaking = enabled) }
        }

        override suspend fun setNotifyTopStory(enabled: Boolean) {
            mutablePreferences.update { it.copy(notifyTopStory = enabled) }
        }

        override suspend fun setNotifyReminder(enabled: Boolean) {
            mutablePreferences.update { it.copy(notifyReminder = enabled) }
        }
    }

    private fun SyncedSettings.toPreferences(): AppPreferences = AppPreferences(
        newsLanguage = newsLanguage,
        appLocale = appLocale,
        selectedCountry = selectedCountry,
        themeMode = themeMode,
        notificationsEnabled = notificationsEnabled,
        notifyBreaking = notifyBreaking,
        notifyTopStory = notifyTopStory,
        notifyReminder = notifyReminder,
    )
}

private fun authUser(uid: String): AuthUser = AuthUser(
    uid = uid,
    displayName = "Reader $uid",
    email = "$uid@example.com",
    photoUrl = null,
)
