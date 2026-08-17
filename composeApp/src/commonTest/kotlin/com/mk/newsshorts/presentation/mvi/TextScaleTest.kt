package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.presentation.ui.components.feedSummaryMaxLines
import com.mk.newsshorts.presentation.ui.components.feedTitleMaxLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The feed card is one screen tall and cannot grow, so the largest text size
 * has to still fit inside it. These are the numbers that decide whether a
 * reading setting helps or breaks the screen it was meant to help.
 */
class TextScaleTest {

    @Test
    fun `an unknown stored value falls back rather than throwing`() {
        // A setting written by a later release, read by an older build.
        assertEquals(TextScale.DEFAULT, TextScale.fromStored("gigantic"))
        assertEquals(TextScale.DEFAULT, TextScale.fromStored(""))
    }

    @Test
    fun `every scale round-trips through storage`() {
        TextScale.entries.forEach { scale ->
            assertEquals(scale, TextScale.fromStored(scale.stored), "${scale.name} did not survive")
        }
    }

    @Test
    fun `the default scale changes nothing`() {
        assertEquals(1.0f, TextScale.DEFAULT.multiplier)
    }

    @Test
    fun `the scale rises with the setting`() {
        val multipliers = TextScale.entries.map { it.multiplier }

        assertEquals(multipliers.sorted(), multipliers, "the steps are out of order")
    }

    @Test
    fun `the feed gives up lines as the type grows`() {
        assertEquals(4, feedTitleMaxLines(TextScale.SMALL.multiplier))
        assertEquals(4, feedTitleMaxLines(TextScale.DEFAULT.multiplier))
        assertEquals(3, feedTitleMaxLines(TextScale.LARGE.multiplier))
        assertEquals(3, feedTitleMaxLines(TextScale.EXTRA_LARGE.multiplier))
    }

    @Test
    fun `the headline keeps a line longer than the summary`() {
        TextScale.entries.forEach { scale ->
            assertTrue(
                feedTitleMaxLines(scale.multiplier) > feedSummaryMaxLines(scale.multiplier),
                "at ${scale.name} the headline is not given more room than the summary",
            )
        }
    }

    @Test
    fun `the summary never disappears entirely`() {
        TextScale.entries.forEach { scale ->
            assertTrue(
                feedSummaryMaxLines(scale.multiplier) >= 2,
                "at ${scale.name} the summary is down to one line",
            )
        }
    }
}
