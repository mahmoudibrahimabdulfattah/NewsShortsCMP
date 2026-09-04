package com.mk.newsshorts.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.mk.newsshorts.core.model.article.ArticleOpenOrigin
import com.mk.newsshorts.feature.auth.AuthUiEvent
import com.mk.newsshorts.feature.auth.AuthUiState
import com.mk.newsshorts.feature.auth.SignInScreen
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.feature.feed.FeedUiState
import com.mk.newsshorts.feature.inbox.InboxUiEvent
import com.mk.newsshorts.feature.inbox.InboxUiState
import com.mk.newsshorts.feature.inbox.NotificationInboxScreen
import com.mk.newsshorts.feature.saved.SavedArticlesScreen
import com.mk.newsshorts.feature.saved.SavedArticlesUiEvent
import com.mk.newsshorts.feature.saved.SavedArticlesUiState
import com.mk.newsshorts.feature.search.SearchScreen
import com.mk.newsshorts.feature.search.SearchUiEvent
import com.mk.newsshorts.feature.search.SearchUiState
import com.mk.newsshorts.feature.settings.SettingsScreen
import com.mk.newsshorts.feature.settings.SettingsUiEvent
import com.mk.newsshorts.feature.settings.SettingsUiState
import com.mk.newsshorts.navigation.Navigator
import com.mk.newsshorts.navigation.Overlay
import com.mk.newsshorts.feature.feed.ArticleDetailsScreen
import com.mk.newsshorts.presentation.ui.screen.LicensesScreen
import com.mk.newsshorts.presentation.viewmodel.AppShellUiEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OverlayHost(
    overlays: List<Overlay>,
    navigator: Navigator,
    uiState: FeedUiState,
    authUiState: AuthUiState,
    inboxUiState: InboxUiState,
    searchUiState: SearchUiState,
    savedArticlesUiState: SavedArticlesUiState,
    settingsUiState: SettingsUiState,
    onFeedEvent: (FeedUiEvent) -> Unit,
    onShellEvent: (AppShellUiEvent) -> Unit,
    onSavedEvent: (SavedArticlesUiEvent) -> Unit,
    onAuthEvent: (AuthUiEvent) -> Unit,
    onInboxEvent: (InboxUiEvent) -> Unit,
    onSettingsEvent: (SettingsUiEvent) -> Unit,
    onSearchEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topOverlay = overlays.lastOrNull()

    // One rule for every screen pushed above the tabs rather than each owning
    // its own BackHandler. Only Android has a back gesture, which is why every
    // overlay also keeps a visible back arrow instead of relying on this alone.
    BackHandler(enabled = overlays.isNotEmpty()) {
        navigator.close()
    }

    // A plain Box with only a background does not consume touch input in
    // Compose — a tap on empty space (a gap between controls, the space
    // below a button) falls straight through to whatever is laid out
    // underneath in the same Box, which is the tab content this overlay
    // is meant to be covering. One blocking modifier here, rather than on
    // every overlay screen individually, is what actually makes each of
    // them modal.
    if (topOverlay != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
        )
    }

    when (topOverlay) {
        is Overlay.Details -> {
            ArticleDetailsScreen(
                article = topOverlay.article,
                isSaved = savedArticlesUiState.articles.any {
                    it.articleUrl == topOverlay.article.articleUrl
                },
                onBack = { onShellEvent(AppShellUiEvent.CloseOverlay) },
                onShare = { onShellEvent(AppShellUiEvent.ShareArticle(topOverlay.article)) },
                onToggleSaved = { onSavedEvent(SavedArticlesUiEvent.Toggle(topOverlay.article)) },
                onOpenSource = { onShellEvent(AppShellUiEvent.OpenArticleSource) },
                modifier = Modifier.fillMaxSize()
            )
        }
        Overlay.Settings -> {
            SettingsScreen(
                newsLanguage = uiState.selectedLanguage,
                settingsUiState = settingsUiState,
                authUser = authUiState.authUser,
                authInProgress = authUiState.authInProgress,
                authError = authUiState.authError,
                onNewsLanguageSelected = { onFeedEvent(FeedUiEvent.SelectLanguage(it)) },
                onBack = { onShellEvent(AppShellUiEvent.CloseOverlay) },
                onSettingsEvent = onSettingsEvent,
                onOpenSignIn = { navigator.open(Overlay.SignIn) },
                onSignOut = { onAuthEvent(AuthUiEvent.SignOut) },
                onDeleteAccount = { onAuthEvent(AuthUiEvent.DeleteAccount) },
                onDismissAuthError = { onAuthEvent(AuthUiEvent.DismissAuthError) },
                modifier = Modifier.fillMaxSize()
            )
        }
        Overlay.SavedArticles -> {
            SavedArticlesScreen(
                uiState = savedArticlesUiState,
                onBack = { onShellEvent(AppShellUiEvent.CloseOverlay) },
                onOpenArticle = { article ->
                    onShellEvent(AppShellUiEvent.OpenArticleDetails(article, ArticleOpenOrigin.SAVED))
                },
                onSavedEvent = onSavedEvent,
                modifier = Modifier.fillMaxSize()
            )
        }
        Overlay.Licenses -> {
            LicensesScreen(
                onShellEvent = onShellEvent,
                modifier = Modifier.fillMaxSize()
            )
        }
        Overlay.NotificationInbox -> {
            NotificationInboxScreen(
                uiState = inboxUiState,
                onEvent = onInboxEvent,
                onClose = { navigator.close() },
                modifier = Modifier.fillMaxSize()
            )
        }
        Overlay.Search -> {
            SearchScreen(
                uiState = searchUiState,
                onEvent = onSearchEvent,
                modifier = Modifier.fillMaxSize()
            )
        }
        Overlay.SignIn -> {
            SignInScreen(
                uiState = authUiState,
                onEvent = onAuthEvent,
                modifier = Modifier.fillMaxSize()
            )
        }
        null -> Unit
    }
}
