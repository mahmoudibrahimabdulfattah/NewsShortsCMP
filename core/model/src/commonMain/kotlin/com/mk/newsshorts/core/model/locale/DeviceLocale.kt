package com.mk.newsshorts.core.model.locale

data class DeviceLocale(
    val languageTag: String,
    val region: String?,
)

expect fun currentDeviceLocale(): DeviceLocale

internal fun regionFromLanguageTag(languageTag: String): String? =
    languageTag
        .split('-', '_')
        .drop(1)
        .firstOrNull { part ->
            (part.length == 2 && part.all { character -> character.isLetter() }) ||
                (part.length == 3 && part.all { character -> character.isDigit() })
        }
        ?.lowercase()
