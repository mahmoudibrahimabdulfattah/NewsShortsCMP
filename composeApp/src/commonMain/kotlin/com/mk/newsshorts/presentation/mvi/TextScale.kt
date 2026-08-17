package com.mk.newsshorts.presentation.mvi

/**
 * How large the reader wants text, as a multiplier on the type scale.
 *
 * Multiplies the app's own sizes rather than replacing the platform's
 * accessibility scaling: a reader who has already turned type up system-wide
 * gets that *and* this, which is the point — the OS setting says how they read
 * everything, this one says how they read a news app. Overriding the first
 * would quietly undo an accessibility choice they made deliberately.
 *
 * The range stops at 1.3. Past that the feed's headline no longer fits its
 * card at any line count worth reading, and a setting that breaks the screen it
 * is meant to help is not an accessibility feature.
 */
enum class TextScale(val multiplier: Float) {
    SMALL(0.88f),
    DEFAULT(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f);

    companion object {
        /** Unknown stored values fall back rather than throwing — see [ThemeMode]. */
        fun fromStored(value: String): TextScale =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }

    val stored: String get() = name.lowercase()
}
