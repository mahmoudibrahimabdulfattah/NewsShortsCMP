package com.mk.newsshorts.sync

import com.mk.newsshorts.auth.AuthSession
import com.mk.newsshorts.domain.model.NewsArticle
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SyncPublisher {
    /**
     * Queue a user-initiated saved-articles write. The caller does not wait for
     * the remote write; newer queued snapshots replace older queued snapshots.
     */
    fun publishSavedArticles(articles: List<NewsArticle>)

    /**
     * For a write that has to finish before its caller continues — hydration's
     * own — while still taking its turn behind anything already in flight.
     */
    suspend fun publishSavedArticlesNow(articles: List<NewsArticle>)

    /**
     * Queue a user-initiated settings write. The caller does not wait for the
     * remote write; newer queued snapshots replace older queued snapshots.
     */
    fun publishSettings(settings: SyncedSettings)

    /**
     * For a write that has to finish before its caller continues — hydration's
     * own — while still taking its turn behind anything already in flight.
     */
    suspend fun publishSettingsNow(settings: SyncedSettings)

    /**
     * Abandon everything queued or in flight for the account that just went
     * away. Called by whoever observes sign-in, which is the same code that
     * runs [AccountSyncUseCase] — this class deliberately keeps no observer of
     * its own, so that it owns no coroutine that outlives a caller.
     *
     * A `stillCurrent` check is not enough on its own: it is read once, before
     * a write starts, so a slow request that was legitimately current when it
     * left can still land under the old account minutes later.
     */
    fun discardQueued()
}

class DefaultSyncPublisher(
    private val authSession: AuthSession,
    private val remoteSyncClient: RemoteSyncClient,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : SyncPublisher {
    private val savedWrites = ConflatedRemoteWriter<List<NewsArticle>>(
        scope = syncScope,
        write = remoteSyncClient::pushSavedArticles,
    )
    private val settingsWrites = ConflatedRemoteWriter<SyncedSettings>(
        scope = syncScope,
        write = remoteSyncClient::pushSettings,
    )

    // There is deliberately no collector on authSession.user here, even though
    // this class is what has to react to a sign-out. The account observer that
    // already exists — the one that runs AccountSyncUseCase — calls
    // discardQueued() instead.
    //
    // The reason is that a collector never completes, and this class is
    // constructed with a scope its caller owns. In a test that scope is the
    // test's own, where a coroutine that never ends means runTest waits for it
    // until the timeout; moving the collector to a background scope trades that
    // for a worse problem, because runTest does not advance background work, so
    // the writes this class exists to make would stop happening in tests
    // without any test failing to say so.

    override fun publishSavedArticles(articles: List<NewsArticle>) {
        val uid = currentUid() ?: return
        savedWrites.submit(
            uid = uid,
            stillCurrent = { currentUid() == uid },
            value = articles,
        )
    }

    override suspend fun publishSavedArticlesNow(articles: List<NewsArticle>) {
        val uid = currentUid() ?: return
        savedWrites.writeInline(
            uid = uid,
            stillCurrent = { currentUid() == uid },
            value = articles,
        )
    }

    override fun publishSettings(settings: SyncedSettings) {
        val uid = currentUid() ?: return
        settingsWrites.submit(
            uid = uid,
            stillCurrent = { currentUid() == uid },
            value = settings,
        )
    }

    override suspend fun publishSettingsNow(settings: SyncedSettings) {
        val uid = currentUid() ?: return
        settingsWrites.writeInline(
            uid = uid,
            stillCurrent = { currentUid() == uid },
            value = settings,
        )
    }

    override fun discardQueued() {
        savedWrites.discard()
        settingsWrites.discard()
    }

    private fun currentUid(): String? = authSession.user.value?.uid
}

/**
 * One remote document, written one write at a time, newest value wins.
 *
 * Bookmarks and settings both replace their whole document on every write, so
 * they share this exactly: overlapping writes cannot merge, and an older value
 * waiting in a queue has nothing to contribute that the newer one behind it
 * does not already carry. Holding more than the newest would just be writing
 * stale data on purpose.
 *
 * The newest value lives in a conflated channel rather than in a field. That
 * matters because [submit] is called from whichever thread the reader happened
 * to tap on, while the writes run on [scope] — a plain `var` would be shared
 * mutable state across threads, and on Native that is not merely a race but
 * illegal.
 *
 * Every [submit] launches a short-lived job that takes *at most one* value.
 * There is deliberately no long-lived consumer: extra jobs are harmless because
 * [writeLock] serialises them and the channel holds only the newest value, so a
 * job that finds the channel empty simply exits. A permanent consumer would
 * need a field saying whether it is still running, and checking that field is
 * exactly the race this design removes — a submit arriving as the consumer
 * retires would set a value nobody was left to read.
 */
internal class ConflatedRemoteWriter<T>(
    private val scope: CoroutineScope,
    private val write: suspend (uid: String, value: T) -> Unit,
) {
    private val writeLock = Mutex()
    private val writes = Channel<PendingWrite<T>>(Channel.CONFLATED)

    /**
     * Parent of every queued write, so [discard] can cancel them as a group
     * without cancelling [scope], which the caller owns.
     *
     * Deliberately not a child of [scope]'s job. A `SupervisorJob` stays active
     * until something completes it, so as a child it would be a coroutine that
     * never finishes — which is fine in production, where this object lives as
     * long as the process, and fatal in a test, where `runTest` waits out its
     * whole timeout for exactly that.
     */
    private val queuedWrites = SupervisorJob()

    fun submit(uid: String, stillCurrent: () -> Boolean, value: T) {
        writes.trySend(PendingWrite(uid = uid, stillCurrent = stillCurrent, value = value))
        scope.launch(queuedWrites) {
            val next = writes.tryReceive().getOrNull() ?: return@launch
            writeLock.withLock {
                if (next.stillCurrent()) {
                    write(next.uid, next.value)
                }
            }
        }
    }

    /**
     * Drops what is queued and cancels what is already being written.
     *
     * Cancelling matters more than draining: a request that was current when it
     * left can still be in the air when the account changes, and the server has
     * no way to know the reader it belongs to is gone.
     */
    fun discard() {
        queuedWrites.children.forEach { it.cancel() }
        while (writes.tryReceive().isSuccess) {
            // Taking the value out of the channel is the drop.
        }
    }

    /**
     * For a write that has to finish before its caller continues — hydration's
     * own — while still taking its turn behind anything already in flight.
     */
    suspend fun writeInline(uid: String, stillCurrent: () -> Boolean, value: T) {
        writeLock.withLock {
            if (stillCurrent()) {
                write(uid, value)
            }
        }
    }

    private data class PendingWrite<T>(
        val uid: String,
        val stillCurrent: () -> Boolean,
        val value: T,
    )
}
