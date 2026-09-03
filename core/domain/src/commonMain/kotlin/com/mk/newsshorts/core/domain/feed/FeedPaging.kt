package com.mk.newsshorts.core.domain.feed

import com.mk.newsshorts.core.model.NewsArticle

/**
 * How close to the end of what is loaded a reader gets before the next page is
 * asked for. Five cards at a swipe or two a second is a few seconds of warning,
 * which is enough for a small JSON file on a CDN and short enough that a reader
 * who stops after one card has not pulled a page they will never look at.
 */
const val PREFETCH_DISTANCE: Int = 5

/**
 * Whether the reader is close enough to the end of the loaded feed to fetch the
 * next page.
 *
 * [failed] holds the request back after a page load failed rather than
 * retrying on every swipe — a reader idling near the end of a broken feed
 * would otherwise re-request it several times a second. Reaching the very last
 * card is a deliberate enough signal to try again, and clears it.
 */
fun shouldLoadNextPage(
    currentIndex: Int,
    loadedCount: Int,
    hasNextPage: Boolean,
    isLoading: Boolean,
    failed: Boolean,
): Boolean {
    if (!hasNextPage || isLoading || loadedCount == 0) return false
    if (failed) return currentIndex >= loadedCount - 1
    return currentIndex >= loadedCount - PREFETCH_DISTANCE
}

/**
 * Adds a freshly loaded page to what is already on screen.
 *
 * Append-only, and the existing list is passed through untouched: re-sorting
 * the feed while a reader is part way down it would move the card under their
 * thumb. Ranking is applied to [incoming] before it gets here, so a new page is
 * ordered by what the reader has already read without disturbing anything
 * above it.
 *
 * Articles already on screen are dropped from [incoming] rather than appended
 * again. The page boundary is anchored on the backend precisely so this cannot
 * normally happen, but a refresh landing at the same moment as a page load, or
 * an article a publisher posts twice under two URLs of the same story, would
 * otherwise put the same card in the feed twice.
 */
fun appendPage(current: List<NewsArticle>, incoming: List<NewsArticle>): List<NewsArticle> {
    if (incoming.isEmpty()) return current
    val known = current.mapTo(HashSet()) { it.articleUrl.value }
    val added = incoming.filter { known.add(it.articleUrl.value) }
    return if (added.isEmpty()) current else current + added
}
