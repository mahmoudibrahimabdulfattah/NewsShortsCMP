package com.mk.newsshorts.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The app only names the landing page; the server writes it. If these two
 * implementations drift, every shared link 404s and nothing else breaks — so
 * the failure would show up in a friend's chat window rather than in a build.
 *
 * The literals below are duplicated in the server's ShareSlugTest on purpose.
 * Changing the algorithm here must fail there too. Keep them in sync.
 */
class ShareSlugTest {

    @Test
    fun `pins the slug for an ascii url`() {
        assertEquals("2s4erimguajnl", ShareSlug.of("https://example.com/story"))
    }

    /**
     * Bytes above 127 are negative in Kotlin, and an implementation that lets
     * one sign-extend produces a different slug for exactly the URLs an Arabic
     * feed is full of — while ASCII ones keep matching, so the mistake looks
     * like it works.
     */
    @Test
    fun `pins the slug for a url with non-ascii bytes`() {
        assertEquals("1pp7ptiryl177", ShareSlug.of("https://example.com/قصة"))
        assertEquals("81y26mvqyrou", ShareSlug.of("https://example.com/%D9%82%D8%B5%D8%A9"))
    }

    /** A trailing newline out of an RSS field must not move the page. */
    @Test
    fun `ignores surrounding whitespace`() {
        assertEquals(
            ShareSlug.of("https://example.com/story"),
            ShareSlug.of("  https://example.com/story\n"),
        )
    }

    @Test
    fun `gives different urls different slugs`() {
        assertNotEquals(
            ShareSlug.of("https://example.com/story"),
            ShareSlug.of("https://example.com/story/"),
        )
    }
}
