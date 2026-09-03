package com.mk.newsshorts.core.domain.preferences

import com.mk.newsshorts.core.model.NewsCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a reader picked at onboarding has to survive into the feed, or the step
 * was a screen that changed nothing. The rule worth protecting is that picking
 * *promotes* and never *removes*: an install-time choice must not be able to
 * make part of the app unreachable months later.
 */
class CategoryPreferencesTest {

    @Test
    fun `picks come first in the order they were picked`() {
        val ordered = orderedCategories(listOf("sports", "business"))

        assertEquals(
            listOf(NewsCategory.SPORTS, NewsCategory.BUSINESS),
            ordered.take(2),
        )
    }

    @Test
    fun `nothing is ever dropped`() {
        val ordered = orderedCategories(listOf("health"))

        assertEquals(NewsCategory.entries.size, ordered.size)
        assertTrue(ordered.containsAll(NewsCategory.entries))
    }

    @Test
    fun `no category appears twice`() {
        val ordered = orderedCategories(listOf("sports", "sports", "science"))

        assertEquals(ordered.size, ordered.toSet().size)
    }

    @Test
    fun `skipping onboarding leaves the declared order`() {
        assertEquals(NewsCategory.entries, orderedCategories(emptyList()))
    }

    @Test
    fun `a stored value that no longer names a category is ignored`() {
        // A category removed in a later release is still sitting in the
        // settings of everyone who picked it.
        val ordered = orderedCategories(listOf("crypto", "sports"))

        assertEquals(NewsCategory.SPORTS, ordered.first())
        assertEquals(NewsCategory.entries.size, ordered.size)
    }

    @Test
    fun `the feed opens on the first pick`() {
        assertEquals(NewsCategory.SCIENCE, openingCategory(listOf("science", "sports")))
    }

    @Test
    fun `the feed opens on general when nothing was picked`() {
        assertEquals(NewsCategory.GENERAL, openingCategory(emptyList()))
    }
}
