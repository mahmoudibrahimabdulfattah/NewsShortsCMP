package com.mk.newsshorts.core.data.local

import com.mk.newsshorts.core.domain.repository.InboxReadMarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which notifications the reader has dealt with.
 *
 * Two things clear a mark and nothing else does: opening that notification —
 * from the inbox *or* from the system tray — or saying so for all of them.
 * Merely looking at the inbox does not: the marks are what tells a reader which
 * stories they have not gone into yet, and a list that forgets that the moment
 * it is opened cannot answer the question it exists to answer.
 *
 * Individual marks are keyed by **the article**, not by when it was sent. A push
 * tapped from the tray arrives as a deep link carrying the article's URL and
 * nothing about the send, and on a cold start it arrives before the published
 * list has been fetched — so a mark keyed on the notification could not be
 * written at the one moment it matters most. Keyed on the article it can be
 * written immediately and is still true when the list turns up.
 *
 * The URL is stored as a hash, the same trade `SeenArticlesStore` makes and for
 * the same reason: a couple of hundred ints where a couple of hundred URLs
 * would not fit.
 *
 * [InboxReadState.readAllBefore] is the sweep that "mark all read" writes.
 * Keeping it is what stops the set growing without end — everything sent at or
 * before it is read, so those keys can be dropped.
 */
class NotificationInboxStore(
    private val settingsStorage: SettingsStorage,
) : InboxReadMarker {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableReadState = MutableStateFlow(readStoredState())
    val readState: StateFlow<InboxReadState> = mutableReadState.asStateFlow()
    private val mutableDismissedState = MutableStateFlow(readStoredDismissed())
    val dismissedState: StateFlow<Set<Int>> = mutableDismissedState.asStateFlow()

    fun read(): InboxReadState = readState.value

    /**
     * The notifications this reader has swiped away.
     *
     * Local, and only local. The list itself is one file published for every
     * reader, so there is nothing on the server a dismissal could remove — this
     * hides a row on this device and says nothing about anyone else's.
     *
     * Keyed by article for the same reason the read marks are: it is the one
     * identity every route to a notification carries.
     */
    fun dismissed(): Set<Int> = dismissedState.value

    fun dismiss(articleUrl: String) {
        if (articleUrl.isBlank()) return
        val key = articleKey(articleUrl)
        val current = dismissed()
        if (key in current) return
        saveDismissed((current + key).toList().takeLast(MAX_TRACKED_KEYS).toSet())
    }

    /** Undo. Kept as its own call so the snackbar has something exact to do. */
    fun restore(articleUrl: String) {
        if (articleUrl.isBlank()) return
        val key = articleKey(articleUrl)
        val current = dismissed()
        if (key !in current) return
        saveDismissed(current - key)
    }

    private fun saveDismissed(keys: Set<Int>) {
        runCatching {
            settingsStorage.putString(KEY_DISMISSED, json.encodeToString(DismissedArticles(keys)))
            mutableDismissedState.value = keys
        }
    }

    /**
     * One article, opened — from a row in the inbox, or from the notification
     * itself out in the tray.
     */
    override fun markRead(articleUrl: String) {
        if (articleUrl.isBlank()) return
        val key = articleKey(articleUrl)
        val current = read()
        if (key in current.readArticles) return
        // Oldest dropped first: set addition keeps insertion order, so the tail
        // is the most recently opened.
        val tracked = (current.readArticles + key).toList().takeLast(MAX_TRACKED_KEYS)
        save(current.copy(readArticles = tracked.toSet()))
    }

    /**
     * Everything up to [newestSentAt], in one row of storage rather than one id
     * per notification — and the ids below it become redundant, so they go.
     */
    fun markAllRead(newestSentAt: Long) {
        if (newestSentAt <= 0) return
        val current = read()
        if (current.readAllBefore >= newestSentAt) return
        // The sweep covers everything currently listed, so the individual keys
        // below it say nothing the sweep does not. Anything opened from the tray
        // after this will add itself back.
        save(InboxReadState(readAllBefore = newestSentAt))
    }

    private fun save(state: InboxReadState) {
        runCatching {
            settingsStorage.putString(KEY_READ, json.encodeToString(state))
            mutableReadState.value = state
        }
    }

    private fun readStoredState(): InboxReadState =
        decodeInboxReadState(settingsStorage.getString(KEY_READ, ""), json)

    private fun readStoredDismissed(): Set<Int> =
        decodeDismissed(settingsStorage.getString(KEY_DISMISSED, ""), json)

    private companion object {
        const val KEY_READ: String = "notification_inbox_read"
        const val KEY_DISMISSED: String = "notification_inbox_dismissed"

        /**
         * The published inbox holds a month at four notifications a day, so this
         * is far more headroom than the list itself has. It exists only so a
         * corrupted or ancient value cannot grow unbounded.
         */
        const val MAX_TRACKED_KEYS: Int = 200
    }
}

/**
 * [readAllBefore] is a sweep: everything sent at or before it is read.
 * [readArticles] are the articles opened individually, by [articleKey].
 */
@Serializable
data class InboxReadState(
    val readAllBefore: Long = 0,
    val readArticles: Set<Int> = emptySet(),
) {
    fun isRead(sentAt: Long, articleUrl: String): Boolean =
        sentAt <= readAllBefore || articleKey(articleUrl) in readArticles
}

/**
 * Identity used by the inbox read and dismissed sets, so callers must key
 * articles the same way.
 */
fun articleKey(articleUrl: String): Int = articleUrl.trim().hashCode()

/**
 * Anything unreadable reads as nothing read.
 *
 * That errs towards showing a mark on something the reader has already opened
 * rather than hiding one they have not — the first is a moment's confusion they
 * can clear themselves, the second silently loses the story.
 */
internal fun decodeInboxReadState(raw: String, json: Json): InboxReadState =
    if (raw.isBlank()) InboxReadState()
    else runCatching { json.decodeFromString<InboxReadState>(raw) }.getOrElse { InboxReadState() }

@Serializable
private data class DismissedArticles(val keys: Set<Int> = emptySet())

/** Unreadable reads as nothing dismissed, which shows a row rather than hiding it. */
internal fun decodeDismissed(raw: String, json: Json): Set<Int> =
    if (raw.isBlank()) emptySet()
    else runCatching { json.decodeFromString<DismissedArticles>(raw).keys }.getOrElse { emptySet() }
