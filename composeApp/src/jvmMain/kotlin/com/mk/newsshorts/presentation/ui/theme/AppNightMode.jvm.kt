package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.runtime.Composable
import com.mk.newsshorts.presentation.mvi.ThemeMode

/** No-op: only Android paints a launch window from its own night-mode flag. */
@Composable
actual fun ApplyAppNightMode(themeMode: ThemeMode) = Unit
