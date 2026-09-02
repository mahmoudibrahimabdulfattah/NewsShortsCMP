package com.mk.newsshorts.core.domain.ranking

import com.mk.newsshorts.core.model.NewsArticle

/**
 * Moves already-read articles to the end, without dropping them.
 *
 * Hiding a read article outright would mean a reader who wants yesterday's
 * story back can never scroll to it again, and a feed that silently loses
 * items reads as broken rather than smart. A stable partition keeps every
 * story reachable while putting the ones worth seeing first, first.
 *
 * Stable within each half: this only decides fresh-vs-read, never re-sorts —
 * that job still belongs to the server's `published_at DESC`.
 */
fun List<NewsArticle>.deprioritiseSeen(seenUrlHashes: Set<Int>): List<NewsArticle> {
    if (seenUrlHashes.isEmpty()) return this
    val (unseen, seen) = partition { it.articleUrl.value.hashCode() !in seenUrlHashes }
    return unseen + seen
}
