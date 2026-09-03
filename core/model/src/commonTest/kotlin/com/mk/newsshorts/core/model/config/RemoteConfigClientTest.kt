package com.mk.newsshorts.core.model.config

import com.mk.newsshorts.core.contract.config.AppConfigDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * This rule can take the app away from every installed reader at once, and a
 * mistake in it is only visible after the file is published. The cases below are
 * the ones where being wrong is expensive.
 */
class RemoteConfigClientTest {

    private val store = "https://play.google.com/store/apps/details?id=com.mk.newsshorts"

    @Test
    fun `a build below the minimum is blocked`() {
        val update = requiredUpdateFor(
            AppConfigDto(minSupportedVersionCode = 5, latestVersionCode = 5, storeUrl = store),
            currentVersionCode = 4,
        )
        assertEquals(store, update?.storeUrl)
    }

    @Test
    fun `the minimum itself is still supported`() {
        val update = requiredUpdateFor(
            AppConfigDto(minSupportedVersionCode = 5, latestVersionCode = 9, storeUrl = store),
            currentVersionCode = 5,
        )
        assertNull(update, "the minimum supported build was blocked by its own floor")
    }

    @Test
    fun `a newer release available is not a reason to block`() {
        val update = requiredUpdateFor(
            AppConfigDto(minSupportedVersionCode = 1, latestVersionCode = 12, storeUrl = store),
            currentVersionCode = 3,
        )
        assertNull(update)
    }

    @Test
    fun `no store link means no gate`() {
        // Before the app is on Play the URL is empty. Blocking then would leave
        // a reader on a screen whose only button goes nowhere.
        val update = requiredUpdateFor(
            AppConfigDto(minSupportedVersionCode = 99, latestVersionCode = 99, storeUrl = ""),
            currentVersionCode = 1,
        )
        assertNull(update)
    }

    @Test
    fun `a non-https store link is refused`() {
        val update = requiredUpdateFor(
            AppConfigDto(minSupportedVersionCode = 99, latestVersionCode = 99, storeUrl = "javascript:alert(1)"),
            currentVersionCode = 1,
        )
        assertNull(update)
    }

    @Test
    fun `defaults support every build`() {
        // A truncated or half-written app.json parses into the defaults; those
        // must not lock anyone out.
        assertNull(requiredUpdateFor(AppConfigDto(), currentVersionCode = 1))
    }
}
