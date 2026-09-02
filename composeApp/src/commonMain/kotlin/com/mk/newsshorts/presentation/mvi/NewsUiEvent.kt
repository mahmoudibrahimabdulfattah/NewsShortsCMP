package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.navigation.ArticleDeepLink

sealed interface NewsUiEvent {
    data class SelectCategory(val category: NewsCategory) : NewsUiEvent
    data class SelectCountry(val country: CountryOption) : NewsUiEvent
    data class SelectLanguage(val language: LanguageOption) : NewsUiEvent
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
    /** Opens the published privacy policy in a browser tab. */
    data object OpenPrivacyPolicy : NewsUiEvent

    data class OpenDeepLink(val link: ArticleDeepLink) : NewsUiEvent

    /**
     * A shared link that names a landing page rather than carrying the article.
     * Resolving it is a network round trip, so it arrives as its own event and
     * becomes an [OpenDeepLink] once the article is in hand.
     */
    data class OpenSharePage(val url: String) : NewsUiEvent

    /** Opens the inbox. Deliberately does not mark anything read. */
    data object OpenNotificationInbox : NewsUiEvent

    /**
     * A row in the inbox. It carries the link and nothing else: the same
     * handler serves a tap here and a tap on the notification in the tray, so
     * both clear the mark by the same route.
     */
    data class OpenInboxNotification(val deepLink: String) : NewsUiEvent

    /** The other one. Clears every mark currently in the list. */
    data object MarkAllNotificationsRead : NewsUiEvent

    /** Pull-to-refresh on the inbox. */
    data object RefreshNotificationInbox : NewsUiEvent

    /**
     * Swiped away. Local only — the list is published for every reader, so this
     * hides the row on this device and nothing more.
     */
    data class DismissInboxNotification(val articleUrl: String) : NewsUiEvent

    /** What the snackbar's undo does. */
    data class RestoreInboxNotification(val articleUrl: String) : NewsUiEvent

    /** Pushes a screen above the tabs. See [com.mk.newsshorts.presentation.mvi.Overlay]. */
    data class OpenOverlay(val overlay: Overlay) : NewsUiEvent

    /** Pops the top of the overlay stack — one back-press rule for all of them. */
    data object CloseOverlay : NewsUiEvent

    /** Opens the search feature above the feed. */
    data object OpenSearch : NewsUiEvent

    data object RefreshNews : NewsUiEvent
    data object RetryLoading : NewsUiEvent

    /**
     * Try the next page again after it failed. The feed keeps whatever is
     * already loaded — this only resumes it from where it stopped.
     */
    data object RetryNextPage : NewsUiEvent
    data object DismissError : NewsUiEvent
    /** Acknowledges the device-integrity warning, which then stays dismissed. */
    data object DismissSecurityWarning : NewsUiEvent

    /** Fired once, after the reader has read enough to make an informed choice. */
    data object RequestNotificationPermissionIfDue : NewsUiEvent

    /** Ticks a category on or off during onboarding; nothing is saved yet. */
    data class OnboardingToggleCategory(val category: NewsCategory) : NewsUiEvent

    /** Advances a step, or finishes and keeps whatever was chosen. */
    data object OnboardingNext : NewsUiEvent

    /**
     * Leaves onboarding early. Not a cancel — the defaults are real settings
     * and the reader gets a working app, they just did not pick.
     */
    data object OnboardingSkip : NewsUiEvent
}

/**
 * Which per-tier notification switch was toggled. Matches the "tier" field the
 * server puts in the FCM data payload (server's `PushTier.label`), so the
 * stored preference and the incoming message can be compared as plain strings.
 */
enum class NotificationTier(val wireValue: String) {
    BREAKING("breaking"), TOP_STORY("top_story"), REMINDER("reminder");
}
