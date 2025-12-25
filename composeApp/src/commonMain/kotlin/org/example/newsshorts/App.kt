package org.example.newsshorts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import newsshorts.composeapp.generated.resources.Res
import newsshorts.composeapp.generated.resources.logo
import org.example.newsshorts.di.provideNewsViewModel
import org.example.newsshorts.presentation.localization.LocaleProvider
import org.example.newsshorts.presentation.mvi.NewsUiState
import org.example.newsshorts.presentation.ui.screen.NewsScreen
import org.example.newsshorts.presentation.ui.screen.SplashScreen
import org.example.newsshorts.presentation.ui.theme.NewsShortsTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val CROSSFADE_DURATION_MS: Int = 150

@Composable
@Preview
fun App(
    onOpenUrl: (String) -> Unit = {},
    onShareContent: (String, String) -> Unit = { _, _ -> },
    onShowToast: (String) -> Unit = {}
) {
    var showSplash: Boolean by remember { mutableStateOf(true) }
    val logoPainter: Painter = painterResource(Res.drawable.logo)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B)
                    )
                )
            )
    ) {
        Crossfade(
            targetState = showSplash,
            animationSpec = tween(CROSSFADE_DURATION_MS),
            label = "SplashTransition"
        ) { isSplashVisible: Boolean ->
            if (isSplashVisible) {
                SplashScreen(
                    logoPainter = logoPainter,
                    onSplashComplete = { showSplash = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                MainContent(
                    onOpenUrl = onOpenUrl,
                    onShareContent = onShareContent,
                    onShowToast = onShowToast
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    onOpenUrl: (String) -> Unit,
    onShareContent: (String, String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val viewModel = provideNewsViewModel()
    val uiState: NewsUiState by viewModel.uiState.collectAsState()
    LocaleProvider(locale = uiState.appLocale) {
        NewsShortsTheme(isDarkTheme = true) {
            NewsScreen(
                viewModel = viewModel,
                onOpenUrl = onOpenUrl,
                onShareContent = onShareContent,
                onShowToast = onShowToast,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
