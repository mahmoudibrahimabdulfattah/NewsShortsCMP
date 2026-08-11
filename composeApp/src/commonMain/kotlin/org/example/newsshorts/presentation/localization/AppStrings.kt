package org.example.newsshorts.presentation.localization

interface AppStrings {
    val appName: String
    val forYou: String
    val countries: String
    val profile: String
    val newsLanguage: String
    val newsLanguageDescription: String
    val appLanguage: String
    val appLanguageDescription: String
    val savedArticles: String
    val noSavedArticles: String
    val savedArticlesDescription: String
    val about: String
    val aboutDescription: String
    val appVersion: String
    val platform: String
    val poweredBy: String
    val readFullArticle: String
    val save: String
    val share: String
    val swipeUpForMore: String
    val newsFromCountry: String
    val settingsPreferences: String
    val loading: String
    val loadingNewsFrom: String
    val errorTitle: String
    val noNewsFound: String
    val noInternetConnection: String
    val firstTimeMessage: String
    val tryAgain: String
    val languageChangedTo: String
    val articleSaved: String
    val articleAlreadySaved: String
    val articleRemoved: String
    val showingSampleNews: String
    val english: String
    val arabic: String
    val personalizeExperience: String
    val newsReader: String
    val recently: String
    val loadingNews: String
    val fetchingLatestStories: String
    val appTagline: String
    val unknownError: String
    val articleDetails: String
    val readAtSource: String
    val back: String
    val summaryDisclaimer: String
    val unableToOpenLink: String

    /** Keyed by [org.example.newsshorts.domain.model.NewsCategory.apiValue]. */
    val categoryNames: Map<String, String>

    /** Keyed by [org.example.newsshorts.presentation.mvi.CountryOption.code]. */
    val countryNames: Map<String, String>

    /** Keyed by [org.example.newsshorts.presentation.mvi.LanguageOption.code]. */
    val languageNames: Map<String, String>

    /** Twelve abbreviated month names, January first. */
    val monthNames: List<String>
}

object EnglishStrings : AppStrings {
    override val appName: String = "News Shorts"
    override val forYou: String = "For You"
    override val countries: String = "Countries"
    override val profile: String = "Profile"
    override val newsLanguage: String = "News Language"
    override val newsLanguageDescription: String = "Choose your preferred language for news"
    override val appLanguage: String = "App Language"
    override val appLanguageDescription: String = "Choose the app interface language"
    override val savedArticles: String = "Saved Articles"
    override val noSavedArticles: String = "No saved articles"
    override val savedArticlesDescription: String = "Tap the bookmark icon on any article to save it for later"
    override val about: String = "About"
    override val aboutDescription: String = "App information"
    override val appVersion: String = "Version"
    override val platform: String = "Platform"
    override val poweredBy: String = "Powered by"
    override val readFullArticle: String = "Read Article"
    override val save: String = "Save"
    override val share: String = "Share"
    override val swipeUpForMore: String = "✨ Swipe up for more stories"
    override val newsFromCountry: String = "🌍 News from"
    override val settingsPreferences: String = "⚙️ Settings & Preferences"
    override val loading: String = "Loading..."
    override val loadingNewsFrom: String = "Loading news from"
    override val errorTitle: String = "Oops! Something went wrong"
    override val noNewsFound: String = "No news articles found."
    override val noInternetConnection: String = "No internet connection. Please check your network and try again."
    override val firstTimeMessage: String = "Welcome! Please connect to the internet to load news for the first time."
    override val tryAgain: String = "Try Again"
    override val languageChangedTo: String = "Language changed to"
    override val articleSaved: String = "Article saved!"
    override val articleAlreadySaved: String = "Article already saved!"
    override val articleRemoved: String = "Article removed"
    override val showingSampleNews: String = "Showing cached news for"
    override val english: String = "English"
    override val arabic: String = "Arabic"
    override val personalizeExperience: String = "Personalize your news experience"
    override val newsReader: String = "News Reader"
    override val recently: String = "Recently"
    override val loadingNews: String = "Loading News..."
    override val fetchingLatestStories: String = "Fetching the latest stories"
    override val appTagline: String = "Stay informed, stay brief"
    override val unknownError: String = "Unknown error"
    override val articleDetails: String = "Article"
    override val readAtSource: String = "Read at source"
    override val back: String = "Back"
    override val summaryDisclaimer: String =
        "AI-generated summary. Open the source for the full story."
    override val unableToOpenLink: String = "Unable to open link"

    override val categoryNames: Map<String, String> = mapOf(
        "general" to "General",
        "technology" to "Technology",
        "business" to "Business",
        "entertainment" to "Entertainment",
        "sports" to "Sports",
        "science" to "Science",
        "health" to "Health",
    )

    override val countryNames: Map<String, String> = mapOf(
        "us" to "United States",
        "gb" to "United Kingdom",
        "eg" to "Egypt",
        "sa" to "Saudi Arabia",
        "ae" to "UAE",
        "de" to "Germany",
        "fr" to "France",
        "in" to "India",
        "cn" to "China",
        "jp" to "Japan",
        "au" to "Australia",
        "ca" to "Canada",
        "br" to "Brazil",
    )

    override val languageNames: Map<String, String> = mapOf(
        "en" to "English",
        "ar" to "Arabic",
        "de" to "German",
        "es" to "Spanish",
        "fr" to "French",
        "it" to "Italian",
        "nl" to "Dutch",
        "no" to "Norwegian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "zh" to "Chinese",
        "he" to "Hebrew",
    )

    override val monthNames: List<String> = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}

object ArabicStrings : AppStrings {
    override val appName: String = "أخبار مختصرة"
    override val forYou: String = "لك"
    override val countries: String = "الدول"
    override val profile: String = "الملف الشخصي"
    override val newsLanguage: String = "لغة الأخبار"
    override val newsLanguageDescription: String = "اختر لغتك المفضلة للأخبار"
    override val appLanguage: String = "لغة التطبيق"
    override val appLanguageDescription: String = "اختر لغة واجهة التطبيق"
    override val savedArticles: String = "المقالات المحفوظة"
    override val noSavedArticles: String = "لا توجد مقالات محفوظة"
    override val savedArticlesDescription: String = "اضغط على أيقونة الإشارة المرجعية على أي مقال لحفظه"
    override val about: String = "حول التطبيق"
    override val aboutDescription: String = "معلومات التطبيق"
    override val appVersion: String = "الإصدار"
    override val platform: String = "المنصة"
    override val poweredBy: String = "مدعوم من"
    override val readFullArticle: String = "قراءة المقال"
    override val save: String = "حفظ"
    override val share: String = "مشاركة"
    override val swipeUpForMore: String = "✨ اسحب للأعلى لمزيد من الأخبار"
    override val newsFromCountry: String = "🌍 أخبار من"
    override val settingsPreferences: String = "⚙️ الإعدادات والتفضيلات"
    override val loading: String = "جاري التحميل..."
    override val loadingNewsFrom: String = "جاري تحميل أخبار"
    override val errorTitle: String = "حدث خطأ!"
    override val noNewsFound: String = "لم يتم العثور على أخبار."
    override val noInternetConnection: String = "لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة والمحاولة مرة أخرى."
    override val firstTimeMessage: String = "مرحباً! يرجى الاتصال بالإنترنت لتحميل الأخبار لأول مرة."
    override val tryAgain: String = "حاول مرة أخرى"
    override val languageChangedTo: String = "تم تغيير اللغة إلى"
    override val articleSaved: String = "تم حفظ المقال!"
    override val articleAlreadySaved: String = "المقال محفوظ بالفعل!"
    override val articleRemoved: String = "تم إزالة المقال"
    override val showingSampleNews: String = "عرض الأخبار المحفوظة لـ"
    override val english: String = "الإنجليزية"
    override val arabic: String = "العربية"
    override val personalizeExperience: String = "خصص تجربتك الإخبارية"
    override val newsReader: String = "قارئ الأخبار"
    override val recently: String = "مؤخراً"
    override val loadingNews: String = "جاري تحميل الأخبار..."
    override val fetchingLatestStories: String = "جلب آخر الأخبار"
    override val appTagline: String = "ابق على اطلاع، باختصار"
    override val unknownError: String = "خطأ غير معروف"
    override val articleDetails: String = "المقال"
    override val readAtSource: String = "اقرأ من المصدر"
    override val back: String = "رجوع"
    override val summaryDisclaimer: String =
        "ملخص مُولَّد بالذكاء الاصطناعي. افتح المصدر لقراءة الخبر كاملاً."
    override val unableToOpenLink: String = "تعذّر فتح الرابط"

    override val categoryNames: Map<String, String> = mapOf(
        "general" to "عام",
        "technology" to "تكنولوجيا",
        "business" to "اقتصاد",
        "entertainment" to "منوعات",
        "sports" to "رياضة",
        "science" to "علوم",
        "health" to "صحة",
    )

    override val countryNames: Map<String, String> = mapOf(
        "us" to "الولايات المتحدة",
        "gb" to "المملكة المتحدة",
        "eg" to "مصر",
        "sa" to "السعودية",
        "ae" to "الإمارات",
        "de" to "ألمانيا",
        "fr" to "فرنسا",
        "in" to "الهند",
        "cn" to "الصين",
        "jp" to "اليابان",
        "au" to "أستراليا",
        "ca" to "كندا",
        "br" to "البرازيل",
    )

    override val languageNames: Map<String, String> = mapOf(
        "en" to "الإنجليزية",
        "ar" to "العربية",
        "de" to "الألمانية",
        "es" to "الإسبانية",
        "fr" to "الفرنسية",
        "it" to "الإيطالية",
        "nl" to "الهولندية",
        "no" to "النرويجية",
        "pt" to "البرتغالية",
        "ru" to "الروسية",
        "zh" to "الصينية",
        "he" to "العبرية",
    )

    override val monthNames: List<String> = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )
}

fun getStrings(locale: AppLocale): AppStrings {
    return when (locale) {
        AppLocale.ENGLISH -> EnglishStrings
        AppLocale.ARABIC -> ArabicStrings
    }
}

enum class AppLocale(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean
) {
    ENGLISH(code = "en", displayName = "English", nativeName = "English", isRtl = false),
    ARABIC(code = "ar", displayName = "Arabic", nativeName = "العربية", isRtl = true);

    companion object {
        fun fromCode(code: String): AppLocale {
            return entries.find { it.code == code } ?: ENGLISH
        }
    }
}
