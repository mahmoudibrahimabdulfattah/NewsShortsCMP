package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.runtime.Composable
import com.mk.newsshorts.presentation.mvi.ThemeMode

/**
 * Tells the platform which night-mode resources belong to this app.
 *
 * Everything the app draws itself already follows [ThemeMode] — but the very
 * first frame of a cold start is not drawn by the app. The system paints the
 * launch window from the activity's theme before any code runs, and it picks
 * between `values/` and `values-night/` using the *device's* dark-mode flag.
 * A reader who chooses Light on a phone that is set to Dark therefore gets a
 * dark launch window in front of a light app.
 *
 * Compose cannot fix this from inside, because by the time composition happens
 * the window has already been painted. The only lever is to tell the platform,
 * ahead of the next launch, that this app's night mode is the reader's choice
 * rather than the phone's — which is what this does.
 *
 * @param themeMode the reader's Appearance setting.
 */
@Composable
expect fun ApplyAppNightMode(themeMode: ThemeMode)
