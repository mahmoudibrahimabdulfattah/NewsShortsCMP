package com.mk.newsshorts.server.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The app names the page this module publishes, so the two implementations have
 * to agree exactly — a one-bit difference is a 404 on every shared link. The
 * literals below are duplicated in the app's ShareSlugTest on purpose. Changing
 * the algorithm here must fail there too. Keep them in sync.
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

    /** Base36 of a 64-bit hash: short enough to read back off a phone screen. */
    @Test
    fun `stays within thirteen characters`() {
        listOf("", "a", "https://example.com/" + "x".repeat(400)).forEach { url ->
            val slug = ShareSlug.of(url)
            assertEquals(true, slug.length in 1..13, "$slug is ${slug.length} characters")
            assertEquals(true, slug.all { it.isDigit() || it in 'a'..'z' }, slug)
        }
    }
}
