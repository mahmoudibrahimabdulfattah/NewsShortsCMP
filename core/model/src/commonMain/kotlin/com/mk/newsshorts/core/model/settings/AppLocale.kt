package com.mk.newsshorts.core.model.settings

enum class AppLocale(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean,
) {
    ENGLISH(code = "en", displayName = "English", nativeName = "English", isRtl = false),
    ARABIC(code = "ar", displayName = "Arabic", nativeName = "العربية", isRtl = true);

    companion object {
        fun fromCode(code: String): AppLocale =
            entries.find { it.code == code } ?: ENGLISH
    }
}
