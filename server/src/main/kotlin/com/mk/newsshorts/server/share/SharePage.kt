package com.mk.newsshorts.server.share

import com.mk.newsshorts.server.push.ArticleDeepLinks
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One article, in one language, as its landing page needs it. */
data class SharedArticle(
    val slug: String,
    val language: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val sourceName: String,
    val category: String,
    val publishedAt: Long,
)

/**
 * Renders the page a shared link lands on.
 *
 * The point of rendering it here rather than in the browser is the preview card.
 * WhatsApp, Telegram, Facebook and X fetch a shared URL and read its `<head>`;
 * none of them run JavaScript. The old page held the whole article in its query
 * string and drew it on load, so every one of those crawlers saw an empty
 * document and every shared story arrived as a bare grey link.
 *
 * So the tags have to be in the markup as served, which means one file per
 * article per language — see [pathFor].
 */
object SharePage {

    /** Where a page for this article lives under the published site root. */
    fun pathFor(article: SharedArticle): String =
        "a/${article.language}/${article.slug}/index.html"

    /** The absolute link the app shares, and the page's own canonical URL. */
    fun urlFor(article: SharedArticle, siteBaseUrl: String): String =
        "${siteBaseUrl.trimEnd('/')}/a/${article.language}/${article.slug}/"

    /**
     * Long enough for a preview card's two or three lines, short enough that
     * nothing important lands past where the card cuts it off.
     */
    private const val DESCRIPTION_CHARS = 200

    fun render(article: SharedArticle, siteBaseUrl: String, storeUrl: String): String {
        val copy = Copy.of(article.language)
        val canonical = urlFor(article, siteBaseUrl)
        val description = article.summary.trim().truncateOnWord(DESCRIPTION_CHARS)
        val source = article.url.takeIf { it.isWebUrl() }
        val published = Instant.ofEpochMilli(article.publishedAt)

        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"${copy.htmlLang}\" dir=\"${copy.direction}\">\n<head>\n")
            append("<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
            append("<title>").append(article.title.escapeHtml()).append("</title>\n")
            meta(name = "description", content = description)
            // These pages exist to unfurl a link somebody chose to send, not to
            // rank against the publisher whose story they summarise. Crawlers
            // that build preview cards fetch the URL directly and ignore this,
            // so it costs the feature nothing.
            meta(name = "robots", content = "noindex, follow")
            append("<link rel=\"canonical\" href=\"").append(canonical.escapeHtml()).append("\">\n")
            append("<link rel=\"stylesheet\" href=\"")
                .append(stylesheetUrl(siteBaseUrl).escapeHtml()).append("\">\n")

            property("og:type", "article")
            property("og:site_name", copy.siteName)
            property("og:locale", copy.openGraphLocale)
            property("og:title", article.title)
            property("og:description", description)
            property("og:url", canonical)
            property("article:published_time", ISO_INSTANT_SECONDS.format(published))
            property("article:section", article.category)

            // X reads og:title and og:description when their twitter equivalents
            // are absent. Publisher images are deliberately excluded, so every
            // page requests the plain text-first summary card.
            meta(name = "twitter:card", content = "summary")

            append("</head>\n<body>\n<main class=\"card\">\n")
            append("<div class=\"brand\"><span class=\"dot\"></span><span>")
                .append(copy.siteName.escapeHtml()).append("</span></div>\n")
            if (article.category.isNotBlank()) {
                append("<span class=\"badge\">").append(article.category.escapeHtml()).append("</span>\n")
            }
            append("<h1>").append(article.title.escapeHtml()).append("</h1>\n")
            append("<div class=\"meta\">")
                .append(metaLine(article, published, copy).escapeHtml())
                .append("</div>\n")
            if (article.summary.isNotBlank()) {
                append("<p class=\"summary\">").append(article.summary.trim().escapeHtml()).append("</p>\n")
                append("<p class=\"note\">").append(copy.note.escapeHtml()).append("</p>\n")
            }

            // Hidden until the script below confirms Android. The scheme is
            // registered there and nowhere else, so on any other platform this
            // link produces a browser error dialog rather than an app.
            if (source != null) {
                append("<a class=\"button primary\" id=\"open\" hidden href=\"")
                    .append(handoffLink(article).escapeHtml()).append("\">")
                    .append(copy.open.escapeHtml()).append("</a>\n")
            }
            if (storeUrl.isWebUrl()) {
                append("<a class=\"button secondary\" href=\"").append(storeUrl.escapeHtml())
                    .append("\">").append(copy.store.escapeHtml()).append("</a>\n")
            }
            if (source != null) {
                append("<a class=\"button secondary\" rel=\"noopener noreferrer\" href=\"")
                    .append(source.escapeHtml()).append("\">")
                    .append(copy.source.escapeHtml()).append("</a>\n")
            }
            append("</main>\n")
            append("<script src=\"").append(scriptUrl(siteBaseUrl).escapeHtml()).append("\"></script>\n")
            append("</body>\n</html>\n")
        }
    }

    /**
     * What GitHub Pages serves for any path it does not have.
     *
     * Which, for this site, mostly means a shared link older than the archive
     * keeps — see [com.mk.newsshorts.server.store.ArticleStore.pruneShared]. The
     * default page says the repository has no such file, in English, over a
     * GitHub logo; this one says the story has expired and offers the app, which
     * is the only useful thing left to offer at that point.
     *
     * Both languages at once: the URL that got here carries no reliable hint of
     * which one the reader speaks, and guessing wrong is worse than showing two
     * short lines.
     */
    fun notFound(siteBaseUrl: String, storeUrl: String): String = buildString {
        append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
        append("<title>News Shorts</title>\n")
        meta(name = "robots", content = "noindex")
        append("<link rel=\"stylesheet\" href=\"")
            .append(stylesheetUrl(siteBaseUrl).escapeHtml()).append("\">\n")
        append("</head>\n<body>\n<main class=\"card\">\n")
        append("<div class=\"brand\"><span class=\"dot\"></span><span>News Shorts</span></div>\n")
        append("<h1 dir=\"rtl\" lang=\"ar\">لم نعد نحتفظ بهذا الخبر</h1>\n")
        append("<h1>This story is no longer available</h1>\n")
        append("<p class=\"summary\" dir=\"rtl\" lang=\"ar\">")
            .append("الروابط المشتركة تبقى متاحة لفترة محدودة. اقرأ آخر الأخبار في التطبيق.")
            .append("</p>\n")
        append("<p class=\"summary\">Shared links are kept for a limited time. ")
            .append("Read the latest in the app.</p>\n")
        if (storeUrl.isWebUrl()) {
            append("<a class=\"button primary\" href=\"").append(storeUrl.escapeHtml())
                .append("\">Get the app · تحميل التطبيق</a>\n")
        }
        append("</main>\n</body>\n</html>\n")
    }

    /**
     * The one file every page links instead of carrying its own copy.
     *
     * At tens of thousands of pages a republished stylesheet is the difference
     * between a site of a few megabytes and one of tens, and it is the same
     * bytes in every copy — so it is a file, and the reader's browser fetches it
     * once for however many links they open.
     */
    fun stylesheet(): String = STYLESHEET

    /**
     * The `newsshorts://` link the Android button carries.
     *
     * Built here, with the article's fields in it, rather than read back off the
     * page's own query string: the URL no longer has one. `src=share` is what
     * lets the app report a share-driven open apart from a notification tap.
     */
    private fun handoffLink(article: SharedArticle): String =
        ArticleDeepLinks.link(
            url = article.url,
            title = article.title,
            summary = article.summary,
            sourceName = article.sourceName,
            category = article.category,
            publishedAt = article.publishedAt,
            referrer = "share",
        )

    private fun metaLine(article: SharedArticle, published: Instant, copy: Copy): String =
        listOfNotNull(
            article.sourceName.takeIf { it.isNotBlank() },
            published.takeIf { article.publishedAt > 0 }
                ?.atZone(ZoneOffset.UTC)?.let { copy.dateFormat.format(it) },
        ).joinToString(" • ")

    private fun StringBuilder.meta(name: String, content: String) {
        if (content.isBlank()) return
        append("<meta name=\"").append(name.escapeHtml())
            .append("\" content=\"").append(content.escapeHtml()).append("\">\n")
    }

    private fun StringBuilder.property(property: String, content: String) {
        if (content.isBlank()) return
        append("<meta property=\"").append(property.escapeHtml())
            .append("\" content=\"").append(content.escapeHtml()).append("\">\n")
    }

    /**
     * Every string on this page came out of an RSS feed or a language model, so
     * none of it is trusted. Both quote characters are escaped as well as the
     * angle brackets, because most of these values are written into attributes.
     */
    private fun String.escapeHtml(): String = buildString(length) {
        for (character in this@escapeHtml) when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(character)
        }
    }

    /** Keeps a javascript: or data: URL out of an href, exactly as the app does. */
    private fun String?.isWebUrl(): Boolean =
        this != null &&
            (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true))

    /** Cuts at the last space before the limit, so a card never ends mid-word. */
    private fun String.truncateOnWord(limit: Int): String {
        if (length <= limit) return this
        val cut = take(limit)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > limit / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }

    private val ISO_INSTANT_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    /** Where [stylesheet] is written, relative to the published site root. */
    const val STYLESHEET_PATH = "a/page.css"

    /** Where [script] is written, relative to the published site root. */
    const val SCRIPT_PATH = "a/page.js"

    /**
     * Absolute, not relative. The article pages sit three levels deep and the
     * 404 page is served for any path at all — including ones that never
     * existed, at any depth — so there is no relative prefix that is right for
     * both.
     */
    private fun stylesheetUrl(siteBaseUrl: String): String =
        "${siteBaseUrl.trimEnd('/')}/$STYLESHEET_PATH"

    private fun scriptUrl(siteBaseUrl: String): String =
        "${siteBaseUrl.trimEnd('/')}/$SCRIPT_PATH"

    /**
     * The copy and direction a page is written in.
     *
     * Chosen by the *article's* language and not the reader's: a story shared
     * out of the English feed should not land on a right-to-left Arabic page.
     */
    private data class Copy(
        val htmlLang: String,
        val direction: String,
        val openGraphLocale: String,
        val siteName: String,
        val open: String,
        val store: String,
        val source: String,
        val note: String,
        val dateFormat: DateTimeFormatter,
    ) {
        companion object {
            fun of(language: String): Copy =
                if (language.equals("en", ignoreCase = true)) ENGLISH else ARABIC

            private val ARABIC = Copy(
                htmlLang = "ar", direction = "rtl", openGraphLocale = "ar_AR",
                siteName = "News Shorts",
                open = "فتح في التطبيق",
                store = "تحميل التطبيق",
                source = "اقرأ من المصدر",
                note = "ملخص مُولَّد بالذكاء الاصطناعي. افتح المصدر لقراءة الخبر كاملاً.",
                // Latin digits, not Arabic-Indic: the rest of the app renders
                // dates the same way, and a card mixing the two reads as a bug.
                dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn")),
            )

            private val ENGLISH = Copy(
                htmlLang = "en", direction = "ltr", openGraphLocale = "en_US",
                siteName = "News Shorts",
                open = "Open in the app",
                store = "Get the app",
                source = "Read at source",
                note = "AI-generated summary. Open the source for the full story.",
                dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            )
        }
    }

    /**
     * The one script every page loads, for the same reason as [stylesheet]: it
     * is identical in all of them, and inlined it would be repeated as many
     * times as the archive is deep.
     *
     * All it does is reveal the hand-off button on Android. The `newsshorts://`
     * scheme is registered there and nowhere else, so the button would produce a
     * browser error dialog on any other platform — and Chrome blocks a scheme
     * navigation no user gesture asked for, so redirecting on load would do
     * nothing while making everyone else wait for it. The tap is both the
     * reliable trigger and the honest one.
     *
     * The link itself is in the button's href, written by the server, so a
     * reader with scripts disabled loses the shortcut and nothing else.
     */
    fun script(): String = SCRIPT

    private val SCRIPT = """
        if (/Android/i.test(navigator.userAgent)) {
          var open = document.getElementById("open");
          if (open) open.hidden = false;
        }

    """.trimIndent()

    private val STYLESHEET = """
        :root { color-scheme: dark; }
        * { box-sizing: border-box; }
        body {
          margin: 0; padding: 24px 20px 40px;
          background: #0F1B2A; color: #F2F5F8;
          font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
          line-height: 1.6; display: flex; justify-content: center;
        }
        .card { width: 100%; max-width: 560px; }
        .brand { display: flex; align-items: center; gap: 8px; font-weight: 700; margin-bottom: 24px; }
        .dot { width: 10px; height: 10px; border-radius: 50%; background: #4ECDC4; }
        .badge {
          display: inline-block; background: #4ECDC4; color: #06222B;
          font-size: 13px; font-weight: 700; padding: 6px 14px; border-radius: 999px;
        }
        h1 { font-size: 26px; line-height: 1.35; margin: 16px 0 12px; }
        .meta { font-size: 14px; opacity: .7; margin-bottom: 18px; }
        p.summary { font-size: 17px; opacity: .9; }
        p.note { font-size: 13px; opacity: .5; margin: 18px 0 28px; }
        a.button {
          display: block; text-align: center; text-decoration: none;
          padding: 15px 20px; border-radius: 14px; font-weight: 700; margin-bottom: 12px;
        }
        a.primary { background: #4ECDC4; color: #06222B; }
        a.secondary { background: rgba(255,255,255,.08); color: #F2F5F8; }
        /* The rule above sets an explicit display, which would otherwise beat
           the hidden attribute's UA style and show a button on a platform that
           cannot follow it. */
        [hidden] { display: none !important; }

    """.trimIndent()
}
