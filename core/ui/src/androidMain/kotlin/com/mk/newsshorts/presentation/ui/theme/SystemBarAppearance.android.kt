package com.mk.newsshorts.presentation.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/**
 * Re-runs [enableEdgeToEdge] with the style the current screen needs. The call
 * is idempotent and meant to be repeated — androidx documents it as the way to
 * change bar appearance after start-up.
 *
 * It is preferred here over setting the appearance flags directly because
 * [SystemBarStyle.light] also carries the fallback for API 24-26, where the
 * navigation bar cannot draw dark icons at all: androidx quietly applies the
 * scrim on those devices instead of leaving white icons on a white bar.
 */
@Composable
actual fun SystemBarAppearance(useDarkIcons: Boolean) {
    val activity: ComponentActivity = LocalActivity.current as? ComponentActivity ?: return
    SideEffect {
        val style: SystemBarStyle = if (useDarkIcons) {
            SystemBarStyle.light(Color.TRANSPARENT, DARK_SCRIM)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

/** androidx's own default scrim, used only where dark icons are unavailable. */
private val DARK_SCRIM: Int = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
