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
