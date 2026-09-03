package com.mk.newsshorts.core.model.search

import com.mk.newsshorts.core.model.NewsArticle

/**
 * A language's published corpus, folded once and then searched many times.
 *
 * The backend is static JSON on a CDN and has no query API, so matching happens
 * here. Folding every article on every keystroke would be the obvious way to do
 * that and the wrong one — [normalizeForSearch] runs over roughly a quarter of
 * a million characters for a full corpus, which is fine once and not fine
 * twenty times while someone types a word. So the folded text is computed when
 * the corpus is built and the query is all that gets folded per search.
 */
class SearchIndex private constructor(private val docs: List<SearchDoc>) {

    val size: Int get() = docs.size

    /**
     * The best [limit] matches for [query], most relevant first.
     *
     * Every token has to match somewhere, which is what makes adding a word
     * narrow the results rather than widen them. Matching is by substring
     * rather than whole word, because Arabic attaches its clitics — و، ب، ل،
     * ال and the pronoun suffixes all glue onto the word — so a whole-word
     * rule would miss وغزة for غزة.
     */
    fun search(query: String, limit: Int = MAX_RESULTS): List<NewsArticle> {
        val tokens = searchTokens(query)
        if (tokens.isEmpty()) return emptyList()
        return docs
            .mapNotNull { doc -> doc.scoreFor(tokens)?.let { score -> doc to score } }
            .sortedWith(
                compareByDescending<Pair<SearchDoc, Int>> { it.second }
                    // Two stories that match equally well are a news feed
                    // question, not a search one: the newer one wins.
                    .thenByDescending { it.first.article.publishedAt.epochMillis }
            )
            .take(limit)
            .map { it.first.article }
    }

    companion object {
        /**
         * Enough that no real query runs out of results, few enough that the
         * list stays a list rather than a second feed.
         */
        const val MAX_RESULTS: Int = 100

        val EMPTY: SearchIndex = SearchIndex(emptyList())

        fun from(articles: List<NewsArticle>): SearchIndex =
            SearchIndex(articles.map { SearchDoc.of(it) })
    }
}

/**
 * One article, with its title and the rest of its text folded separately —
 * separately because a hit in the headline is worth more than a hit in the
 * summary, and that difference is the whole of the ranking.
 */
internal class SearchDoc private constructor(
    val article: NewsArticle,
    private val title: String,
    private val body: String,
) {
    /** Null when any token matches nothing: every token has to appear somewhere. */
    fun scoreFor(tokens: List<String>): Int? {
        var score = 0
        for (token in tokens) {
            val inTitle = title.contains(token)
            if (!inTitle && !body.contains(token)) return null
            score += if (inTitle) TITLE_WEIGHT else BODY_WEIGHT
        }
        return score
    }

    companion object {
        private const val TITLE_WEIGHT: Int = 4
        private const val BODY_WEIGHT: Int = 1

        /**
         * The publisher's name joins the body text so "BBC" or "الشرق الأوسط"
         * works as a query — readers look for an outlet as readily as a topic,
         * and there is nowhere else in this UI to ask for one.
         */
        fun of(article: NewsArticle): SearchDoc = SearchDoc(
            article = article,
            title = normalizeForSearch(article.title.value),
            body = normalizeForSearch(
                "${article.description.value} ${article.source.name.value}"
            ),
        )
    }
}
