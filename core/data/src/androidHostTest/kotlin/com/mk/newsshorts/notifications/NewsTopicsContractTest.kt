package com.mk.newsshorts.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NewsTopicsContractTest {
    @Test
    fun `topic preference keys stay stable`() {
        assertEquals("news_topics", NewsTopics.PREFS)
        assertEquals("subscribed_language", NewsTopics.KEY_LANGUAGE)
    }
}
