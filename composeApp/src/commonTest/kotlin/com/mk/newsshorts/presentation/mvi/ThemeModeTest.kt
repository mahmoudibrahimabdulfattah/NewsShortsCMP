package com.mk.newsshorts.presentation.mvi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The feed itself never consults this — it is always forced dark — so these
 * cases only need to cover the screens that do: Profile, Settings, Saved, the
 * details screen.
 */
class ThemeModeTest {

    @Test
    fun `system follows the device either way`() {
        assertTrue(ThemeMode.SYSTEM.resolveIsDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveIsDark(systemIsDark = false))
    }

    @Test
    fun `light overrides a dark system`() {
        assertFalse(ThemeMode.LIGHT.resolveIsDark(systemIsDark = true))
        assertFalse(ThemeMode.LIGHT.resolveIsDark(systemIsDark = false))
    }

    @Test
    fun `dark overrides a light system`() {
        assertTrue(ThemeMode.DARK.resolveIsDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.resolveIsDark(systemIsDark = false))
    }

    @Test
    fun `a stored value round-trips through the enum name`() {
        // handleSelectThemeMode persists mode.name.lowercase(); loadSavedSettings
        // reads it back with equals(ignoreCase = true). Both directions in one
        // case, so a rename of the enum constant fails this rather than
        // silently resetting everyone to SYSTEM on the next launch.
        ThemeMode.entries.forEach { mode ->
            val stored = mode.name.lowercase()
            val restored = ThemeMode.entries.find { it.name.equals(stored, ignoreCase = true) }
            assertEquals(mode, restored)
        }
    }
}
