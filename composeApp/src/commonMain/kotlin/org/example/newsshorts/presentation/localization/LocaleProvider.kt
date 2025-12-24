package org.example.newsshorts.presentation.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val LocalAppStrings = compositionLocalOf<AppStrings> { EnglishStrings }
val LocalAppLocale = compositionLocalOf<AppLocale> { AppLocale.ENGLISH }

@Composable
fun LocaleProvider(
    locale: AppLocale,
    content: @Composable () -> Unit
) {
    val strings: AppStrings = remember(locale) { getStrings(locale) }
    val layoutDirection: LayoutDirection = if (locale.isRtl) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLocale provides locale,
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}

@Composable
fun appStrings(): AppStrings = LocalAppStrings.current

@Composable
fun appLocale(): AppLocale = LocalAppLocale.current

@Composable
fun isRtl(): Boolean = LocalAppLocale.current.isRtl

