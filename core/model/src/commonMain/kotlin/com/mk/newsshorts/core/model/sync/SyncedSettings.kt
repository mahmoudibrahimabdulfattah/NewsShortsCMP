package com.mk.newsshorts.core.model.sync

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
