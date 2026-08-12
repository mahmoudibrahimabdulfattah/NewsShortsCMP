package com.mk.newsshorts.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.countryName
import com.mk.newsshorts.presentation.mvi.ArticleOpenOrigin
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.presentation.mvi.NewsUiEffect
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState
import com.mk.newsshorts.presentation.mvi.Overlay
import com.mk.newsshorts.presentation.ui.components.ArticleIndicator
import com.mk.newsshorts.presentation.ui.components.BottomNavigationBar
import com.mk.newsshorts.presentation.ui.components.CategoryRow
import com.mk.newsshorts.presentation.ui.components.CountrySelector
import com.mk.newsshorts.presentation.ui.components.ErrorScreen
import com.mk.newsshorts.presentation.ui.components.LoadingScreen
import com.mk.newsshorts.presentation.ui.components.NewsCard
import com.mk.newsshorts.presentation.ui.components.ProfileScreen
import com.mk.newsshorts.presentation.ui.theme.NewsShortsTheme
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel

@Composable
fun NewsScreen(
    viewModel: NewsViewModel,
    onOpenUrl: (String) -> Unit = {},
    onShareContent: (String, String, String) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState: NewsUiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is NewsUiEffect.OpenUrl -> onOpenUrl(effect.url)
                is NewsUiEffect.ShareContent ->
                    onShareContent(effect.title, effect.url, effect.chooserTitle)
                is NewsUiEffect.ShowToast -> onShowToast(effect.message)
                NewsUiEffect.RequestNotificationPermission -> onRequestNotificationPermission()
            }
        }
    }
    NewsScreenContent(
        uiState = uiState,
        onEvent = viewModel::processEvent,
        modifier = modifier
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NewsScreenContent(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // One rule for every screen pushed above the tabs — the details screen,
    // Settings, Saved — rather than each owning its own BackHandler. Only
    // Android has a back gesture, which is why every overlay also keeps a
    // visible back arrow instead of relying on this alone.
    BackHandler(enabled = uiState.overlays.isNotEmpty()) {
        onEvent(NewsUiEvent.CloseOverlay)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState.currentTab) {
            NavigationTab.PROFILE -> {
                ProfileScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
            else -> {
                // The feed stays dark regardless of the app theme: its text
                // sits directly on full-bleed photos, not on a themed surface,
                // and a light background there would make headlines unreadable
                // over a bright image.
                NewsShortsTheme(isDarkTheme = true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            uiState.isLoading -> {
                                LoadingScreen()
                            }
                            uiState.isError && !uiState.hasArticles -> {
                                ErrorScreen(
                                    errorMessage = uiState.errorMessage ?: appStrings().unknownError,
                                    onRetry = { onEvent(NewsUiEvent.RetryLoading) }
                                )
                            }
                            uiState.hasArticles -> {
                                NewsArticlesPager(
                                    uiState = uiState,
                                    onEvent = onEvent
                                )
                            }
                        }
                        TopGradientOverlay()
                        NewsScreenHeader(
                            uiState = uiState,
                            onEvent = onEvent,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        AnimatedVisibility(
                            visible = uiState.hasArticles,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                        ) {
                            ArticleIndicator(
                                totalCount = uiState.articles.size,
                                currentIndex = uiState.currentArticleIndex
                            )
                        }
                    }
                }
            }
        }
        if (uiState.overlays.isEmpty()) {
            BottomNavigationBar(
                selectedTab = uiState.currentTab,
                onTabSelected = { tab -> onEvent(NewsUiEvent.SelectTab(tab)) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        val topOverlay = uiState.overlays.lastOrNull()
        // A plain Box with only a background does not consume touch input in
        // Compose — a tap on empty space (a gap between controls, the space
        // below a button) falls straight through to whatever is laid out
        // underneath in the same Box, which is the tab content this overlay
        // is meant to be covering. One blocking modifier here, rather than on
        // every overlay screen individually, is what actually makes each of
        // them modal.
        if (topOverlay != null) {
            Box(
                modifier = Modifier
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
                    isSaved = uiState.savedArticles.any { it.articleUrl == topOverlay.article.articleUrl },
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Overlay.Settings -> {
                SettingsScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Overlay.SavedArticles -> {
                SavedArticlesScreen(
                    savedArticles = uiState.savedArticles,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Overlay.SignIn -> {
                SignInScreen(
                    isLoading = uiState.authInProgress,
                    errorFailure = uiState.authError,
                    onGoogleClick = { onEvent(NewsUiEvent.SignInWithGoogle) },
                    onEmailSignIn = { email, password ->
                        onEvent(NewsUiEvent.SignInWithEmail(email, password))
                    },
                    onEmailSignUp = { email, password ->
                        onEvent(NewsUiEvent.SignUpWithEmail(email, password))
                    },
                    onDismissError = { onEvent(NewsUiEvent.DismissAuthError) },
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun TopGradientOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun NewsScreenHeader(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
    ) {
        Text(
            text = strings.appName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = getHeaderSubtitle(uiState, strings),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (uiState.currentTab) {
            NavigationTab.FOR_YOU -> {
                CategoryRow(
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { category ->
                        onEvent(NewsUiEvent.SelectCategory(category))
                    }
                )
            }
            NavigationTab.COUNTRIES -> {
                CountrySelector(
                    selectedCountry = uiState.selectedCountry,
                    onCountrySelected = { country ->
                        onEvent(NewsUiEvent.SelectCountry(country))
                    }
                )
            }
            NavigationTab.PROFILE -> {
                // Profile tab has its own header in ProfileScreen
            }
        }
    }
}

@Composable
private fun getHeaderSubtitle(
    uiState: NewsUiState,
    strings: com.mk.newsshorts.presentation.localization.AppStrings
): String {
    return when (uiState.currentTab) {
        NavigationTab.FOR_YOU -> strings.swipeUpForMore
        NavigationTab.COUNTRIES ->
            "${strings.newsFromCountry} ${countryName(uiState.selectedCountry.code, uiState.selectedCountry.displayName)}"
        NavigationTab.PROFILE -> strings.settingsPreferences
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NewsArticlesPager(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentArticleIndex,
        pageCount = { uiState.articles.size }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onEvent(NewsUiEvent.ScrollToArticle(page))
        }
    }
    LaunchedEffect(uiState.selectedCategory, uiState.selectedCountry, uiState.currentTab) {
        if (uiState.articles.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { onEvent(NewsUiEvent.RefreshNews) },
        modifier = modifier.fillMaxSize()
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2
        ) { pageIndex ->
            val article = uiState.articles[pageIndex]
            val isArticleSaved: Boolean = uiState.savedArticles.any { it.articleUrl == article.articleUrl }
            NewsCard(
                article = article,
                isSaved = isArticleSaved,
                onOpenArticle = {
                    onEvent(NewsUiEvent.OpenArticleDetails(article, ArticleOpenOrigin.FEED))
                },
                onShareArticle = { onEvent(NewsUiEvent.ShareArticle(article)) },
                onSaveArticle = { onEvent(NewsUiEvent.SaveArticle(article)) }
            )
        }
    }
}
