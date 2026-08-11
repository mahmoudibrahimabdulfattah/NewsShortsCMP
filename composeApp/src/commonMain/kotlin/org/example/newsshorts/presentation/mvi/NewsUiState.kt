package org.example.newsshorts.presentation.mvi

import org.example.newsshorts.domain.model.NewsArticle
import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.presentation.localization.AppLocale

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
    /** Non-null while the details screen is showing. One level deep, so no stack. */
    val articleDetails: ArticleDetails? = null
) {
    val hasArticles: Boolean
        get() = articles.isNotEmpty()

    val currentArticle: NewsArticle?
        get() = articles.getOrNull(currentArticleIndex)

    val isError: Boolean
        get() = errorMessage != null && !isLoading

    val hasSavedArticles: Boolean
        get() = savedArticles.isNotEmpty()
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
    SHARE("share")
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
