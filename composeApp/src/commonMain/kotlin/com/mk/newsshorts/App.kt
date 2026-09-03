package com.mk.newsshorts

import com.mk.newsshorts.presentation.viewmodel.AppShellUiEvent
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.mk.newsshorts.di.provideAppGateViewModel
import com.mk.newsshorts.di.provideAppShellViewModel
import com.mk.newsshorts.di.provideAuthViewModel
import com.mk.newsshorts.di.provideInboxViewModel
import com.mk.newsshorts.di.provideFeedViewModel
import com.mk.newsshorts.di.provideOnboardingViewModel
import com.mk.newsshorts.di.provideSavedArticlesViewModel
import com.mk.newsshorts.di.provideSearchViewModel
import com.mk.newsshorts.di.provideSettingsViewModel
import com.mk.newsshorts.feature.auth.AuthUiEffect
import com.mk.newsshorts.feature.settings.SettingsUiState
import com.mk.newsshorts.presentation.localization.LocaleProvider
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.feature.feed.FeedUiState
import com.mk.newsshorts.presentation.mvi.Overlay
import com.mk.newsshorts.feature.appgate.AppGateUiEvent
import com.mk.newsshorts.feature.appgate.BlockingNoticeScreen
import com.mk.newsshorts.feature.appgate.SecurityWarningDialog
import com.mk.newsshorts.core.model.security.SecurityNotice
import com.mk.newsshorts.core.model.security.SecurityReason
import com.mk.newsshorts.presentation.ui.screen.NewsScreen
import com.mk.newsshorts.feature.onboarding.OnboardingScreen
import com.mk.newsshorts.feature.onboarding.OnboardingUiEffect
import com.mk.newsshorts.presentation.ui.screen.SplashScreen
import com.mk.newsshorts.presentation.ui.theme.ApplyAppNightMode
import com.mk.newsshorts.presentation.ui.theme.LocalTextScale
import com.mk.newsshorts.presentation.ui.theme.NewsShortsTheme
import com.mk.newsshorts.presentation.ui.theme.SystemBarAppearance
import com.mk.newsshorts.presentation.ui.theme.appLogoPainter
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
    val logoPainter: Painter = appLogoPainter()
    // The ViewModel is read here rather than inside MainContent so the splash
    // is inside LocaleProvider too — otherwise it always renders in English.
    val viewModel = provideFeedViewModel()
    val authViewModel = provideAuthViewModel()
    val inboxViewModel = provideInboxViewModel()
    val searchViewModel = provideSearchViewModel()
    val savedArticlesViewModel = provideSavedArticlesViewModel()
    val settingsViewModel = provideSettingsViewModel()
    val appGateViewModel = provideAppGateViewModel()
    val onboardingViewModel = provideOnboardingViewModel()
    val shellViewModel = provideAppShellViewModel()
    val uiState: FeedUiState by viewModel.uiState.collectAsState()
    val settingsUiState: SettingsUiState by settingsViewModel.uiState.collectAsState()
    val gateUiState by appGateViewModel.uiState.collectAsState()
    val onboardingUiState by onboardingViewModel.uiState.collectAsState()
    val shellUiState by shellViewModel.uiState.collectAsState()
    // Onboarding asks for the notification permission only when the reader
    // pressed through the last step with notifications on. Collected here
    // rather than in NewsScreen because onboarding is drawn above it, and by
    // the time the request is made the screen below has not been composed.
    val requestNotificationPermission by rememberUpdatedState(onRequestNotificationPermission)
    LaunchedEffect(Unit) {
        onboardingViewModel.uiEffect.collect { effect ->
            when (effect) {
                OnboardingUiEffect.RequestNotificationPermission ->
                    requestNotificationPermission()
            }
        }
    }
    LaunchedEffect(Unit) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            authViewModel.uiEffect.collect { effect ->
                when (effect) {
                    AuthUiEffect.CloseOverlay -> {
                        shellViewModel.processEvent(AppShellUiEvent.CloseOverlay)
                    }
                    AuthUiEffect.OpenSignInOverlay -> {
                        shellViewModel.processEvent(AppShellUiEvent.OpenOverlay(Overlay.SignIn))
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
        gateUiState.requiredUpdate != null -> false
        gateUiState.securityNotice == SecurityNotice.BLOCKED -> false
        // Details, Settings, Saved and Search all paint colorScheme.background.
        shellUiState.overlays.isNotEmpty() -> !isDarkTheme
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
                val requiredUpdate = gateUiState.requiredUpdate
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
                } else if (gateUiState.securityNotice == SecurityNotice.BLOCKED) {
                    // No action: there is nothing the reader can tap that would
                    // make the device trustworthy, and a button that pretends
                    // otherwise would only teach them to distrust the message.
                    val isEnvironment = gateUiState.securityReason == SecurityReason.ENVIRONMENT
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
                } else if (onboardingUiState.isShowing) {
                    // After the splash, before the feed. The feed is already
                    // loading behind this, and the category chosen here decides
                    // which feed that should have been — see finishOnboarding.
                    OnboardingScreen(
                        uiState = onboardingUiState,
                        settingsUiState = settingsUiState,
                        onEvent = onboardingViewModel::processEvent,
                        onSettingsEvent = { event -> settingsViewModel.processEvent(event) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    NewsScreen(
                        viewModel = viewModel,
                        shellViewModel = shellViewModel,
                        authViewModel = authViewModel,
                        inboxViewModel = inboxViewModel,
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
                    if (gateUiState.securityNotice == SecurityNotice.WARNING) {
                        SecurityWarningDialog(
                            reason = gateUiState.securityReason,
                            onDismiss = {
                                appGateViewModel.processEvent(
                                    AppGateUiEvent.DismissSecurityWarning
                                )
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
