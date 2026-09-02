package com.mk.newsshorts.core.data.remote

import com.mk.newsshorts.core.model.deeplink.ArticleDeepLink
import com.mk.newsshorts.core.model.deeplink.ArticleDeepLinks
import com.mk.newsshorts.core.contract.deeplink.ArticleDeepLinks as ContractArticleDeepLinks
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Gets an article back out of a shared link.
 *
 * A shared link names a published page rather than carrying the article, which
 * is what lets a chat app draw a preview card for it — but it also means the app
 * receives nothing it can open. The page itself already holds the answer: its
 * "open in the app" button is a `newsshorts://article` link with every field in
 * it, written by the server. So this fetches the page and reads that link back,
 * rather than adding a second published file per article or a lookup endpoint a
 * static host cannot serve.
 *
 * Every failure returns null and the caller opens the page instead, which is
 * what a reader without the app sees anyway — offline, a link older than the
 * archive, a site mid-deploy.
 */
class SharePageResolver(
    private val httpClient: HttpClient,
) {

    suspend fun resolve(pageUrl: String): ArticleDeepLink? =
        runCatching { ArticleDeepLinks.parse(handoffLinkIn(httpClient.get(pageUrl).bodyAsText())) }
            .getOrNull()

    internal companion object {

        /**
         * The link is inside an `href`, so it ends at the closing quote and its
         * ampersands arrive HTML-escaped.
         *
         * A plain scan rather than a parser: this reads one attribute out of a
         * document this project generates, and everything it finds still goes
         * through [ArticleDeepLinks.parse], which is the actual boundary — a
         * page serving something hostile gets no further than a parser that
         * already rejects non-http URLs.
         */
        fun handoffLinkIn(html: String): String? {
            val start = html.indexOf(HANDOFF_PREFIX)
            if (start < 0) return null
            val end = html.indexOf('"', start)
            if (end < 0 || end - start > MAX_LINK_CHARS) return null
            return html.substring(start, end).replace("&amp;", "&")
        }

        private val HANDOFF_PREFIX = ContractArticleDeepLinks.HANDOFF_PREFIX

        /** Matches the server's own cap on the link it writes into the page. */
        private const val MAX_LINK_CHARS = 4000
    }
}
