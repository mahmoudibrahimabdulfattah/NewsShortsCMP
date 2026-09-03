package com.mk.newsshorts.core.model.settings

/**
 * SYSTEM follows the device; LIGHT/DARK are an explicit override. Applies to
 * every screen except the vertical feed itself, which stays dark regardless -
 * its text sits on full-bleed photos, not on a themed surface.
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun resolveIsDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}
