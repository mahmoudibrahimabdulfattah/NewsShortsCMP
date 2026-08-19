package com.mk.newsshorts.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which notifications the reader has dealt with.
 *
 * Two things clear a mark and nothing else does: opening that notification, or
 * saying so for all of them. Merely looking at the inbox does not — the marks
 * are what tells a reader which stories they have not gone into yet, and a list
 * that forgets that the moment it is opened cannot answer the question it exists
 * to answer.
 *
 * So it is stored as both: [InboxReadState.readAllBefore] is the sweep, and
 * [InboxReadState.readIds] holds the individual ones opened since. Keeping the
 * sweep is what stops the set growing without end — everything below it is
 * already read, so those ids can be dropped.
 */
class NotificationInboxStore(
    private val settingsStorage: SettingsStorage,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(): InboxReadState = decodeInboxReadState(settingsStorage.getString(KEY_READ, ""), json)

    /** One notification, opened. */
    fun markRead(sentAt: Long) {
        if (sentAt <= 0) return
        val current = read()
        if (current.isRead(sentAt)) return
        // Oldest dropped first: set addition keeps insertion order, so the tail
        // is the most recently opened.
        val tracked = (current.readIds + sentAt).toList().takeLast(MAX_TRACKED_IDS)
        save(current.copy(readIds = tracked.toSet()))
    }

    /**
     * Everything up to [newestSentAt], in one row of storage rather than one id
     * per notification — and the ids below it become redundant, so they go.
     */
    fun markAllRead(newestSentAt: Long) {
        if (newestSentAt <= 0) return
        val current = read()
        if (current.readAllBefore >= newestSentAt) return
        save(
            InboxReadState(
                readAllBefore = newestSentAt,
                readIds = current.readIds.filter { it > newestSentAt }.toSet(),
            )
        )
    }

    private fun save(state: InboxReadState) {
        runCatching { settingsStorage.putString(KEY_READ, json.encodeToString(state)) }
    }

    private companion object {
        const val KEY_READ: String = "notification_inbox_read"

        /**
         * The published inbox holds a month at four notifications a day, so this
         * is far more headroom than the list itself has. It exists only so a
         * corrupted or ancient value cannot grow unbounded.
         */
        const val MAX_TRACKED_IDS: Int = 200
    }
}

/**
 * [readAllBefore] is a sweep: everything sent at or before it is read.
 * [readIds] are the ones opened individually since that sweep.
 */
@Serializable
data class InboxReadState(
    val readAllBefore: Long = 0,
    val readIds: Set<Long> = emptySet(),
) {
    fun isRead(sentAt: Long): Boolean = sentAt <= readAllBefore || sentAt in readIds
}

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
