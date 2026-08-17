package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The near-black the feed's scrims are built from.
 *
 * Deliberately not [Color.Black]: it carries the same azure cast as the rest of
 * the dark neutrals, so the feed reads as this app's own dark surface instead of
 * an absence of one. Also the colorScheme `scrim` role in both schemes.
 */
internal val ImageryScrim: Color = Color(0xFF03121F)

/**
 * The third surface.
 *
 * The feed is neither the light nor the dark theme — it is text laid directly
 * over photographs, which is why it stays dark whatever the reader has chosen.
 * That was previously expressed as roughly thirty-five white-and-black literals
 * scattered across the card, the header, the chips and the nav bar, which meant
 * the feed had a palette nobody had written down. This is that palette, written
 * down.
 *
 * Alphas rather than solid colours throughout: the photograph has to stay
 * visible through the chrome, and a fixed grey would flatten against a dark
 * image and glare against a bright one.
 */
object OnImagery {

    /** Headlines and anything that must be read at a glance. */
    val content: Color = Color.White

    /** Supporting text — source, timestamp, the header subtitle. */
    val contentMuted: Color = Color.White.copy(alpha = 0.72f)

    /** Text that should recede until looked for. */
    val contentFaint: Color = Color.White.copy(alpha = 0.55f)

    /** Resting fill for chips and secondary controls. */
    val fill: Color = Color.White.copy(alpha = 0.10f)

    /**
     * Fill for controls that read as buttons — share, save.
     *
     * Nearly opaque, and dark rather than white. As a white wash it inherited
     * whatever was behind it, so a control over a snow-bright photograph became
     * pale-on-pale: a white icon on it measured 1.99:1. Painting the chip dark
     * instead means the icon on top has a known background whatever the picture
     * does, which is the only way a coloured state on it can be legible.
     */
    val fillStrong: Color = ImageryScrim.copy(alpha = 0.80f)

    /**
     * The saved-bookmark tint, and the only crimson on a feed card.
     *
     * The same value the dark scheme uses for `tertiary`, so a saved article is
     * the identical crimson as a selected chip rather than a second, paler red.
     * Against [fillStrong] that is 5.86:1 on a typical photograph and 3.76:1 on
     * a near-white one — above the 3:1 an icon needs in the worst case, and the
     * reason [fillStrong] had to stop being a translucent white wash.
     */
    val savedTint: Color = Color(0xFFFF4D74)

    /** Hairline around unselected chips. */
    val border: Color = Color.White.copy(alpha = 0.22f)

    /**
     * Selected-chip fill: the logo's crimson.
     *
     * Azure was the first choice and it was the weaker one. Over a photograph
     * the chrome is already blue-grey, so a blue chip announced very little —
     * the reader had to hunt for which filter was active. Crimson is the only
     * other colour in the mark, it appears nowhere else on the feed, and it
     * separates from any photograph. Slightly lighter than the light theme's
     * crimson so it holds up against a dimmed image; white on it is 4.81:1.
     */
    val selectedFill: Color = Color(0xFFE4003D)

    /** Text and icons on [selectedFill]. */
    val onSelectedFill: Color = Color.White

    /**
     * Behind the masthead and the chip rows. Opaque at the top so the status
     * bar icons stay readable, gone by the time it reaches the headline.
     */
    val topScrim: Brush = Brush.verticalGradient(
        colors = listOf(
            ImageryScrim.copy(alpha = 0.90f),
            ImageryScrim.copy(alpha = 0.70f),
            ImageryScrim.copy(alpha = 0.40f),
            Color.Transparent,
        )
    )

    /**
     * Behind the headline, the summary and the actions. Reaches further and
     * ends heavier than the top: this end carries several lines of body text,
     * not one line of chrome.
     */
    val bottomScrim: Brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            ImageryScrim.copy(alpha = 0.30f),
            ImageryScrim.copy(alpha = 0.70f),
            ImageryScrim.copy(alpha = 0.95f),
        )
    )

    /** Behind the bottom navigation bar where it floats over the feed. */
    val navScrim: Brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            ImageryScrim.copy(alpha = 0.70f),
            ImageryScrim.copy(alpha = 0.95f),
        )
    )
}

/**
 * The full-bleed backdrop behind the splash and the blocking screens.
 *
 * Those are branded moments rather than content, so they stay dark in either
 * theme — but they used to say so by repeating `0xFF0D1B2A` and `0xFF1B263B` as
 * literals in two separate files, which is how they drifted from the palette in
 * the first place. Reading the scheme means they cannot drift again.
 */
@Composable
fun brandBackdrop(): Brush = Brush.verticalGradient(
    colors = listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surface,
    )
)
