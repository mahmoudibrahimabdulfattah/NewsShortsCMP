package com.mk.newsshorts.presentation.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedLinksTest {

    @Test
    fun `the language is appended as the first query parameter`() {
        assertEquals(
            "https://example.com/privacy/?lang=ar",
            urlInLanguage("https://example.com/privacy/", "ar"),
        )
    }

    @Test
    fun `an existing query is kept`() {
        // The URL is configurable from local.properties, so it may already
        // carry one — replacing the '?' would silently drop it.
        assertEquals(
            "https://example.com/privacy?v=2&lang=en",
            urlInLanguage("https://example.com/privacy?v=2", "en"),
        )
    }

    @Test
    fun `a blank code leaves the url alone`() {
        // Better the page guesses from the browser than that it is handed an
        // empty parameter to interpret.
        assertEquals("https://example.com/privacy", urlInLanguage("https://example.com/privacy", ""))
    }

    @Test
    fun `every app locale produces a code the page can act on`() {
        AppLocale.entries.forEach { locale ->
            assertEquals(
                "https://example.com/?lang=${locale.code}",
                urlInLanguage("https://example.com/", locale.code),
            )
        }
    }
}
