package com.mk.newsshorts.sync

import com.mk.newsshorts.auth.AuthSession
import com.mk.newsshorts.data.local.SettingsPersistence
import com.mk.newsshorts.data.repository.SavedArticles
import com.mk.newsshorts.domain.model.NewsArticle

data class SyncOutcome(
    val settings: SyncedSettings? = null,
    val saved: List<NewsArticle> = emptyList(),
)

/**
 * Fetches the signed-in reader's remote copy once, when the caller asks.
 *
 * The use case is deliberately call-driven. A sign-in observer in the caller
 * owns launching and cancelling it; this class only preserves the sync rules:
 * wait for the local saved list, merge bookmarks as a union, seed empty remote
 * documents, and never echo remote-wins settings back to the server.
 */
class AccountSyncUseCase(
    private val remoteSyncClient: RemoteSyncClient,
    private val savedArticles: SavedArticles,
    private val settingsManager: SettingsPersistence,
    private val syncPublisher: SyncPublisher,
    private val authSession: AuthSession,
) {
    suspend operator fun invoke(): SyncOutcome {
        val uid = authSession.user.value?.uid ?: return currentOutcome()

        savedArticles.awaitLoaded()
        if (!isStillSignedInAs(uid)) return currentOutcome()

        val saved = when (val remoteSaved = remoteSyncClient.fetchSavedArticles(uid)) {
            is SyncFetch.Found -> {
                if (!isStillSignedInAs(uid)) return currentOutcome()
                val merged = savedArticles.mergeWithRemote(remoteSaved.value)
                syncPublisher.publishSavedArticlesNow(merged)
                merged
            }
            SyncFetch.NotFound -> {
                if (!isStillSignedInAs(uid)) return currentOutcome()
                val localSaved = savedArticles.saved.value
                syncPublisher.publishSavedArticlesNow(localSaved)
                localSaved
            }
            SyncFetch.Unavailable -> savedArticles.saved.value
        }

        val settings = when (val remoteSettings = remoteSyncClient.fetchSettings(uid)) {
            is SyncFetch.Found -> {
                if (!isStillSignedInAs(uid)) return SyncOutcome(saved = saved)
                remoteSettings.value
            }
            SyncFetch.NotFound -> {
                if (!isStillSignedInAs(uid)) return SyncOutcome(saved = saved)
                syncPublisher.publishSettingsNow(settingsManager.preferences.value.toSyncedSettings())
                null
            }
            SyncFetch.Unavailable -> null
        }

        return SyncOutcome(settings = settings, saved = saved)
    }

    private fun currentOutcome(): SyncOutcome = SyncOutcome(saved = savedArticles.saved.value)

    private fun isStillSignedInAs(uid: String): Boolean =
        authSession.user.value?.uid == uid
}
