package com.mk.newsshorts.core.contract.deeplink

/**
 * Ktor-free constants and URL formatting for article deep links and share pages.
 */
object ArticleDeepLinks {
    const val SCHEME: String = "newsshorts"
    const val HOST: String = "article"

    /** Value of `src` that the landing page adds when it hands off to the app. */
    const val SHARE_REFERRER: String = "share"

    /** Last path segment of a shared link, and of the published landing page. */
    const val LANDING_PATH: String = "a"

    const val HANDOFF_PREFIX: String = "newsshorts://article?"

    /**
     * Recognises a per-article landing page, and returns its URL unchanged.
     *
     * These carry no query string — they name a story rather than describing
     * one — so parsing cannot build an article and something has to fetch the
     * page to get the article back. This only decides whether that is worth
     * doing.
     *
     * [baseUrl] is checked and not merely assumed. The activity receiving these
     * is exported, so any installed app can hand it a URL, and whatever
     * recovers an article from this one will go and fetch it: without the
     * check, another app could aim that fetch at a host of its choosing.
     */
    fun sharePageUrl(raw: String?, baseUrl: String): String? {
        val url = RawHttpsUrl.parse(raw) ?: return null
        val base = RawHttpsUrl.parse(baseUrl) ?: return null
        if (!url.host.equals(base.host, ignoreCase = true)) return null

        val prefix = base.path.trimEnd('/') + "/" + LANDING_PATH + "/"
        if (!url.path.startsWith(prefix)) return null
        // Exactly a language and a slug after it. Anything else under the same
        // prefix is the stylesheet, the script, or the legacy landing page.
        val rest = url.path.removePrefix(prefix).trim('/').split('/')
        if (rest.size != 2 || rest.any { it.isEmpty() }) return null
        return raw
    }

    /**
     * The link put into a share sheet.
     *
     * Names a page on the published site rather than carrying the article in a
     * query string. The language belongs in the path because the same article
     * is a different page in each one.
     */
    fun shareUrl(articleUrl: String, baseUrl: String, language: String): String {
        val slug = ShareSlug.of(articleUrl)
        return "${baseUrl.trimEnd('/')}/$LANDING_PATH/${language.lowercase()}/$slug/"
    }
}

private data class RawHttpsUrl(
    val host: String,
    val path: String,
) {
    companion object {
        fun parse(raw: String?): RawHttpsUrl? {
            if (raw.isNullOrBlank()) return null
            val withoutScheme = raw.substringAfter("https://", missingDelimiterValue = "")
            if (withoutScheme.isEmpty() || withoutScheme == raw) return null
            val host = withoutScheme.substringBefore('/').substringBefore(':')
            if (host.isBlank()) return null
            val pathAndQuery = withoutScheme.substringAfter('/', missingDelimiterValue = "")
            val path = "/" + pathAndQuery.substringBefore('?').substringBefore('#').trim('/')
            return RawHttpsUrl(host = host, path = path)
        }
    }
}
