package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mk.newsshorts.presentation.localization.appLocale

/*
 * The palette has exactly two seeds, both taken from the logo: the azure of the
 * mark and the crimson of its underline. Everything else is those two hues at
 * other luminances, plus a neutral ramp tinted toward the azure so the greys
 * read as part of the brand rather than as something sitting next to it.
 *
 * Two colours rather than four is the whole point. The palette this replaced
 * carried mint, coral, amber and violet at once, none of which appeared in the
 * logo — four accents with no common origin, which is what makes a screen look
 * assembled rather than designed.
 *
 * Role policy, which the screens are expected to honour:
 *
 *   primary    azure         navigation, selection, primary actions, links
 *   secondary  azure-slate   supporting chrome, icon tiles, placeholders
 *   tertiary   crimson       saved state, breaking/live — rationed, under ~10%
 *   error      deep crimson  destructive actions and failures, nothing else
 *
 * The old scheme had secondary and error set to the same coral, so every
 * decorative flourish in the app looked like a warning. Keeping crimson scarce
 * is what lets it still mean something when it does appear.
 *
 * Every foreground/background pair here was measured, not eyeballed: body text
 * clears 4.5:1 and boundaries clear 3:1. Figures are in DESIGN.md.
 */

// -- Brand seeds ------------------------------------------------------------

/** The logo's azure, straight from the mark. */
private val Azure: Color = Color(0xFF005291)

/** The same hue lifted for dark surfaces — the logo azure fails there. */
private val AzureLift: Color = Color(0xFF5FAAE8)

/** The logo's crimson, darkened just enough to carry white text on white. */
private val Crimson: Color = Color(0xFFC8003C)

/** The crimson lifted for dark surfaces. */
private val CrimsonLift: Color = Color(0xFFFF4D74)

// -- Light neutrals, tinted toward azure ------------------------------------

private val LightBackground: Color = Color(0xFFF5F8FB)
private val LightSurface: Color = Color(0xFFFFFFFF)
private val LightSurfaceVariant: Color = Color(0xFFDFE8F1)
private val LightSurfaceContainer: Color = Color(0xFFE9EFF6)
private val LightInk: Color = Color(0xFF0B1D2E)
private val LightInkMuted: Color = Color(0xFF4E6379)
private val LightOutline: Color = Color(0xFF6E8598)
private val LightOutlineVariant: Color = Color(0xFFD3DEE9)

// -- Dark neutrals, same hue family -----------------------------------------

private val DarkBackground: Color = Color(0xFF081726)
private val DarkSurface: Color = Color(0xFF0F2234)
private val DarkSurfaceVariant: Color = Color(0xFF1B3247)
private val DarkSurfaceContainer: Color = Color(0xFF152B41)
private val DarkInk: Color = Color(0xFFE6EEF7)
private val DarkInkMuted: Color = Color(0xFFA0B6CA)
private val DarkOutline: Color = Color(0xFF6B8299)
private val DarkOutlineVariant: Color = Color(0xFF24405A)

private val PureWhite: Color = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = Azure,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFCFE2F5),
    onPrimaryContainer = Color(0xFF00304F),
    secondary = Color(0xFF4A6480),
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFD5E1EE),
    onSecondaryContainer = Color(0xFF152C40),
    tertiary = Crimson,
    onTertiary = PureWhite,
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF5C0019),
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    surfaceContainer = LightSurfaceContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFA00020),
    onError = PureWhite,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410007),
    scrim = ImageryScrim,
)

private val DarkColorScheme = darkColorScheme(
    primary = AzureLift,
    onPrimary = Color(0xFF00243F),
    primaryContainer = Color(0xFF00456F),
    onPrimaryContainer = Color(0xFFCFE2F5),
    secondary = Color(0xFFA9C2DC),
    onSecondary = Color(0xFF152C40),
    secondaryContainer = Color(0xFF32485F),
    onSecondaryContainer = Color(0xFFD5E1EE),
    tertiary = CrimsonLift,
    onTertiary = Color(0xFF4A0014),
    tertiaryContainer = Color(0xFF8C0028),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainer = DarkSurfaceContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF5C0019),
    errorContainer = Color(0xFF8C0014),
    onErrorContainer = Color(0xFFFFDAD9),
    scrim = ImageryScrim,
)

/**
 * Which of the two schemes is in force.
 *
 * Some choices cannot be expressed as a single colour role because the right
 * answer genuinely differs by surface — see the selected chip in `FilterPill`.
 * Reading this is honest about that; inferring it from a colour's luminance
 * would be guessing at the same thing less legibly.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun NewsShortsTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme
    // Typography follows the reading language, not the platform: Arabic and
    // Latin get different families, leading and tracking. Every caller sits
    // inside LocaleProvider, so the locale is always here to read.
    CompositionLocalProvider(LocalIsDarkTheme provides isDarkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = newsShortsTypography(appLocale()),
            shapes = NewsShortsShapes,
            content = content
        )
    }
}
