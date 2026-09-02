package com.mk.newsshorts.sync

import com.mk.newsshorts.domain.model.NewsArticle

/**
 * The settings worth following a reader across devices. Deliberately not all
 * of [com.mk.newsshorts.feature.feed.FeedUiState] — `selectedCategory`,
 * `currentArticleIndex` and the rest are session state, not preference.
 */
data class SyncedSettings(
    val newsLanguage: String,
    val appLocale: String,
    val selectedCountry: String,
    val themeMode: String,
    val notificationsEnabled: Boolean,
    val notifyBreaking: Boolean,
    val notifyTopStory: Boolean,
    val notifyReminder: Boolean,
)

/**
 * A fetch has three possible outcomes, not two — and the two failure-shaped
 * ones must never be confused with each other. [NotFound] means this reader
 * has never synced from any device: the right response is to push what is on
 * this device up, so it becomes the seed. [Unavailable] means the question
 * could not be answered at all — offline, a permission error, a flaky
 * connection — and the right response is to do nothing and try again later.
 * Treating [Unavailable] as [NotFound] would mean a dropped connection looks
 * identical to "this reader has no data yet", and the local copy would
 * silently overwrite a remote one it never actually saw.
 */
sealed interface SyncFetch<out T> {
    data class Found<out T>(val value: T) : SyncFetch<T>
    data object NotFound : SyncFetch<Nothing>
    data object Unavailable : SyncFetch<Nothing>
}

/**
 * Account deletion only has two useful remote outcomes for the caller: the
 * synced copy is gone, or it may still exist. A document that was already
 * absent is [Success] — the caller asked for it not to exist, and it does not.
 */
sealed interface SyncDelete {
    data object Success : SyncDelete
    data object Failed : SyncDelete
}

/**
 * Cross-device sync for a signed-in reader. Every push is best-effort from the
 * caller's point of view: a failure here must never be the reason a save or a
 * settings change is lost locally, so callers push in the background and do
 * not block on the result.
 */
interface RemoteSyncClient {
    suspend fun fetchSavedArticles(uid: String): SyncFetch<List<NewsArticle>>
    suspend fun pushSavedArticles(uid: String, articles: List<NewsArticle>)
    suspend fun fetchSettings(uid: String): SyncFetch<SyncedSettings>
    suspend fun pushSettings(uid: String, settings: SyncedSettings)

    /** Removes this reader's synced data — the server half of account deletion. */
    suspend fun deleteUserData(uid: String): SyncDelete
}

/** Used on every target without a Firestore backend — there is no remote copy to strand. */
object NoOpRemoteSyncClient : RemoteSyncClient {
    override suspend fun fetchSavedArticles(uid: String): SyncFetch<List<NewsArticle>> = SyncFetch.Unavailable
    override suspend fun pushSavedArticles(uid: String, articles: List<NewsArticle>) = Unit
    override suspend fun fetchSettings(uid: String): SyncFetch<SyncedSettings> = SyncFetch.Unavailable
    override suspend fun pushSettings(uid: String, settings: SyncedSettings) = Unit
    override suspend fun deleteUserData(uid: String): SyncDelete = SyncDelete.Success
}
