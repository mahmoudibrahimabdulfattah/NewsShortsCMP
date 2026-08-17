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
    /** Opens the published privacy policy in a browser tab. */
    data object OpenPrivacyPolicy : NewsUiEvent

    data class OpenDeepLink(val link: ArticleDeepLink) : NewsUiEvent

    /** Pushes a screen above the tabs. See [com.mk.newsshorts.presentation.mvi.Overlay]. */
    data class OpenOverlay(val overlay: Overlay) : NewsUiEvent

    /** Pops the top of the overlay stack — one back-press rule for all of them. */
    data object CloseOverlay : NewsUiEvent

    /** Opens search from the feed, with the recent searches already loaded. */
    data object OpenSearch : NewsUiEvent

    /**
     * A keystroke. Debounced before it becomes a search — matching runs over
     * the whole corpus, and doing that per character would burn a phone's
     * battery to show results nobody has finished asking for.
     */
    data class SearchQueryChanged(val query: String) : NewsUiEvent

    /**
     * Search this, now: the keyboard's search key, or a tap on a recent one.
     * Skips the debounce, and is the moment a query is worth remembering —
     * unlike a debounced keystroke, which is usually a prefix of what the
     * reader actually meant.
     */
    data class RunSearch(val query: String) : NewsUiEvent

    /** Empties the field without leaving search. */
    data object ClearSearchQuery : NewsUiEvent
    data class RemoveRecentSearch(val query: String) : NewsUiEvent
    data object ClearRecentSearches : NewsUiEvent

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

    data class SelectThemeMode(val mode: ThemeMode) : NewsUiEvent

    data object ToggleNotificationsEnabled : NewsUiEvent
    data class ToggleNotificationTier(val tier: NotificationTier) : NewsUiEvent
    /** Fired once, after the reader has read enough to make an informed choice. */
    data object RequestNotificationPermissionIfDue : NewsUiEvent

    data object SignInWithGoogle : NewsUiEvent
    /** Emails a sign-in link. Signing in only happens once it is followed. */
    data class SendSignInLink(val email: String) : NewsUiEvent
    /** A followed link arrived from the OS, carrying no address of its own. */
    data class SignInLinkOpened(val link: String) : NewsUiEvent
    /**
     * The address for a link opened on a device that never requested one, so
     * nothing was stored to match it against.
     */
    data class SupplyLinkEmail(val email: String) : NewsUiEvent
    /** Back out of the "check your inbox" state to send to a different address. */
    data object CancelPendingSignInLink : NewsUiEvent
    data object SignOut : NewsUiEvent
    data object DeleteAccount : NewsUiEvent
    data object DismissAuthError : NewsUiEvent

    /** Ticks a category on or off during onboarding; nothing is saved yet. */
    data class OnboardingToggleCategory(val category: NewsCategory) : NewsUiEvent

    /** Advances a step, or finishes and keeps whatever was chosen. */
    data object OnboardingNext : NewsUiEvent

    /**
     * Leaves onboarding early. Not a cancel — the defaults are real settings
     * and the reader gets a working app, they just did not pick.
     */
    data object OnboardingSkip : NewsUiEvent

    data class SelectTextScale(val scale: TextScale) : NewsUiEvent
}

/**
 * Which per-tier notification switch was toggled. Matches the "tier" field the
 * server puts in the FCM data payload (server's `PushTier.label`), so the
 * stored preference and the incoming message can be compared as plain strings.
 */
enum class NotificationTier(val wireValue: String) {
    BREAKING("breaking"), TOP_STORY("top_story"), REMINDER("reminder");
}
