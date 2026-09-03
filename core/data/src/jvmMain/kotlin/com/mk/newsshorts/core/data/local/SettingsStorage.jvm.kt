package com.mk.newsshorts.core.data.local

import java.util.prefs.Preferences

class JavaPrefsSettingsStorage : SettingsStorage {
    // This literal is a storage location, not a code location. Deriving it
    // from this class's package silently rehomes every setting whenever the
    // package moves.
    private val preferences: Preferences = Preferences.userRoot().node(JAVA_PREFS_STABLE_NODE_PATH)

    init {
        copyHistoricalNodesIfTargetEmpty(
            target = preferences,
            root = Preferences.userRoot(),
            historicalPaths = JAVA_PREFS_HISTORICAL_NODE_PATHS,
        )
    }

    override fun getString(key: String, defaultValue: String): String {
        return preferences.get(key, defaultValue)
    }

    override fun putString(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }
}

internal const val JAVA_PREFS_STABLE_NODE_PATH: String = "com/mk/newsshorts"

// Current core.data.local package-derived node first, then the pre-phase-3
// data.local package-derived node.
internal val JAVA_PREFS_HISTORICAL_NODE_PATHS: List<String> = listOf(
    "com/mk/newsshorts/core/data/local",
    "com/mk/newsshorts/data/local",
)

internal fun copyHistoricalNodesIfTargetEmpty(
    target: Preferences,
    root: Preferences,
    historicalPaths: List<String>,
) {
    runCatching {
        if (target.keys().isNotEmpty()) return
        historicalPaths.forEach { path ->
            if (!root.nodeExists(path)) return@forEach
            val historical = root.node(path)
            historical.keys().forEach { key ->
                if (target.get(key, null) == null) {
                    historical.get(key, null)?.let { value -> target.put(key, value) }
                }
            }
        }
        target.flush()
    }
}
