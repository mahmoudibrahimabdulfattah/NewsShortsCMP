package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

/**
 * iOS has no navigation bar to tint, so only the clock and indicators move.
 *
 * This goes through the application rather than a view controller because the
 * whole app is one `ComposeUIViewController`: there is no second controller to
 * override `preferredStatusBarStyle` on when the reader moves between the feed
 * and Profile. `UIViewControllerBasedStatusBarAppearance` is set to false in
 * Info.plist so that this call is the one the system listens to.
 */
@Composable
actual fun SystemBarAppearance(useDarkIcons: Boolean) {
    SideEffect {
        val style: UIStatusBarStyle = if (useDarkIcons) {
            UIStatusBarStyleDarkContent
        } else {
            UIStatusBarStyleLightContent
        }
        UIApplication.sharedApplication.setStatusBarStyle(style, animated = true)
    }
}
