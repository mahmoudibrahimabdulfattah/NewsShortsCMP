package com.mk.newsshorts.sync

import com.mk.newsshorts.data.repository.SavedArticlesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns everything that happens when the signed-in reader changes.
 *
 * It exists because three separate things could go wrong when this ran loose
 * inside the ViewModel, and all three are invisible when they do:
 *
 * 1. **Hydrating before the local list is loaded.** Firebase hands back a
 *    restored session immediately at cold start, while the bookmarks are still
 *    being read from disk. Merging remote data against a list that is empty
 *    only because loading has not finished destroys every local-only bookmark.
 *    [SavedArticlesRepository.awaitLoaded] is the fix, and the reason the
 *    repository grew a readiness signal in the first place.
 *
 * 2. **One account's fetch finishing after another has signed in.** Account
 *    switching left the old hydration running; whatever it fetched was written
 *    into the new reader's state.
 *
 * 3. **Signing out mid-hydration.** Same as above, with nobody to own the
 *    result.
 *
 * Two and three are the same fix: one job at a time, cancelled the moment the
 * reader changes, plus a check that the account is still current before
 * anything is written. Cancellation alone would very nearly do it — the check
 * is there because "very nearly" is how bookmarks go missing.
 */
class AccountSyncCoordinator(
    private val remoteSyncClient: RemoteSyncClient,
    private val savedArticlesRepository: SavedArticlesRepository,
    private val currentSettings: () -> SyncedSettings,
    private val applyRemoteSettings: suspend (SyncedSettings) -> Unit,
) {
    private var activeUid: String? = null
    private var job: Job? = null

    /**
     * Call on every auth change, including sign-out ([uid] null) and including
     * a session Firebase restored on its own. Signing in as the same account
     * twice does not re-hydrate.
     */
    fun onUserChanged(scope: CoroutineScope, uid: String?) {
        if (uid != null && uid == activeUid) return
        job?.cancel()
        activeUid = uid
        job = if (uid == null) null else scope.launch { hydrate(uid) }
    }

    internal suspend fun hydrate(uid: String) {
        // Before anything remote is read: a merge against a list that has not
        // been loaded yet is a merge against nothing.
        savedArticlesRepository.awaitLoaded()
        if (activeUid != uid) return

        when (val remoteSaved = remoteSyncClient.fetchSavedArticles(uid)) {
            is SyncFetch.Found -> {
                if (activeUid != uid) return
                val merged = savedArticlesRepository.mergeWithRemote(remoteSaved.value)
                remoteSyncClient.pushSavedArticles(uid, merged)
            }
            SyncFetch.NotFound -> {
                if (activeUid != uid) return
                remoteSyncClient.pushSavedArticles(uid, savedArticlesRepository.saved.value)
            }
            // Offline or a transient failure: neither side is touched, and the
            // next launch (or the next save) tries again.
            SyncFetch.Unavailable -> Unit
        }

        when (val remoteSettings = remoteSyncClient.fetchSettings(uid)) {
            is SyncFetch.Found -> {
                if (activeUid != uid) return
                applyRemoteSettings(remoteSettings.value)
            }
            SyncFetch.NotFound -> {
                if (activeUid != uid) return
                remoteSyncClient.pushSettings(uid, currentSettings())
            }
            SyncFetch.Unavailable -> Unit
        }
    }
}
