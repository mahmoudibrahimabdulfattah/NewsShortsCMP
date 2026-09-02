package com.mk.newsshorts.core.data.repository

import com.mk.newsshorts.core.model.ToggleResult
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.core.data.local.SavedArticlesLocalStore
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.domain.sync.mergeSavedArticles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * The one owner of the bookmark list.
 *
 * It used to live in `FeedUiState` and be mutated in place by three handlers,
 * each of which had to remember to persist and to push. Collecting that here
 * means the rules — match by URL, newest first, write through to disk — are
 * stated once, and can be tested without a ViewModel.
 */
class DefaultSavedArticlesRepository(
    private val store: SavedArticlesLocalStore,
) : SavedArticles {
    private val mutableSaved = MutableStateFlow<List<NewsArticle>>(emptyList())
    override val saved: StateFlow<List<NewsArticle>> = mutableSaved.asStateFlow()

    private val mutableLoaded = MutableStateFlow(false)

    /**
     * False until [load] has actually read the store. An empty list means two
     * very different things before and after that point, and sign-in sync has
     * no way to tell them apart on its own: merging remote data against a list
     * that is empty only because the disk read has not finished yet is how
     * local-only bookmarks get destroyed.
     */
    override val isLoaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

    /** Suspends until the on-disk list has been read at least once. */
    override suspend fun awaitLoaded() {
        isLoaded.first { it }
    }

    /**
     * Suspending because it is disk I/O plus a JSON decode of up to 200
     * articles, and naming that honestly is what lets a caller await it.
     * Deliberately without `withContext(Dispatchers.IO)`: that dispatcher does
     * not exist on js or wasmJs, and every caller is already off the frame.
     */
    override suspend fun load() {
        mutableSaved.value = store.load()
        mutableLoaded.value = true
    }

    /** Adds the article, or removes it if that URL is already bookmarked. */
    override fun toggle(article: NewsArticle): ToggleResult {
        val current = mutableSaved.value
        val existing = current.indexOfFirst { it.articleUrl == article.articleUrl }
        return if (existing != -1) {
            publish(current.toMutableList().apply { removeAt(existing) })
            ToggleResult.REMOVED
        } else {
            // Newest first: a bookmark just made is the one the reader is
            // looking for when they open the Saved tab.
            publish(listOf(article) + current)
            ToggleResult.SAVED
        }
    }

    /** False when that URL was not bookmarked, so the caller can stay silent. */
    override fun remove(article: NewsArticle): Boolean {
        val current = mutableSaved.value
        // Matched by URL, the only stable identity an article has.
        val existing = current.indexOfFirst { it.articleUrl == article.articleUrl }
        if (existing == -1) return false
        publish(current.toMutableList().apply { removeAt(existing) })
        return true
    }

    /**
     * The sign-in union. Returns the merged list because the caller still owns
     * pushing it back to the server.
     */
    override fun mergeWithRemote(remote: List<NewsArticle>): List<NewsArticle> {
        val merged = mergeSavedArticles(local = mutableSaved.value, remote = remote)
        publish(merged)
        return merged
    }

    override fun replaceAll(articles: List<NewsArticle>) {
        publish(articles)
    }

    /** Bookmarks are only useful if they outlive the session. */
    private fun publish(articles: List<NewsArticle>) {
        mutableSaved.value = articles
        store.save(articles)
    }
}
