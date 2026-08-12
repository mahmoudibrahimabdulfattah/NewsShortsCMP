package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.navigation.ArticleDeepLink
import com.mk.newsshorts.presentation.localization.AppLocale

sealed interface NewsUiEvent {
    data class SelectCategory(val category: NewsCategory) : NewsUiEvent
    data class SelectCountry(val country: CountryOption) : NewsUiEvent
    data class SelectLanguage(val language: LanguageOption) : NewsUiEvent
    data class SelectAppLocale(val locale: AppLocale) : NewsUiEvent
    data class SelectTab(val tab: NavigationTab) : NewsUiEvent

    /** A pager position, so genuinely an index — unlike the events below. */
    data class ScrollToArticle(val index: Int) : NewsUiEvent

    /**
     * Article-carrying rather than index-carrying: the feed and the saved list
     * are different lists, and an index into one used to be read against the
     * other.
     */
    data class OpenArticleDetails(
        val article: NewsArticle,
        val origin: ArticleOpenOrigin
    ) : NewsUiEvent
    data class ShareArticle(val article: NewsArticle) : NewsUiEvent
    data class SaveArticle(val article: NewsArticle) : NewsUiEvent
    data class RemoveSavedArticle(val article: NewsArticle) : NewsUiEvent

    data object CloseArticleDetails : NewsUiEvent

    /** Takes no argument so there is one source of truth for which URL opens. */
    data object OpenArticleSource : NewsUiEvent

    data class OpenDeepLink(val link: ArticleDeepLink) : NewsUiEvent

    data object RefreshNews : NewsUiEvent
    data object RetryLoading : NewsUiEvent
    data object DismissError : NewsUiEvent
    /** Acknowledges the device-integrity warning, which then stays dismissed. */
    data object DismissSecurityWarning : NewsUiEvent

    data object NavigateToSavedArticles : NewsUiEvent
    data object NavigateToLanguageSettings : NewsUiEvent
}
