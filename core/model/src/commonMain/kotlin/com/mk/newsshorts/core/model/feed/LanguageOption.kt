package com.mk.newsshorts.core.model.feed

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
