package com.mk.newsshorts

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
import com.mk.newsshorts.di.provideNewsViewModel
import com.mk.newsshorts.presentation.localization.LocaleProvider
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState
import com.mk.newsshorts.presentation.ui.screen.BlockingNoticeScreen
import com.mk.newsshorts.presentation.ui.screen.SecurityWarningDialog
import com.mk.newsshorts.security.SecurityNotice
import com.mk.newsshorts.security.SecurityReason
import com.mk.newsshorts.presentation.ui.screen.NewsScreen
import com.mk.newsshorts.presentation.ui.screen.SplashScreen
import com.mk.newsshorts.presentation.ui.theme.NewsShortsTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val CROSSFADE_DURATION_MS: Int = 150

@Composable
@Preview
fun App(
    onOpenUrl: (String) -> Unit = {},
    onShareContent: (String, String, String) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit = {}
) {
    var showSplash: Boolean by remember { mutableStateOf(true) }
    val logoPainter: Painter = painterResource(Res.drawable.logo)
    // The ViewModel is read here rather than inside MainContent so the splash
    // is inside LocaleProvider too — otherwise it always renders in English.
    val viewModel = provideNewsViewModel()
    val uiState: NewsUiState by viewModel.uiState.collectAsState()
    LocaleProvider(locale = uiState.appLocale) {
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
                val requiredUpdate = uiState.requiredUpdate
                val strings = appStrings()
                if (requiredUpdate != null) {
                    // Replaces the content rather than covering it: nothing
                    // underneath should keep running once the build is retired.
                    NewsShortsTheme(isDarkTheme = true) {
                        BlockingNoticeScreen(
                            icon = "⬆️",
                            title = strings.updateRequiredTitle,
                            message = strings.updateRequiredMessage,
                            actionLabel = strings.updateNow,
                            onAction = { onOpenUrl(requiredUpdate.storeUrl) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (uiState.securityNotice == SecurityNotice.BLOCKED) {
                    // No action: there is nothing the reader can tap that would
                    // make the device trustworthy, and a button that pretends
                    // otherwise would only teach them to distrust the message.
                    val isEnvironment = uiState.securityReason == SecurityReason.ENVIRONMENT
                    NewsShortsTheme(isDarkTheme = true) {
                        BlockingNoticeScreen(
                            icon = if (isEnvironment) "🛠️" else "🔒",
                            title = if (isEnvironment) {
                                strings.environmentBlockedTitle
                            } else {
                                strings.securityBlockedTitle
                            },
                            message = if (isEnvironment) {
                                strings.environmentBlockedMessage
                            } else {
                                strings.securityBlockedMessage
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (isSplashVisible) {
                    SplashScreen(
                        logoPainter = logoPainter,
                        onSplashComplete = { showSplash = false },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    NewsShortsTheme(isDarkTheme = true) {
                        NewsScreen(
                            viewModel = viewModel,
                            onOpenUrl = onOpenUrl,
                            onShareContent = onShareContent,
                            onShowToast = onShowToast,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Over the feed, not instead of it: this tier is a
                        // caution, and the reader is allowed to carry on.
                        if (uiState.securityNotice == SecurityNotice.WARNING) {
                            SecurityWarningDialog(
                                reason = uiState.securityReason,
                                onDismiss = {
                                    viewModel.processEvent(NewsUiEvent.DismissSecurityWarning)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
