package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.runtime.Composable

/**
 * Tells the platform which way to tint the status and navigation bar icons.
 *
 * The app cannot leave this to the operating system. Android decides icon
 * contrast from its own night-mode flag, but a reader who picks Light while the
 * phone is in Dark gets white icons on a near-white screen. The theme the app
 * actually drew is the only thing that knows the answer, and it is not always
 * the app theme either: the feed, the splash and the blocking screens are drawn
 * dark whatever the setting says.
 *
 * @param useDarkIcons true when the bars sit over a light surface.
 */
@Composable
expect fun SystemBarAppearance(useDarkIcons: Boolean)
