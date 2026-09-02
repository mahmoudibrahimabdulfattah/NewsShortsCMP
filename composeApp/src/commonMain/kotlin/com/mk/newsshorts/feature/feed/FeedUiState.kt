package com.mk.newsshorts.feature.feed

import com.mk.newsshorts.presentation.mvi.ArticleOpenOrigin
import com.mk.newsshorts.presentation.mvi.CountryOption
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.core.model.config.RequiredUpdate
import com.mk.newsshorts.core.model.security.SecurityNotice
import com.mk.newsshorts.core.model.security.SecurityReason
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.NewsCategory

data class FeedUiState(
    val isLoading: Boolean = true,
    val articles: List<NewsArticle> = emptyList(),
    val selectedCategory: NewsCategory = NewsCategory.GENERAL,
    val currentArticleIndex: Int = 0,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    val selectedCountry: CountryOption = CountryOption.UNITED_STATES,
    val selectedLanguage: LanguageOption = LanguageOption.ENGLISH,
    val currentTab: NavigationTab = NavigationTab.FOR_YOU,
    val isOfflineMode: Boolean = false,
    /**
     * The category row's order — the reader's picks first. Held in state rather
     * than read from settings at each call site, so the row and the feed can
     * never disagree about which category comes first.
     */
    val categoryOrder: List<NewsCategory> = NewsCategory.entries,
    /**
     * Bumped when [articles] starts a genuinely new reading session and the
     * reader's position must reset — an explicit refresh, a first visit to a
     * category, or a new country. Re-selecting a remembered category and
     * appending a page both preserve the existing session, so neither changes
     * it.
     *
     * The pager scrolls back to the top when this changes, which nothing else
     * in the state can tell it: after a refresh the list is a different list,
     * but its size and often its first card are the same, so the pager would
     * otherwise sit exactly where it was on a feed that had moved underneath it.
     */
    val feedRevision: Int = 0,
    /**
     * Bumped only when [articles] returns to a category remembered in this
     * session, with [currentArticleIndex] restored alongside it.
     *
     * The pager follows this revision to the remembered card. Category identity
     * cannot carry that signal because it also changes on a first visit, while
     * the previous feed may still be on screen.
     */
    val categoryRestoreRevision: Int = 0,
    /**
     * The file holding the page below the last one loaded, or null at the end
     * of the feed. Comes from the feed itself — each published page names the
     * one after it — so the app never has to work out where a page boundary
     * falls, which is the part that would drift between publishes.
     */
    val nextPageFile: String? = null,
    /** A page is in flight; keeps the prefetch from asking twice. */
    val isLoadingNextPage: Boolean = false,
    /**
     * The last page load failed. [nextPageFile] is deliberately kept, so the
     * feed can carry on from the same place once the reader reaches the end and
     * it is tried again.
     */
    val nextPageFailed: Boolean = false,
) {
    val hasArticles: Boolean
        get() = articles.isNotEmpty()

    val currentArticle: NewsArticle?
        get() = articles.getOrNull(currentArticleIndex)

    val isError: Boolean
        get() = errorMessage != null && !isLoading

    val hasMorePages: Boolean
        get() = nextPageFile != null

    /** The reader is on the last card and the feed genuinely stops there. */
    val isAtEndOfFeed: Boolean
        get() = hasArticles && !hasMorePages && currentArticleIndex >= articles.lastIndex

}
