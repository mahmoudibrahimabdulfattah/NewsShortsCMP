package com.mk.newsshorts.core.data.local

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaPrefsSettingsStorageTest {
    @Test
    fun `preference node paths stay stable`() {
        assertEquals("com/mk/newsshorts", JAVA_PREFS_STABLE_NODE_PATH)
        assertEquals(
            listOf(
                "com/mk/newsshorts/core/data/local",
                "com/mk/newsshorts/data/local",
            ),
            JAVA_PREFS_HISTORICAL_NODE_PATHS,
        )
    }

    @Test
    fun `empty target copies historical nodes newest first`() {
        withTemporaryRoot { root ->
            root.node("newest").apply {
                put("theme_mode", "dark")
                put("news_language", "ar")
            }
            root.node("older").apply {
                put("theme_mode", "light")
                put("selected_country", "eg")
            }
            val target = root.node("target")

            copyHistoricalNodesIfTargetEmpty(target, root, listOf("newest", "older"))

            assertEquals("dark", target.get("theme_mode", ""))
            assertEquals("ar", target.get("news_language", ""))
            assertEquals("eg", target.get("selected_country", ""))
        }
    }

    @Test
    fun `non-empty target skips historical copy`() {
        withTemporaryRoot { root ->
            val target = root.node("target").apply {
                put("theme_mode", "system")
            }
            root.node("newest").apply {
                put("theme_mode", "dark")
                put("news_language", "ar")
            }

            copyHistoricalNodesIfTargetEmpty(target, root, listOf("newest"))

            assertEquals("system", target.get("theme_mode", ""))
            assertEquals("", target.get("news_language", ""))
        }
    }

    private fun withTemporaryRoot(block: (Preferences) -> Unit) {
        val parent = Preferences.userRoot().node("com/mk/newsshorts/test")
        val root = parent.node(UUID.randomUUID().toString())
        try {
            block(root)
        } finally {
            root.removeNode()
            parent.flush()
        }
    }
}
