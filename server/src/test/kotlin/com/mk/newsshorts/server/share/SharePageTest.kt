package com.mk.newsshorts.server.share

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharePageTest {

    private fun article(
        title: String = "Cairo metro line opens",
        summary: String = "The third line opened this morning after two years of work.",
        url: String = "https://example.com/story",
        imageUrl: String? = "https://example.com/a.jpg",
        language: String = "en",
        category: String = "general",
    ) = SharedArticle(
        slug = ShareSlug.of(url),
        language = language,
        title = title,
        summary = summary,
        url = url,
        imageUrl = imageUrl,
        sourceName = "Example News",
        category = category,
        publishedAt = 1_700_000_000_000L,
    )

    private fun render(article: SharedArticle, storeUrl: String = "") =
        SharePage.render(article, siteBaseUrl = SITE, storeUrl = storeUrl)

    /**
     * The whole reason the page is rendered here rather than in the browser:
     * every crawler that draws a preview card reads these and runs no script.
     */
    @Test
    fun `bakes the open graph tags into the markup`() {
        val html = render(article())

        assertContains(html, """<meta property="og:title" content="Cairo metro line opens">""")
        assertContains(html, """<meta property="og:type" content="article">""")
        assertContains(html, """<meta property="og:image" content="https://example.com/a.jpg">""")
        assertContains(html, """<meta name="twitter:card" content="summary_large_image">""")
        assertContains(html, "og:description")
        assertContains(html, "The third line opened this morning")
    }

    @Test
    fun `points og url and canonical at this page`() {
        val subject = article()
        val expected = "$SITE/a/en/${subject.slug}/"

        val html = render(subject)

        assertEquals(expected, SharePage.urlFor(subject, SITE))
        assertContains(html, """<meta property="og:url" content="$expected">""")
        assertContains(html, """<link rel="canonical" href="$expected">""")
    }

    /** Without an image X shows a small card, and a large-image tag is a lie. */
    @Test
    fun `falls back to a small twitter card when there is no image`() {
        val html = render(article(imageUrl = null))

        assertContains(html, """<meta name="twitter:card" content="summary">""")
        assertFalse(html.contains("og:image"), "og:image was emitted without an image")
    }

    /**
     * Titles and summaries come from RSS feeds and a language model. A page that
     * pasted either straight into markup would let any publisher this app
     * aggregates run script on a domain the app's own links point at.
     */
    @Test
    fun `escapes markup in untrusted article text`() {
        val html = render(
            article(
                title = """Breaking: "<script>alert(1)</script>" & more""",
                summary = "<img src=x onerror=alert(1)>",
            )
        )

        assertFalse(html.contains("<script>alert(1)"), "a script tag survived escaping")
        assertFalse(html.contains("<img src=x"), "an img tag survived escaping")
        assertContains(html, "&lt;script&gt;alert(1)&lt;/script&gt;")
        assertContains(html, "&amp; more")
    }

    /** The same allowlist the app applies before handing a URL to a browser. */
    @Test
    fun `drops urls that are not http`() {
        val html = render(
            article(url = "javascript:alert(1)", imageUrl = "data:text/html,<script>")
        )

        assertFalse(html.contains("javascript:"), "a javascript: URL reached an href")
        assertFalse(html.contains("data:text/html"), "a data: URL reached a src")
    }

    @Test
    fun `writes an arabic page right to left`() {
        val html = render(article(language = "ar", title = "افتتاح الخط الثالث"))

        assertContains(html, """<html lang="ar" dir="rtl">""")
        assertContains(html, """<meta property="og:locale" content="ar_AR">""")
        assertContains(html, "اقرأ من المصدر")
        assertContains(html, "افتتاح الخط الثالث")
    }

    @Test
    fun `writes an english page left to right`() {
        val html = render(article(language = "en"))

        assertContains(html, """<html lang="en" dir="ltr">""")
        assertContains(html, "Read at source")
    }

    /** A card cuts the description off anyway; better to cut it at a word. */
    @Test
    fun `truncates a long description without breaking a word`() {
        val summary = List(60) { "word" }.joinToString(" ")

        val html = render(article(summary = summary))

        val description = Regex("""<meta name="description" content="([^"]*)">""")
            .find(html)!!.groupValues[1]
        assertTrue(description.length <= 210, "description is ${description.length} characters")
        assertTrue(description.endsWith("…"), description)
        assertFalse(description.contains("wor…"), "cut mid-word: $description")
        // The body still carries the whole summary — only the card is trimmed.
        assertContains(html, summary)
    }

    /**
     * The page no longer has a query string to read the article back out of, so
     * the hand-off link has to be built into the button.
     */
    @Test
    fun `carries a hand-off link marked as a share`() {
        val html = render(article())

        val href = Regex("""id="open" hidden href="([^"]*)"""").find(html)!!.groupValues[1]
        assertTrue(href.startsWith("newsshorts://article?"), href)
        assertContains(href, "src=share")
        assertContains(href, "url=https%3A%2F%2Fexample.com%2Fstory")
    }

    @Test
    fun `hides the store button until there is a store`() {
        assertFalse(render(article()).contains("Get the app"))
        assertContains(render(article(), storeUrl = STORE), STORE)
    }

    @Test
    fun `files a page under its language and slug`() {
        val subject = article(language = "ar")

        assertEquals("a/ar/${subject.slug}/index.html", SharePage.pathFor(subject))
    }

    /** The page a link older than the archive lands on. */
    @Test
    fun `offers the app on the not found page`() {
        val html = SharePage.notFound(siteBaseUrl = SITE, storeUrl = STORE)

        assertContains(html, "no longer available")
        assertContains(html, "لم نعد نحتفظ بهذا الخبر")
        assertContains(html, STORE)
        assertContains(html, """<meta name="robots" content="noindex">""")
    }

    private companion object {
        const val SITE = "https://example.github.io/NewsShortsCMP"
        const val STORE = "https://play.google.com/store/apps/details?id=com.mk.newsshorts"
    }
}
