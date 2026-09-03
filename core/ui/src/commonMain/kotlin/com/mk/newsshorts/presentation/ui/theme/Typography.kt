package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.core.ui.resources.Res
import com.mk.newsshorts.core.ui.resources.poppins_bold
import com.mk.newsshorts.core.ui.resources.poppins_light
import com.mk.newsshorts.core.ui.resources.poppins_medium
import com.mk.newsshorts.core.ui.resources.poppins_regular
import com.mk.newsshorts.core.ui.resources.poppins_semibold
import com.mk.newsshorts.core.ui.resources.poppins_thin
import com.mk.newsshorts.core.ui.resources.tajawal_bold
import com.mk.newsshorts.core.ui.resources.tajawal_extrabold
import com.mk.newsshorts.core.ui.resources.tajawal_light
import com.mk.newsshorts.core.ui.resources.tajawal_medium
import com.mk.newsshorts.core.ui.resources.tajawal_regular
import org.jetbrains.compose.resources.Font

/*
 * One family per script: Tajawal for Arabic, Poppins for Latin, chosen from the
 * reader's app language rather than per glyph. Both are bundled, so a headline
 * weighs the same on Android, iOS and desktop — before this every style asked
 * for FontFamily.Default, which meant the platform chose, and the Black and
 * ExtraBold steps often had no matching face to choose at all.
 *
 * Two things differ for Arabic beyond the family, and neither is cosmetic:
 *
 * Tracking is forced to zero. Arabic is a connected script, and positive
 * letter-spacing pulls joined letters apart — the M3 scale ships tracking on
 * nine of its fifteen styles because it is designed around Latin.
 *
 * Line height is opened up. Arabic carries dots and diacritics above and below
 * the baseline that Latin metrics leave no room for, so lines set to Latin
 * leading collide.
 */

/*
 * Arabic needs more leading than the Latin-derived M3 scale allows, and it needs
 * it unevenly: small text is where lines actually collide, because the dots and
 * diacritics stay roughly the same size while the leading shrinks with the font.
 * A display line at 57sp has room to spare at 1.1; a 12sp label does not.
 */
private const val ARABIC_LEADING_DISPLAY: Float = 1.12f
private const val ARABIC_LEADING_TITLE: Float = 1.22f
private const val ARABIC_LEADING_BODY: Float = 1.40f

@Composable
private fun poppins(): FontFamily = FontFamily(
    Font(Res.font.poppins_thin, weight = FontWeight.Thin),
    Font(Res.font.poppins_light, weight = FontWeight.Light),
    Font(Res.font.poppins_regular, weight = FontWeight.Normal),
    Font(Res.font.poppins_medium, weight = FontWeight.Medium),
    Font(Res.font.poppins_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.poppins_bold, weight = FontWeight.Bold),
)

@Composable
private fun tajawal(): FontFamily = FontFamily(
    Font(Res.font.tajawal_light, weight = FontWeight.Light),
    Font(Res.font.tajawal_regular, weight = FontWeight.Normal),
    Font(Res.font.tajawal_medium, weight = FontWeight.Medium),
    Font(Res.font.tajawal_bold, weight = FontWeight.Bold),
    Font(Res.font.tajawal_extrabold, weight = FontWeight.ExtraBold),
)

/**
 * The type scale for a given reading language.
 *
 * Styles still name weights the families do not both carry — Poppins stops at
 * Bold, Tajawal at ExtraBold — and that is fine: Compose resolves to the
 * nearest registered face, so each family lands on its own heaviest cut rather
 * than being synthetically smeared into one.
 */
@Composable
fun newsShortsTypography(locale: AppLocale, scale: Float = 1f): Typography {
    val isArabic: Boolean = locale.isRtl
    val family: FontFamily = if (isArabic) tajawal() else poppins()

    // Everything in the style scales together — size, leading and tracking —
    // so a larger setting is the same typography at another size rather than
    // the same lines with bigger letters crammed into them. Sizes stay in sp,
    // so the platform's own accessibility scale still applies underneath.
    fun style(
        weight: FontWeight,
        size: TextUnit,
        lineHeight: TextUnit,
        tracking: TextUnit,
        leading: Float,
    ): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size * scale,
        lineHeight = (if (isArabic) lineHeight * leading else lineHeight) * scale,
        letterSpacing = if (isArabic) 0.sp else tracking * scale,
    )

    val display = ARABIC_LEADING_DISPLAY
    val title = ARABIC_LEADING_TITLE
    val body = ARABIC_LEADING_BODY

    return Typography(
        displayLarge = style(FontWeight.Black, 57.sp, 64.sp, (-0.25).sp, display),
        displayMedium = style(FontWeight.Bold, 45.sp, 52.sp, 0.sp, display),
        displaySmall = style(FontWeight.Bold, 36.sp, 44.sp, 0.sp, display),
        headlineLarge = style(FontWeight.ExtraBold, 32.sp, 40.sp, 0.sp, display),
        headlineMedium = style(FontWeight.Bold, 28.sp, 36.sp, 0.sp, title),
        headlineSmall = style(FontWeight.SemiBold, 24.sp, 32.sp, 0.sp, title),
        titleLarge = style(FontWeight.Bold, 22.sp, 28.sp, 0.sp, title),
        titleMedium = style(FontWeight.SemiBold, 16.sp, 24.sp, 0.15.sp, title),
        titleSmall = style(FontWeight.Medium, 14.sp, 20.sp, 0.1.sp, body),
        bodyLarge = style(FontWeight.Normal, 16.sp, 24.sp, 0.5.sp, body),
        bodyMedium = style(FontWeight.Normal, 14.sp, 20.sp, 0.25.sp, body),
        bodySmall = style(FontWeight.Normal, 12.sp, 16.sp, 0.4.sp, body),
        labelLarge = style(FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp, body),
        labelMedium = style(FontWeight.Medium, 12.sp, 16.sp, 0.5.sp, body),
        labelSmall = style(FontWeight.Medium, 11.sp, 16.sp, 0.5.sp, body),
    )
}
