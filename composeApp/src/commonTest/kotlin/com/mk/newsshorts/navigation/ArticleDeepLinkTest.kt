package com.mk.newsshorts.navigation

import com.mk.newsshorts.domain.model.ArticleDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The activity receiving these links is exported, so this parser is reachable by
 * any installed app. These cases are the security boundary, not a formality.
 *
 * The literal below is duplicated in the server's builder test on purpose — the
 * two modules cannot share code, so editing one payload format must fail the
 * other. Keep them in sync.
 */
class ArticleDeepLinkTest {

    private val validLink =
        "newsshorts://article?url=https%3A%2F%2Fwww.reuters.com%2Fworld%2Fexample" +
            "&title=Test%20Headline&summary=A%20short%20summary." +
            "&image=https%3A%2F%2Fexample.com%2Fa.jpg&source=Reuters" +
            "&category=technology&published=1754870000000"

    @Test
    fun `parses every field`() {
        val link = ArticleDeepLinks.parse(validLink)!!
        assertEquals("https://www.reuters.com/world/example", link.url)
        assertEquals("Test Headline", link.title)
        assertEquals("A short summary.", link.summary)
        assertEquals("https://example.com/a.jpg", link.imageUrl)
        assertEquals("Reuters", link.sourceName)
        assertEquals("technology", link.category)
        assertEquals(1754870000000L, link.publishedAtMillis)
    }

    @Test
    fun `rejects non-web article urls`() {
        listOf("javascript%3Aalert(1)", "file%3A%2F%2F%2Fetc%2Fpasswd", "intent%3A%23Intent%3B", "content%3A%2F%2Fx")
            .forEach { hostile ->
                assertNull(
                    ArticleDeepLinks.parse("newsshorts://article?url=$hostile&title=x"),
                    "$hostile was accepted as an article url",
                )
            }
    }

    @Test
    fun `drops a hostile image but keeps the article`() {
        val link = ArticleDeepLinks.parse(
            "newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa&title=x&image=javascript%3Aalert(1)"
        )!!
        assertNull(link.imageUrl)
    }

    @Test
    fun `rejects links that are not ours`() {
        assertNull(ArticleDeepLinks.parse("https://example.com/article?url=https%3A%2F%2Fexample.com"))
        assertNull(ArticleDeepLinks.parse("newsshorts://other?url=https%3A%2F%2Fexample.com"))
        assertNull(ArticleDeepLinks.parse("newsshorts://article"))
        assertNull(ArticleDeepLinks.parse(""))
        assertNull(ArticleDeepLinks.parse(null))
        assertNull(ArticleDeepLinks.parse("not a url at all"))
    }

    @Test
    fun `caps oversized fields`() {
        val huge = "x".repeat(50_000)
        val link = ArticleDeepLinks.parse(
            "newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa&title=$huge&summary=$huge"
        )!!
        assertTrue(link.title!!.length <= 300)
        assertTrue(link.summary!!.length <= 4000)
    }

    @Test
    fun `builds an article a details screen can render`() {
        val article = ArticleDeepLinks.parse(validLink)!!.toNewsArticle()!!
        assertEquals("Test Headline", article.title.value)
        assertEquals("A short summary.", article.description.value)
        assertEquals("Reuters", article.source.name.value)
        assertEquals("technology", article.category.apiValue)
        assertEquals("https://example.com/a.jpg", article.imageUrl?.value)
    }

    @Test
    fun `refuses to build an article with no headline`() {
        val link = ArticleDeepLinks.parse("newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa")!!
        assertNull(link.toNewsArticle())
    }

    @Test
    fun `a share link stays short enough for a chat bubble`() {
        // Arabic is the worst case: percent-encoding costs ~9 bytes a character,
        // and chat clients truncate long URLs mid-string.
        val article = ArticleDeepLinks.parse(validLink)!!.toNewsArticle()!!.copy(
            description = ArticleDescription("ملخص طويل جدا ".repeat(300)),
        )
        val shared = ArticleDeepLinks.shareUrl(article, "https://example.com/site", "ar")

        assertTrue(shared.length <= 1200, "share link was ${shared.length} characters")
        assertTrue(shared.startsWith("https://example.com/site/a/?"))
        assertTrue(shared.contains("lang=ar"))
    }

    @Test
    fun `a share link keeps a short summary intact`() {
        val article = ArticleDeepLinks.parse(validLink)!!.toNewsArticle()!!
        val shared = ArticleDeepLinks.shareUrl(article, "https://example.com/site/", "en")

        // Trailing slash on the base must not double up.
        assertTrue(shared.startsWith("https://example.com/site/a/?"))
        assertTrue(shared.contains("lang=en"))
        // Round-trips through the parser the landing page hands back to the app.
        val roundTripped = ArticleDeepLinks.parse(
            "newsshorts://article?" + shared.substringAfter("/a/?")
        )!!
        assertEquals(article.articleUrl.value, roundTripped.url)
        assertEquals(article.title.value, roundTripped.title)
        assertEquals(article.description.value, roundTripped.summary)
    }

    @Test
    fun `a link marked by the landing page is recognised as a share`() {
        val link = ArticleDeepLinks.parse(
            "newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa&title=x&src=share"
        )!!
        assertEquals(ArticleDeepLinks.SHARE_REFERRER, link.referrer)
        // A push carries no marker, which is how the two are told apart.
        assertNull(ArticleDeepLinks.parse(validLink)!!.referrer)
    }

    @Test
    fun `accepts the https share link the app itself produces`() {
        // This is the App Links path: Android hands the https URL to the app,
        // so the parser must read it exactly as it reads the custom scheme.
        val article = ArticleDeepLinks.parse(validLink)!!.toNewsArticle()!!
        val shared = ArticleDeepLinks.shareUrl(
            article, "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP", "ar"
        )

        val parsed = ArticleDeepLinks.parse(shared)!!
        assertEquals(article.articleUrl.value, parsed.url)
        assertEquals(article.title.value, parsed.title)
    }

    @Test
    fun `ignores https urls that are not the landing page`() {
        assertNull(
            ArticleDeepLinks.parse(
                "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP/v1/feed/ar.json?url=https%3A%2F%2Fx.com"
            )
        )
        assertNull(ArticleDeepLinks.parse("https://evil.example.com/b/?url=https%3A%2F%2Fx.com&title=x"))
    }

    @Test
    fun `a missing timestamp stays zero rather than becoming 1970`() {
        val article = ArticleDeepLinks
            .parse("newsshorts://article?url=https%3A%2F%2Fexample.com%2Fa&title=x")!!
            .toNewsArticle()!!
        assertEquals(0L, article.publishedAt.epochMillis)
    }
}
