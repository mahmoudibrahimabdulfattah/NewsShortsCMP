package com.mk.newsshorts.data.repository

import com.mk.newsshorts.domain.model.ArticleAuthor
import com.mk.newsshorts.domain.model.ArticleContent
import com.mk.newsshorts.domain.model.ArticleDescription
import com.mk.newsshorts.domain.model.ArticleId
import com.mk.newsshorts.domain.model.ArticleTitle
import com.mk.newsshorts.domain.model.ArticleUrl
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsSource
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.domain.model.SourceId
import com.mk.newsshorts.domain.model.SourceName
import com.mk.newsshorts.testing.FakeSavedArticlesLocalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The bookmark list is the one thing in this app a reader deliberately keeps,
 * so every rule that could silently drop one is worth pinning down here.
 */
class SavedArticlesRepositoryTest {

    private fun article(id: String, url: String) = NewsArticle(
        id = ArticleId(id),
        title = ArticleTitle("Headline $id"),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor(""),
        source = NewsSource(SourceId("s"), SourceName("Source")),
        imageUrl = null,
        articleUrl = ArticleUrl(url),
        publishedAt = PublishedTimestamp(0L),
        category = NewsCategory.GENERAL,
    )

    private val a = article("a", "https://example.com/a")
    private val b = article("b", "https://example.com/b")

    private fun urls(articles: List<NewsArticle>) = articles.map { it.articleUrl.value }

    @Test
    fun `load publishes what the store already held`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a, b))
        val repository = SavedArticlesRepository(store)

        repository.load()

        assertEquals(urls(listOf(a, b)), urls(repository.saved.value))
    }

    @Test
    fun `readiness only turns true once the store has actually been read`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a))
        val repository = SavedArticlesRepository(store)
        assertFalse(repository.isLoaded.value)

        var resumed = false
        launch {
            repository.awaitLoaded()
            resumed = true
        }
        runCurrent()
        assertFalse(resumed, "awaitLoaded resumed before the list was read")

        repository.load()
        runCurrent()

        assertTrue(repository.isLoaded.value)
        assertTrue(resumed)
    }

    @Test
    fun `an empty store still counts as loaded`() = runTest {
        val repository = SavedArticlesRepository(FakeSavedArticlesLocalStore())

        repository.load()

        assertTrue(repository.isLoaded.value)
        assertEquals(emptyList(), repository.saved.value)
    }

    @Test
    fun `a new bookmark goes to the front and is written through`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a))
        val repository = SavedArticlesRepository(store)
        repository.load()

        val result = repository.toggle(b)

        assertEquals(ToggleResult.SAVED, result)
        assertEquals(urls(listOf(b, a)), urls(repository.saved.value))
        assertEquals(urls(listOf(b, a)), urls(store.contents))
    }

    @Test
    fun `toggling the same url again removes it`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a, b))
        val repository = SavedArticlesRepository(store)
        repository.load()

        val result = repository.toggle(a)

        assertEquals(ToggleResult.REMOVED, result)
        assertEquals(urls(listOf(b)), urls(repository.saved.value))
        assertEquals(urls(listOf(b)), urls(store.contents))
    }

    @Test
    fun `a different article object with the same url is the same bookmark`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a))
        val repository = SavedArticlesRepository(store)
        repository.load()

        // Same URL, everything else different — the feed hands out fresh
        // instances on every refresh, so identity has to come from the URL.
        val sameUrl = article("different-id", a.articleUrl.value)

        assertEquals(ToggleResult.REMOVED, repository.toggle(sameUrl))
        assertEquals(emptyList(), repository.saved.value)
    }

    @Test
    fun `removing a bookmark that is not there changes nothing`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a))
        val repository = SavedArticlesRepository(store)
        repository.load()
        val savesAfterLoad = store.saveCount

        assertFalse(repository.remove(b))
        assertEquals(urls(listOf(a)), urls(repository.saved.value))
        assertEquals(savesAfterLoad, store.saveCount, "an absent bookmark still hit the disk")
    }

    @Test
    fun `removing a bookmark that is there reports true and persists`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a, b))
        val repository = SavedArticlesRepository(store)
        repository.load()

        assertTrue(repository.remove(a))
        assertEquals(urls(listOf(b)), urls(store.contents))
    }

    @Test
    fun `merging with remote keeps both sides and persists the union`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a))
        val repository = SavedArticlesRepository(store)
        repository.load()

        val merged = repository.mergeWithRemote(listOf(b))

        assertEquals(urls(listOf(a, b)), urls(merged))
        assertEquals(urls(listOf(a, b)), urls(repository.saved.value))
        assertEquals(urls(listOf(a, b)), urls(store.contents))
    }

    @Test
    fun `a url on both sides is not duplicated by the merge`() = runTest {
        val store = FakeSavedArticlesLocalStore(listOf(a))
        val repository = SavedArticlesRepository(store)
        repository.load()

        val merged = repository.mergeWithRemote(listOf(article("remote-copy", a.articleUrl.value), b))

        assertEquals(urls(listOf(a, b)), urls(merged))
    }

    @Test
    fun `the store's cap survives the repository`() = runTest {
        val store = FakeSavedArticlesLocalStore()
        val repository = SavedArticlesRepository(store)
        repository.load()

        repeat(250) { repository.toggle(article("a$it", "https://example.com/$it")) }

        assertEquals(250, repository.saved.value.size)
        assertEquals(200, store.contents.size, "the disk copy was not capped")
    }
}
