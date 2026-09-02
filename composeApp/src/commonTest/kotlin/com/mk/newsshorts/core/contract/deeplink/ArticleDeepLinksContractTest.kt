package com.mk.newsshorts.core.contract.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleDeepLinksContractTest {

    @Test
    fun `a share link names a page rather than carrying the article`() {
        val shared = ArticleDeepLinks.shareUrl(
            articleUrl = "https://www.reuters.com/world/example",
            baseUrl = "https://example.com/site",
            language = "ar",
        )

        val slug = ShareSlug.of("https://www.reuters.com/world/example")
        assertEquals("https://example.com/site/a/ar/$slug/", shared)
    }

    @Test
    fun `a share link carries the article language instead of the reader language`() {
        // Trailing slash on the base must not double up.
        val english = ArticleDeepLinks.shareUrl(
            articleUrl = "https://www.reuters.com/world/example",
            baseUrl = "https://example.com/site/",
            language = "en",
        )
        val arabic = ArticleDeepLinks.shareUrl(
            articleUrl = "https://www.reuters.com/world/example",
            baseUrl = "https://example.com/site",
            language = "AR",
        )

        val slug = ShareSlug.of("https://www.reuters.com/world/example")
        assertEquals("https://example.com/site/a/en/$slug/", english)
        // Same story, different page: the two say different things.
        assertEquals("https://example.com/site/a/ar/$slug/", arabic)
    }

    @Test
    fun `recognises a per-article page it cannot parse`() {
        val shared = ArticleDeepLinks.shareUrl(
            articleUrl = "https://www.reuters.com/world/example",
            baseUrl = SITE,
            language = "ar",
        )

        assertEquals(shared, ArticleDeepLinks.sharePageUrl(shared, SITE))
    }

    @Test
    fun `refuses a page on another host`() {
        assertNull(ArticleDeepLinks.sharePageUrl("https://evil.example.com/NewsShortsCMP/a/ar/abc/", SITE))
        assertNull(ArticleDeepLinks.sharePageUrl("http://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP/a/ar/abc/", SITE))
    }

    /** Everything else under the same prefix is a file, or the legacy page. */
    @Test
    fun `refuses paths under a-slash that are not an article`() {
        listOf(
            "$SITE/a/",
            "$SITE/a/page.css",
            "$SITE/a/page.js",
            "$SITE/a/ar/",
            "$SITE/a/ar/abc/extra/",
            "$SITE/v1/feed/ar.json",
        ).forEach { assertNull(ArticleDeepLinks.sharePageUrl(it, SITE), it) }
    }

    private companion object {
        const val SITE = "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP"
    }
}
