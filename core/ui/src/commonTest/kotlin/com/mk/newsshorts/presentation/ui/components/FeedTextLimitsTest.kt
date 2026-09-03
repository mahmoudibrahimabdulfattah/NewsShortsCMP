package com.mk.newsshorts.presentation.ui.components

import com.mk.newsshorts.core.model.settings.TextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The feed card is one screen tall and cannot grow, so the largest text size
 * has to still fit inside it. These are the numbers that decide whether a
 * reading setting helps or breaks the screen it was meant to help.
 */
class FeedTextLimitsTest {

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
