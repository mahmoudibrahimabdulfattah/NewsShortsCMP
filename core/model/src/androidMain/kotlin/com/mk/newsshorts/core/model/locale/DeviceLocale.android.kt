package com.mk.newsshorts.core.model.locale

import java.util.Locale

actual fun currentDeviceLocale(): DeviceLocale {
    val locale = Locale.getDefault()
    return DeviceLocale(
        languageTag = locale.toLanguageTag(),
        region = locale.country.takeIf { it.isNotBlank() }?.lowercase(),
    )
}
