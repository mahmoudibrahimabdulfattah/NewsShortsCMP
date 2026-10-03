package com.mk.newsshorts.core.data.local

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSettingsStorageContractTest {
    @Test
    fun `shared preferences file name stays stable`() {
        assertEquals("news_shorts_prefs", AndroidSettingsStorage.PREFS_NAME)
    }
}
