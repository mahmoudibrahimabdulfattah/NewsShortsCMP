package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.domain.model.NewsArticle

sealed interface Overlay {
    data class Details(val article: NewsArticle, val origin: ArticleOpenOrigin) : Overlay
    data object Settings : Overlay
    data object SavedArticles : Overlay
    data object SignIn : Overlay
    data object Search : Overlay
    data object NotificationInbox : Overlay
    /** Third-party notices. The bundled fonts are under the OFL, which
     *  requires its notice to travel with them. */
    data object Licenses : Overlay
}

/**
 * The three things worth asking before the first headline: what language to
 * read in, what to read about, and whether to be told when something breaks.
 *
 * Ordered by how much the answer changes what the reader sees next — language
 * rewrites every screen including this one, categories decide what the feed
 * opens on, and notifications only matter after they have left.
 */
enum class OnboardingStep {
    LANGUAGE, CATEGORIES, NOTIFICATIONS;

    val next: OnboardingStep? get() = entries.getOrNull(ordinal + 1)
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
