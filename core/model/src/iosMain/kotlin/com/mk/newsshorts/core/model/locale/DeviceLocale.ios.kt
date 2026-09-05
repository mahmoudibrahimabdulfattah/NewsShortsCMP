package com.mk.newsshorts.core.model.locale

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual fun currentDeviceLocale(): DeviceLocale {
    val locale = NSLocale.currentLocale
    // Foundation reports `ar_EG@calendar=islamic`; the rest of the app speaks
    // BCP 47, so drop the extension and swap the separator.
    val languageTag = locale.localeIdentifier
        .substringBefore('@')
        .replace('_', '-')
    return DeviceLocale(
        languageTag = languageTag,
        region = locale.countryCode?.takeIf { it.isNotBlank() }?.lowercase()
            ?: regionFromLanguageTag(languageTag),
    )
}
