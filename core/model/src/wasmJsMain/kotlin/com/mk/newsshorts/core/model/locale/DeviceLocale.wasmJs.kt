package com.mk.newsshorts.core.model.locale

private fun navigatorLanguage(): String = js("navigator.language")

actual fun currentDeviceLocale(): DeviceLocale {
    val languageTag = navigatorLanguage()
    return DeviceLocale(
        languageTag = languageTag,
        region = regionFromLanguageTag(languageTag),
    )
}
