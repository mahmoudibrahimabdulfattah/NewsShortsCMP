package com.mk.newsshorts.presentation.localization

/**
 * Tags a published page with the language the reader chose in the app.
 *
 * The pages the app links out to carry both languages, and without this they
 * would guess from the browser's locale — which is the system language, not the
 * app's. Someone reading the app in Arabic on an English phone would be handed
 * the English policy, and the setting they explicitly chose would count for
 * nothing.
 *
 * Appends rather than assumes: the configured URL may already carry a query.
 */
fun urlInLanguage(baseUrl: String, languageCode: String): String {
    if (languageCode.isBlank()) return baseUrl
    val separator = if ('?' in baseUrl) '&' else '?'
    return "$baseUrl$separator$LANG_PARAM=$languageCode"
}

private const val LANG_PARAM = "lang"
