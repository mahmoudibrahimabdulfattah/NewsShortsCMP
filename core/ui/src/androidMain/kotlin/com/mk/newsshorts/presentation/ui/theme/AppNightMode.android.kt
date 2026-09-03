package com.mk.newsshorts.presentation.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mk.newsshorts.core.model.settings.ThemeMode

/**
 * Pins the app's night mode with `UiModeManager`, the framework's own answer to
 * "this app has its own dark-mode switch". The value survives the process, so
 * the system honours it when it paints the launch window next time — which is
 * the whole point, since that window is drawn before the app can say anything.
 *
 * Requires API 31. Below it there is no per-app night mode and no system splash
 * screen either: the launch window is just `windowBackground`, so a reader whose
 * app and phone disagree sees one wrong-coloured frame before Compose paints.
 */
@Composable
actual fun ApplyAppNightMode(themeMode: ThemeMode) {
    val context: Context = LocalContext.current
    // The first value is always the SYSTEM default, not the reader's choice:
    // FeedUiState starts there and only becomes the saved setting once
    // loadSavedSettings returns. Pinning it would unpin a saved Light or Dark
    // on every single launch — the bug this function exists to prevent.
    var sawInitialValue: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(themeMode) {
        if (!sawInitialValue) {
            sawInitialValue = true
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        val uiModeManager: UiModeManager =
            context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                ?: return@LaunchedEffect
        // MODE_NIGHT_AUTO is the only unpinned value UiModeManager offers, so
        // it is what SYSTEM means here: hand the decision back to the phone.
        uiModeManager.setApplicationNightMode(
            when (themeMode) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
    }
}
