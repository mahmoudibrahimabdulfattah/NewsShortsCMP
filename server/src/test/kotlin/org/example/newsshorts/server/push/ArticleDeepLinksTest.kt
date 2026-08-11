package org.example.newsshorts.server.push

import org.example.newsshorts.server.model.FeedArticleDto
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The client parses what this builds, and the two modules cannot share code, so
 * the literal below is duplicated in the app's ArticleDeepLinkTest on purpose.
 * Changing the format here must fail there too. Keep them in sync.
 */
class ArticleDeepLinksTest {

    private fun article(
        title: String = "Test Headline",
        summary: String = "A short summary.",
        url: String = "https://www.reuters.com/world/example",
        imageUrl: String? = "https://example.com/a.jpg",
    ) = FeedArticleDto(
        id = 1,
        title = title,
        summary = summary,
        url = url,
        imageUrl = imageUrl,
        sourceName = "Reuters",
        language = "en",
        category = "technology",
        publishedAt = 1754870000000L,
    )

    @Test
    fun `builds the shape the client parses`() {
        assertEquals(
            "newsshorts://article?url=https%3A%2F%2Fwww.reuters.com%2Fworld%2Fexample" +
                "&title=Test%20Headline&summary=A%20short%20summary." +
                "&image=https%3A%2F%2Fexample.com%2Fa.jpg&source=Reuters" +
                "&category=technology&published=1754870000000",
            ArticleDeepLinks.build(article()),
        )
    }

    @Test
    fun `encodes spaces as percent-20 rather than plus`() {
        val link = ArticleDeepLinks.build(article(title = "Two Words"))
        assertContains(link, "title=Two%20Words")
        assertTrue("+" !in link, "a literal + would decode as a plus, not a space")
    }

    @Test
    fun `keeps an Arabic summary inside the FCM budget`() {
        // Arabic costs ~9 bytes per character once percent-encoded, so this is
        // the case that would silently break push for every Arabic reader.
        val huge = "خبر عاجل عن تطورات مهمة في المنطقة ".repeat(200)
        val link = ArticleDeepLinks.build(article(summary = huge))
        assertTrue(
            link.length <= ArticleDeepLinks.MAX_LINK_CHARS,
            "link was ${link.length} chars, over the ${ArticleDeepLinks.MAX_LINK_CHARS} cap",
        )
        assertTrue(link.toByteArray(Charsets.UTF_8).size < 4096)
        // Truncating must not cost the fields the screen needs.
        assertContains(link, "url=")
        assertContains(link, "title=")
    }

    @Test
    fun `omits an absent image`() {
        assertTrue("image=" !in ArticleDeepLinks.build(article(imageUrl = null)))
    }
}
