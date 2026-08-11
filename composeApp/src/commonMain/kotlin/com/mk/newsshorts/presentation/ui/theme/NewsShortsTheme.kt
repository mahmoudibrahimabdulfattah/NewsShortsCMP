package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Distinctive color palette - Midnight Ocean theme
private val MidnightBlue: Color = Color(0xFF0D1B2A)
private val DeepNavy: Color = Color(0xFF1B263B)
private val SlateBlue: Color = Color(0xFF415A77)
private val SteelBlue: Color = Color(0xFF778DA9)
private val IvoryWhite: Color = Color(0xFFF0F4F8)
private val ElectricCoral: Color = Color(0xFFFF6B6B)
private val NeonMint: Color = Color(0xFF4ECDC4)
private val GoldenAmber: Color = Color(0xFFFFBE0B)
private val VividViolet: Color = Color(0xFF9B5DE5)
private val PureWhite: Color = Color(0xFFFFFFFF)
private val SoftGray: Color = Color(0xFFE8ECF0)
private val CharcoalGray: Color = Color(0xFF2D3436)

private val DarkColorScheme = darkColorScheme(
    primary = NeonMint,
    onPrimary = MidnightBlue,
    primaryContainer = DeepNavy,
    onPrimaryContainer = NeonMint,
    secondary = ElectricCoral,
    onSecondary = MidnightBlue,
    secondaryContainer = SlateBlue,
    onSecondaryContainer = IvoryWhite,
    tertiary = GoldenAmber,
    onTertiary = MidnightBlue,
    tertiaryContainer = DeepNavy,
    onTertiaryContainer = GoldenAmber,
    background = MidnightBlue,
    onBackground = IvoryWhite,
    surface = DeepNavy,
    onSurface = IvoryWhite,
    surfaceVariant = SlateBlue,
    onSurfaceVariant = SteelBlue,
    error = ElectricCoral,
    onError = MidnightBlue,
    outline = SteelBlue,
    outlineVariant = SlateBlue
)

private val LightColorScheme = lightColorScheme(
    primary = SlateBlue,
    onPrimary = PureWhite,
    primaryContainer = SteelBlue,
    onPrimaryContainer = MidnightBlue,
    secondary = ElectricCoral,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFFE5E5),
    onSecondaryContainer = MidnightBlue,
    tertiary = VividViolet,
    onTertiary = PureWhite,
    tertiaryContainer = Color(0xFFF0E5FF),
    onTertiaryContainer = MidnightBlue,
    background = IvoryWhite,
    onBackground = MidnightBlue,
    surface = PureWhite,
    onSurface = MidnightBlue,
    surfaceVariant = SoftGray,
    onSurfaceVariant = CharcoalGray,
    error = ElectricCoral,
    onError = PureWhite,
    outline = SteelBlue,
    outlineVariant = SoftGray
)

@Composable
fun NewsShortsTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NewsShortsTypography,
        content = content
    )
}

