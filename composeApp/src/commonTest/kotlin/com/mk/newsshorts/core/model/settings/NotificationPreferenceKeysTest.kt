package com.mk.newsshorts.core.model.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * This gate is checked once per push, inside a system-instantiated service
 * that cannot be exercised directly in a fast test — these cases cover the
 * decision itself, which is where a mistake would either spam a reader who
 * turned a tier off or go silent for one they left on.
 */
class NotificationPreferenceKeysTest {

    @Test
    fun `master off blocks every tier regardless of its own switch`() {
        assertFalse(NotificationPreferenceKeys.isAllowed(masterEnabled = false, tierEnabled = true))
        assertFalse(NotificationPreferenceKeys.isAllowed(masterEnabled = false, tierEnabled = false))
        assertFalse(NotificationPreferenceKeys.isAllowed(masterEnabled = false, tierEnabled = null))
    }

    @Test
    fun `a single tier off blocks only that tier`() {
        assertFalse(NotificationPreferenceKeys.isAllowed(masterEnabled = true, tierEnabled = false))
        assertTrue(NotificationPreferenceKeys.isAllowed(masterEnabled = true, tierEnabled = true))
    }

    @Test
    fun `an unrecognised tier is allowed through rather than dropped`() {
        // A server sending a tier this build predates should not go silent —
        // that is a worse failure than one extra notification.
        assertTrue(NotificationPreferenceKeys.isAllowed(masterEnabled = true, tierEnabled = null))
    }

    @Test
    fun `every known wire tier maps to its own key`() {
        assertEquals(NotificationPreferenceKeys.NOTIFY_BREAKING, NotificationPreferenceKeys.keyForWireTier("breaking"))
        assertEquals(NotificationPreferenceKeys.NOTIFY_TOP_STORY, NotificationPreferenceKeys.keyForWireTier("top_story"))
        assertEquals(NotificationPreferenceKeys.NOTIFY_REMINDER, NotificationPreferenceKeys.keyForWireTier("reminder"))
    }

    @Test
    fun `an unknown wire value maps to no key`() {
        assertEquals(null, NotificationPreferenceKeys.keyForWireTier("future_tier"))
        assertEquals(null, NotificationPreferenceKeys.keyForWireTier(""))
    }
}
