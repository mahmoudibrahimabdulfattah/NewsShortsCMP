package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.auth.AuthFailure
import com.mk.newsshorts.auth.AuthUser
import com.mk.newsshorts.data.remote.RequiredUpdate
import com.mk.newsshorts.security.SecurityNotice
import com.mk.newsshorts.security.SecurityReason
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.presentation.localization.AppLocale

data class NewsUiState(
    val isLoading: Boolean = true,
    val articles: List<NewsArticle> = emptyList(),
    val selectedCategory: NewsCategory = NewsCategory.GENERAL,
    val currentArticleIndex: Int = 0,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    val selectedCountry: CountryOption = CountryOption.UNITED_STATES,
    val selectedLanguage: LanguageOption = LanguageOption.ENGLISH,
    val appLocale: AppLocale = AppLocale.ENGLISH,
    val currentTab: NavigationTab = NavigationTab.FOR_YOU,
    val savedArticles: List<NewsArticle> = emptyList(),
    val isOfflineMode: Boolean = false,
    val isFirstLaunch: Boolean = true,
    /**
     * Bumped every time [articles] is *replaced* — a refresh, a new category, a
     * new country — and deliberately not when a page is appended to it.
     *
     * The pager scrolls back to the top when this changes, which nothing else
     * in the state can tell it: after a refresh the list is a different list,
     * but its size and often its first card are the same, so the pager would
     * otherwise sit exactly where it was on a feed that had moved underneath it.
     */
    val feedRevision: Int = 0,
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
    /**
     * Screens pushed above the tabs — Profile → Settings, Profile → Saved,
     * a details screen. Last element is what is on screen; empty means none.
     * A list rather than one nullable field, because Settings needs Sign-in
     * above it in a later phase and the tabs themselves are not part of this
     * stack — they switch, they do not push.
     */
    val overlays: List<Overlay> = emptyList(),
    /** Non-null when the backend no longer supports this build; blocks the UI. */
    val requiredUpdate: RequiredUpdate? = null,
    /** Result of the device-integrity check under the backend's policy. */
    val securityNotice: SecurityNotice = SecurityNotice.NONE,
    /** Which signals produced [securityNotice]; decides the wording shown. */
    val securityReason: SecurityReason = SecurityReason.INTEGRITY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val notifyBreaking: Boolean = true,
    val notifyTopStory: Boolean = true,
    val notifyReminder: Boolean = true,
    /** Null for a guest. Every screen already works without one. */
    val authUser: AuthUser? = null,
    /** True while a sign-in/up/delete call is in flight — drives a spinner. */
    val authInProgress: Boolean = false,
    /**
     * The last auth failure, shown once then dismissed. A value rather than
     * a message so the screen can render it in the reader's language — the
     * SDKs' own text is English-only.
     */
    val authError: AuthFailure? = null,
    /**
     * The address a sign-in link was just sent to. Non-null means the reader is
     * waiting on their inbox, which is a state the screen has to show plainly:
     * nothing else happens in the app until they leave it for their mail.
     */
    val pendingSignInEmail: String? = null,
    /**
     * A followed link that arrived with no stored address to redeem it — the
     * link was opened on a different device than asked for it. Held so the
     * screen can ask who it belongs to instead of discarding it.
     */
    val unclaimedSignInLink: String? = null,
    /** Exactly what the reader has typed, unfolded — this is what the field shows. */
    val searchQuery: String = "",
    val searchResults: List<NewsArticle> = emptyList(),
    val isSearching: Boolean = false,
    /**
     * A search finished for the query currently in the field. Without it an
     * empty result list is ambiguous — it looks the same before the first
     * search runs as it does after one that found nothing, and those are two
     * different screens.
     */
    val searchSettled: Boolean = false,
    /** The corpus could not be reached and nothing was held from earlier. */
    val searchFailed: Boolean = false,
    /** Most recent first. Never leaves the device — see `RecentSearchesStore`. */
    val recentSearches: List<String> = emptyList(),
) {
    val hasArticles: Boolean
        get() = articles.isNotEmpty()

    val currentArticle: NewsArticle?
        get() = articles.getOrNull(currentArticleIndex)

    val isError: Boolean
        get() = errorMessage != null && !isLoading

    val hasSavedArticles: Boolean
        get() = savedArticles.isNotEmpty()

    val hasMorePages: Boolean
        get() = nextPageFile != null

    /** The reader is on the last card and the feed genuinely stops there. */
    val isAtEndOfFeed: Boolean
        get() = hasArticles && !hasMorePages && currentArticleIndex >= articles.lastIndex

    /** A search ran for what is in the field and came back with nothing. */
    val hasNoSearchResults: Boolean
        get() = searchSettled && !searchFailed && searchResults.isEmpty()

    /**
     * The details overlay, if that is what is on top. Kept as a derived
     * property under the old name so every existing read site — the pager,
     * the save-state lookup, the details composable itself — keeps compiling
     * against the shape it already expects.
     */
    val articleDetails: ArticleDetails?
        get() = (overlays.lastOrNull() as? Overlay.Details)?.let {
            ArticleDetails(article = it.article, origin = it.origin)
        }
}

/** One screen pushed above the tabs. See [NewsUiState.overlays]. */
sealed interface Overlay {
    data class Details(val article: NewsArticle, val origin: ArticleOpenOrigin) : Overlay
    data object Settings : Overlay
    data object SavedArticles : Overlay
    data object SignIn : Overlay
    data object Search : Overlay
    /** Third-party notices. The bundled fonts are under the OFL, which
     *  requires its notice to travel with them. */
    data object Licenses : Overlay
}

/**
 * SYSTEM follows the device; LIGHT/DARK are an explicit override. Applies to
 * every screen except the vertical feed itself, which stays dark regardless —
 * its text sits on full-bleed photos, not on a themed surface.
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun resolveIsDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}

/**
 * The article being shown full-screen.
 *
 * Holds the article itself rather than an index: [ArticleId] is derived from
 * list position, and an article arriving from a notification is not in any list
 * at all on a cold start.
 */
data class ArticleDetails(
    val article: NewsArticle,
    val origin: ArticleOpenOrigin
)

/** Where a details screen was opened from — reported with the analytics event. */
enum class ArticleOpenOrigin(val analyticsValue: String) {
    FEED("feed"),
    SAVED("saved"),
    PUSH("push"),
    /** A shared link. Kept apart from PUSH so the two can be compared. */
    SHARE("share"),
    /** A search result. Says whether search finds people anything worth opening. */
    SEARCH("search")
}

enum class NavigationTab(
    val title: String,
    val icon: String
) {
    FOR_YOU(title = "For You", icon = "🔥"),
    COUNTRIES(title = "Countries", icon = "🌍"),
    PROFILE(title = "Profile", icon = "⚙️")
}

enum class LanguageOption(
    val displayName: String,
    val code: String,
    val nativeName: String,
    val flag: String
) {
    ENGLISH(displayName = "English", code = "en", nativeName = "English", flag = "🇺🇸"),
    ARABIC(displayName = "Arabic", code = "ar", nativeName = "العربية", flag = "🇸🇦"),
    GERMAN(displayName = "German", code = "de", nativeName = "Deutsch", flag = "🇩🇪"),
    SPANISH(displayName = "Spanish", code = "es", nativeName = "Español", flag = "🇪🇸"),
    FRENCH(displayName = "French", code = "fr", nativeName = "Français", flag = "🇫🇷"),
    ITALIAN(displayName = "Italian", code = "it", nativeName = "Italiano", flag = "🇮🇹"),
    DUTCH(displayName = "Dutch", code = "nl", nativeName = "Nederlands", flag = "🇳🇱"),
    NORWEGIAN(displayName = "Norwegian", code = "no", nativeName = "Norsk", flag = "🇳🇴"),
    PORTUGUESE(displayName = "Portuguese", code = "pt", nativeName = "Português", flag = "🇵🇹"),
    RUSSIAN(displayName = "Russian", code = "ru", nativeName = "Русский", flag = "🇷🇺"),
    CHINESE(displayName = "Chinese", code = "zh", nativeName = "中文", flag = "🇨🇳"),
    HEBREW(displayName = "Hebrew", code = "he", nativeName = "עברית", flag = "🇮🇱")
}

enum class CountryOption(
    val displayName: String,
    val code: String,
    val flag: String
) {
    UNITED_STATES(displayName = "United States", code = "us", flag = "🇺🇸"),
    UNITED_KINGDOM(displayName = "United Kingdom", code = "gb", flag = "🇬🇧"),
    EGYPT(displayName = "Egypt", code = "eg", flag = "🇪🇬"),
    SAUDI_ARABIA(displayName = "Saudi Arabia", code = "sa", flag = "🇸🇦"),
    UAE(displayName = "UAE", code = "ae", flag = "🇦🇪"),
    GERMANY(displayName = "Germany", code = "de", flag = "🇩🇪"),
    FRANCE(displayName = "France", code = "fr", flag = "🇫🇷"),
    INDIA(displayName = "India", code = "in", flag = "🇮🇳"),
    CHINA(displayName = "China", code = "cn", flag = "🇨🇳"),
    JAPAN(displayName = "Japan", code = "jp", flag = "🇯🇵"),
    AUSTRALIA(displayName = "Australia", code = "au", flag = "🇦🇺"),
    CANADA(displayName = "Canada", code = "ca", flag = "🇨🇦"),
    BRAZIL(displayName = "Brazil", code = "br", flag = "🇧🇷")
}
