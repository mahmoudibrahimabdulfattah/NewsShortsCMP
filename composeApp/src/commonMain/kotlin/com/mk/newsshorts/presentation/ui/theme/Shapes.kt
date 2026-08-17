package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * One corner scale for the whole app.
 *
 * Ten different radii were in use before this — buttons at 14 and 16, cards at
 * 0, 12 and 16, chips at 14, 16 and 24 — which meant two cards in the same list
 * could round differently. None of it was deliberate; the theme simply never
 * passed a [Shapes] and every call site picked its own.
 *
 *   extraSmall   8dp   tags, small pressable surfaces
 *   small       12dp   buttons, text fields
 *   medium      16dp   cards
 *   large       20dp   large containers
 *   extraLarge  28dp   sheets
 */
val NewsShortsShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Fully rounded, for chips and the nav indicator.
 *
 * Kept out of [NewsShortsShapes] because M3's five steps have no slot for it,
 * and a pill is a distinct idea rather than a sixth size.
 */
val PillShape: Shape = RoundedCornerShape(percent = 50)
