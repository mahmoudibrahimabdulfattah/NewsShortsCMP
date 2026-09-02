package com.mk.newsshorts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import newsshorts.composeapp.generated.resources.Res
import newsshorts.composeapp.generated.resources.logo
import com.mk.newsshorts.di.provideAuthViewModel
import com.mk.newsshorts.di.provideNewsViewModel
import com.mk.newsshorts.di.provideSavedArticlesViewModel
import com.mk.newsshorts.di.provideSearchViewModel
import com.mk.newsshorts.di.provideSettingsViewModel
import com.mk.newsshorts.feature.auth.AuthUiEffect
import com.mk.newsshorts.feature.settings.SettingsUiState
import com.mk.newsshorts.presentation.localization.LocaleProvider
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState
import com.mk.newsshorts.presentation.mvi.Overlay
import com.mk.newsshorts.presentation.ui.screen.BlockingNoticeScreen
import com.mk.newsshorts.presentation.ui.screen.SecurityWarningDialog
import com.mk.newsshorts.security.SecurityNotice
import com.mk.newsshorts.security.SecurityReason
import com.mk.newsshorts.presentation.ui.screen.NewsScreen
import com.mk.newsshorts.presentation.ui.screen.OnboardingScreen
import com.mk.newsshorts.presentation.ui.screen.SplashScreen
import com.mk.newsshorts.presentation.ui.theme.ApplyAppNightMode
import com.mk.newsshorts.presentation.ui.theme.LocalTextScale
import com.mk.newsshorts.presentation.ui.theme.NewsShortsTheme
import com.mk.newsshorts.presentation.ui.theme.SystemBarAppearance
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val CROSSFADE_DURATION_MS: Int = 150

@Composable
@Preview
fun App(
    // Carries the resolved app theme with the URL: an in-app browser is a
    // separate process and cannot see the reader's Appearance setting, so it
    // has to be told. Passed here rather than plumbed through every screen —
    // the resolution only exists at this level.
    onOpenUrl: (String, Boolean) -> Unit = { _, _ -> },
    onShareContent: (String, String, String) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    var showSplash: Boolean by remember { mutableStateOf(true) }
    val logoPainter: Painter = painterResource(Res.drawable.logo)
    // The ViewModel is read here rather than inside MainContent so the splash
    // is inside LocaleProvider too — otherwise it always renders in English.
    val viewModel = provideNewsViewModel()
    val authViewModel = provideAuthViewModel()
    val searchViewModel = provideSearchViewModel()
    val savedArticlesViewModel = provideSavedArticlesViewModel()
    val settingsViewModel = provideSettingsViewModel()
    val uiState: NewsUiState by viewModel.uiState.collectAsState()
    val settingsUiState: SettingsUiState by settingsViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            authViewModel.uiEffect.collect { effect ->
                when (effect) {
                    AuthUiEffect.CloseOverlay -> {
                        viewModel.processEvent(NewsUiEvent.CloseOverlay)
                    }
                    AuthUiEffect.OpenSignInOverlay -> {
                        viewModel.processEvent(NewsUiEvent.OpenOverlay(Overlay.SignIn))
                    }
                }
            }
        }
        authViewModel.consumePendingSignInLink()
    }
    // Resolved once here rather than inside NewsShortsTheme's own default: the
    // feed branch inside NewsScreen overrides this with a forced-dark theme of
    // its own, so the resolution has to be visible at this level to differ
    // from what gets passed down.
    val isDarkTheme: Boolean = settingsUiState.themeMode.resolveIsDark(isSystemInDarkTheme())
    // Resolved here for the same reason, and in one place rather than per
    // screen: two of these branches are drawn by a Crossfade, and rival
    // SideEffects would fight over the bars for the length of the animation.
    val barsUseDarkIcons: Boolean = when {
        // Splash and the two blocking screens are branded full-bleed dark.
        showSplash -> false
        uiState.requiredUpdate != null -> false
        uiState.securityNotice == SecurityNotice.BLOCKED -> false
        // Details, Settings, Saved and Search all paint colorScheme.background.
        uiState.overlays.isNotEmpty() -> !isDarkTheme
        uiState.currentTab == NavigationTab.PROFILE -> !isDarkTheme
        // What is left is the feed, which is forced dark whatever the setting.
        else -> false
    }
    SystemBarAppearance(useDarkIcons = barsUseDarkIcons)
    // Covers the one frame the two lines above cannot: the launch window the
    // system paints from `values-night` before the app is running.
    ApplyAppNightMode(themeMode = settingsUiState.themeMode)
    // Screens below take a plain (String) -> Unit; the theme is bound here so
    // none of them has to carry it. Deliberately `isDarkTheme` and not the
    // per-screen theme: the feed is forced dark, but a reader who chose Light
    // should still get a light browser when they open an article from it.
    val openUrl: (String) -> Unit = { url -> onOpenUrl(url, isDarkTheme) }
    // Provided outside every theme so the feed's own forced-dark theme picks
    // the reader's text size up too.
    CompositionLocalProvider(LocalTextScale provides settingsUiState.textScale.multiplier) {
    LocaleProvider(locale = settingsUiState.appLocale) {
        // The one place the resolved app theme is applied. The two blocking
        // screens below override it back to forced-dark — full-bleed branded
        // moments, not content — and so does the feed inside NewsScreen.
        // Everything else (Profile, Settings, Saved, the details screen)
        // inherits this.
        NewsShortsTheme(isDarkTheme = isDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                            onAction = { openUrl(requiredUpdate.storeUrl) },
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
                } else if (uiState.onboarding != null) {
                    // After the splash, before the feed. The feed is already
                    // loading behind this, and the category chosen here decides
                    // which feed that should have been — see finishOnboarding.
                    OnboardingScreen(
                        uiState = uiState,
                        settingsUiState = settingsUiState,
                        onEvent = viewModel::processEvent,
                        onSettingsEvent = { event -> settingsViewModel.processEvent(event) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    NewsScreen(
                        viewModel = viewModel,
                        authViewModel = authViewModel,
                        searchViewModel = searchViewModel,
                        savedArticlesViewModel = savedArticlesViewModel,
                        settingsViewModel = settingsViewModel,
                        onOpenUrl = openUrl,
                        onShareContent = onShareContent,
                        onShowToast = onShowToast,
                        onRequestNotificationPermission = onRequestNotificationPermission,
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
}
