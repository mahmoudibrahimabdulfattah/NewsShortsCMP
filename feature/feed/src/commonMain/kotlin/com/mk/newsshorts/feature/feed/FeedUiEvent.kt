package com.mk.newsshorts.feature.feed

import com.mk.newsshorts.core.model.feed.CountryOption
import com.mk.newsshorts.core.model.feed.LanguageOption
import com.mk.newsshorts.core.model.NewsCategory

sealed interface FeedUiEvent {
    data class SelectCategory(val category: NewsCategory) : FeedUiEvent
    data class SelectCountry(val country: CountryOption) : FeedUiEvent
    data class SelectLanguage(val language: LanguageOption) : FeedUiEvent

    /** A pager position, so genuinely an index — unlike the events below. */
    data class ScrollToArticle(val index: Int) : FeedUiEvent

    data object RefreshNews : FeedUiEvent
    data object RetryLoading : FeedUiEvent

    /**
     * Try the next page again after it failed. The feed keeps whatever is
     * already loaded — this only resumes it from where it stopped.
     */
    data object RetryNextPage : FeedUiEvent
    data object DismissError : FeedUiEvent

    /** Fired once, after the reader has read enough to make an informed choice. */
    data object RequestNotificationPermissionIfDue : FeedUiEvent
}
