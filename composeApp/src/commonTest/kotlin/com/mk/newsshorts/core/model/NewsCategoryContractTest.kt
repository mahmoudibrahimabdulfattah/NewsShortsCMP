package com.mk.newsshorts.core.model

import com.mk.newsshorts.core.contract.feed.NewsCategories
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsCategoryContractTest {

    /**
     * Compares sets rather than lists because the server's linked set drives
     * feed iteration order, while the enum declaration drives the reader's tab
     * order. They must share membership without forcing either order onto the
     * other.
     */
    @Test
    fun `api values match the feed contract categories`() {
        assertEquals(
            NewsCategories.all,
            NewsCategory.entries.map { it.apiValue }.toSet(),
        )
    }
}
