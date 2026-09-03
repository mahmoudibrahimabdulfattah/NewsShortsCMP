package com.mk.newsshorts.core.domain.sync

import com.mk.newsshorts.core.model.sync.SyncDelete
import com.mk.newsshorts.core.model.sync.SyncFetch
import com.mk.newsshorts.core.model.sync.SyncedSettings
import com.mk.newsshorts.core.model.NewsArticle

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
