package com.mk.newsshorts.core.model.feed

enum class LanguageOption(
    val displayName: String,
    val code: String,
    val nativeName: String,
    val flag: String
) {
    ENGLISH(displayName = "English", code = "en", nativeName = "English", flag = "🇺🇸"),
    ARABIC(displayName = "Arabic", code = "ar", nativeName = "العربية", flag = "🇸🇦")
}
