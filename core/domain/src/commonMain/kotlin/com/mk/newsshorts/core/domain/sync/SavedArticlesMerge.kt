package com.mk.newsshorts.core.domain.sync

import com.mk.newsshorts.core.model.NewsArticle

/**
 * The merge rule for the moment a reader signs in on a device that already
 * has its own bookmarks: a union, never a replacement. Overwriting either
 * side would mean losing bookmarks made where the other copy did not know
 * about them yet — a guest session on this device, or a save made from
 * another device before this one ever signed in.
 *
 * De-duplicated by `articleUrl`, the only stable identity an article has.
 * [local] wins on a duplicate — it is the copy the reader can see right now —
 * but that only matters for which row's other fields survive; the URL itself
 * is what decides membership. Capping to the store's own limit is left to
 * `SavedArticlesStore.save()`, which already owns that number.
 */
fun mergeSavedArticles(local: List<NewsArticle>, remote: List<NewsArticle>): List<NewsArticle> {
    val seenUrls = local.mapTo(HashSet()) { it.articleUrl.value }
    val remoteOnly = remote.filterNot { it.articleUrl.value in seenUrls }
    return local + remoteOnly
}
