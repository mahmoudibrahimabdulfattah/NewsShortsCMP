package com.mk.newsshorts.presentation.viewmodel

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
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NewsUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsViewModelFeedMemoryTest {

    @Test
    fun `returning to a category restores article five without replacing the feed`() {
        val memory = CategoryFeedMemory()
        val sportsArticles = articles(NewsCategory.SPORTS, "sports")
        val sports = feedState(
            category = NewsCategory.SPORTS,
            articles = sportsArticles,
            index = 5,
            revision = 7,
            nextPageFile = "sports-page-2.json",
        )

        val firstTechnologySelection = sports.withSelectedCategory(
            category = NewsCategory.TECHNOLOGY,
            remembered = memory.rememberAndFind(sports, NewsCategory.TECHNOLOGY),
        )
        val technology = firstTechnologySelection.withLoadedFeed(
            articles = articles(NewsCategory.TECHNOLOGY, "technology"),
            nextPageFile = "technology-page-2.json",
            preserveReaderPosition = false,
        ).copy(currentArticleIndex = 3)
        val revisionBeforeReturn = technology.feedRevision

        assertEquals(0, firstTechnologySelection.categoryRestoreRevision)

        val returned = technology.withSelectedCategory(
            category = NewsCategory.SPORTS,
            remembered = memory.rememberAndFind(technology, NewsCategory.SPORTS),
        )

        assertEquals(5, returned.currentArticleIndex)
        assertEquals(revisionBeforeReturn, returned.feedRevision)
        assertEquals(1, returned.categoryRestoreRevision)
        assertEquals(sportsArticles, returned.articles)
        assertEquals("sports-page-2.json", returned.nextPageFile)
    }

    @Test
    fun `the first visit to a category starts at the first article`() {
        val memory = CategoryFeedMemory()
        val sports = feedState(
            category = NewsCategory.SPORTS,
            articles = articles(NewsCategory.SPORTS, "sports"),
            index = 5,
            revision = 4,
        )

        val selected = sports.withSelectedCategory(
            category = NewsCategory.BUSINESS,
            remembered = memory.rememberAndFind(sports, NewsCategory.BUSINESS),
        )

        assertEquals(0, selected.currentArticleIndex)
        assertEquals(0, selected.categoryRestoreRevision)
        assertEquals(NewsCategory.BUSINESS, selected.selectedCategory)
    }

    @Test
    fun `pull to refresh replaces the feed and returns to the first article`() {
        val state = feedState(
            category = NewsCategory.SPORTS,
            articles = articles(NewsCategory.SPORTS, "old"),
            index = 5,
            revision = 9,
        )

        val refreshed = state.withLoadedFeed(
            articles = articles(NewsCategory.SPORTS, "fresh"),
            nextPageFile = "fresh-page-2.json",
            preserveReaderPosition = false,
        )

        assertEquals(0, refreshed.currentArticleIndex)
        assertEquals(10, refreshed.feedRevision)
    }

    @Test
    fun `a restored category refresh follows the current article to its new index`() {
        val remembered = articles(NewsCategory.SPORTS, "remembered")
        val state = feedState(
            category = NewsCategory.SPORTS,
            articles = remembered,
            index = 5,
            revision = 11,
        )
        val moved = listOf(article(NewsCategory.SPORTS, "new-at-top")) + remembered

        val refreshed = state.withLoadedFeed(
            articles = moved,
            nextPageFile = "background-page-2.json",
            preserveReaderPosition = true,
        )

        assertEquals(6, refreshed.currentArticleIndex)
        assertEquals(
            remembered[5].articleUrl,
            refreshed.articles[refreshed.currentArticleIndex].articleUrl,
        )
        assertEquals(11, refreshed.feedRevision)
    }

    @Test
    fun `a restored category refresh clamps the index when its current article is gone`() {
        val state = feedState(
            category = NewsCategory.SPORTS,
            articles = articles(NewsCategory.SPORTS, "remembered"),
            index = 5,
            revision = 11,
        )

        val refreshed = state.withLoadedFeed(
            articles = articles(NewsCategory.SPORTS, "replacement").take(3),
            nextPageFile = null,
            preserveReaderPosition = true,
        )

        assertEquals(2, refreshed.currentArticleIndex)
        assertTrue(refreshed.currentArticleIndex in refreshed.articles.indices)
        assertEquals(11, refreshed.feedRevision)
    }

    @Test
    fun `changing language clears remembered category feeds`() {
        val memory = CategoryFeedMemory()
        val sports = feedState(
            category = NewsCategory.SPORTS,
            articles = articles(NewsCategory.SPORTS, "sports-en"),
            index = 5,
            revision = 2,
        )
        memory.rememberAndFind(sports, NewsCategory.TECHNOLOGY)

        memory.clear()

        val technology = feedState(
            category = NewsCategory.TECHNOLOGY,
            articles = articles(NewsCategory.TECHNOLOGY, "technology-ar"),
            index = 3,
            revision = 3,
            language = LanguageOption.ARABIC,
        )
        val rememberedSports = memory.rememberAndFind(technology, NewsCategory.SPORTS)
        val selected = technology.withSelectedCategory(NewsCategory.SPORTS, rememberedSports)

        assertNull(rememberedSports)
        assertEquals(0, selected.currentArticleIndex)
    }

    @Test
    fun `the oldest category is forgotten when the memory is full`() {
        val memory = CategoryFeedMemory(maxEntries = 2)
        val sports = feedState(
            category = NewsCategory.SPORTS,
            articles = articles(NewsCategory.SPORTS, "sports"),
            index = 5,
            revision = 1,
        )
        val technology = feedState(
            category = NewsCategory.TECHNOLOGY,
            articles = articles(NewsCategory.TECHNOLOGY, "technology"),
            index = 4,
            revision = 2,
        )
        val business = feedState(
            category = NewsCategory.BUSINESS,
            articles = articles(NewsCategory.BUSINESS, "business"),
            index = 3,
            revision = 3,
        )
        memory.rememberAndFind(sports, NewsCategory.TECHNOLOGY)
        memory.rememberAndFind(technology, NewsCategory.BUSINESS)
        memory.rememberAndFind(business, NewsCategory.HEALTH)

        val health = feedState(
            category = NewsCategory.HEALTH,
            articles = articles(NewsCategory.HEALTH, "health"),
            index = 2,
            revision = 4,
        )

        assertNull(memory.rememberAndFind(health, NewsCategory.SPORTS))
    }

    private fun feedState(
        category: NewsCategory,
        articles: List<NewsArticle>,
        index: Int,
        revision: Int,
        nextPageFile: String? = null,
        language: LanguageOption = LanguageOption.ENGLISH,
    ) = NewsUiState(
        isLoading = false,
        articles = articles,
        selectedCategory = category,
        currentArticleIndex = index,
        selectedLanguage = language,
        feedRevision = revision,
        nextPageFile = nextPageFile,
    )

    private fun articles(category: NewsCategory, prefix: String): List<NewsArticle> =
        (0 until 10).map { index -> article(category, "$prefix-$index") }

    private fun article(category: NewsCategory, id: String) = NewsArticle(
        id = ArticleId(id),
        title = ArticleTitle("Headline $id"),
        description = ArticleDescription(""),
        content = ArticleContent(""),
        author = ArticleAuthor(""),
        source = NewsSource(SourceId("source"), SourceName("Source")),
        imageUrl = null,
        articleUrl = ArticleUrl("https://example.com/$id"),
        publishedAt = PublishedTimestamp(0L),
        category = category,
    )
}
