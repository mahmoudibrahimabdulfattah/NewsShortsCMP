package com.mk.newsshorts.core.model.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class TextScaleTest {

    @Test
    fun `an unknown stored value falls back rather than throwing`() {
        // A setting written by a later release, read by an older build.
        assertEquals(TextScale.DEFAULT, TextScale.fromStored("gigantic"))
        assertEquals(TextScale.DEFAULT, TextScale.fromStored(""))
    }

    @Test
    fun `persisted values stay stable`() {
        assertEquals("small", TextScale.SMALL.stored)
        assertEquals("default", TextScale.DEFAULT.stored)
        assertEquals("large", TextScale.LARGE.stored)
        assertEquals("extra_large", TextScale.EXTRA_LARGE.stored)
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
}
