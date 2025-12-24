package org.example.newsshorts.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.newsshorts.presentation.localization.appStrings
import org.example.newsshorts.presentation.mvi.NavigationTab
import org.example.newsshorts.presentation.mvi.NewsUiEffect
import org.example.newsshorts.presentation.mvi.NewsUiEvent
import org.example.newsshorts.presentation.mvi.NewsUiState
import org.example.newsshorts.presentation.ui.components.ArticleIndicator
import org.example.newsshorts.presentation.ui.components.BottomNavigationBar
import org.example.newsshorts.presentation.ui.components.CategoryRow
import org.example.newsshorts.presentation.ui.components.CountrySelector
import org.example.newsshorts.presentation.ui.components.ErrorScreen
import org.example.newsshorts.presentation.ui.components.LoadingScreen
import org.example.newsshorts.presentation.ui.components.NewsCard
import org.example.newsshorts.presentation.ui.components.ProfileScreen
import org.example.newsshorts.presentation.viewmodel.NewsViewModel

@Composable
fun NewsScreen(
    viewModel: NewsViewModel,
    onOpenUrl: (String) -> Unit = {},
    onShareContent: (String, String) -> Unit = { _, _ -> },
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState: NewsUiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is NewsUiEffect.OpenUrl -> onOpenUrl(effect.url)
                is NewsUiEffect.ShareContent -> onShareContent(effect.title, effect.url)
                is NewsUiEffect.ShowToast -> onShowToast(effect.message)
            }
        }
    }
    NewsScreenContent(
        uiState = uiState,
        onEvent = viewModel::processEvent,
        modifier = modifier
    )
}

@Composable
private fun NewsScreenContent(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState.currentTab) {
            NavigationTab.PROFILE -> {
                ProfileScreen(
                    uiState = uiState,
                    onLanguageSelected = { language ->
                        onEvent(NewsUiEvent.SelectLanguage(language))
                    },
                    onAppLocaleSelected = { locale ->
                        onEvent(NewsUiEvent.SelectAppLocale(locale))
                    },
                    onSavedArticleClick = { index ->
                        onEvent(NewsUiEvent.OpenArticle(index))
                    },
                    onRemoveSavedArticle = { index ->
                        onEvent(NewsUiEvent.RemoveSavedArticle(index))
                    }
                )
            }
            else -> {
                when {
                    uiState.isLoading -> {
                        LoadingScreen()
                    }
                    uiState.isError && !uiState.hasArticles -> {
                        ErrorScreen(
                            errorMessage = uiState.errorMessage ?: "Unknown error",
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
        BottomNavigationBar(
            selectedTab = uiState.currentTab,
            onTabSelected = { tab -> onEvent(NewsUiEvent.SelectTab(tab)) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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

private fun getHeaderSubtitle(
    uiState: NewsUiState,
    strings: org.example.newsshorts.presentation.localization.AppStrings
): String {
    return when (uiState.currentTab) {
        NavigationTab.FOR_YOU -> strings.swipeUpForMore
        NavigationTab.COUNTRIES -> "${strings.newsFromCountry} ${uiState.selectedCountry.displayName}"
        NavigationTab.PROFILE -> strings.settingsPreferences
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 2
    ) { pageIndex ->
        val article = uiState.articles[pageIndex]
        val isArticleSaved = uiState.savedArticles.any { it.articleUrl == article.articleUrl }
        NewsCard(
            article = article,
            isSaved = isArticleSaved,
            onOpenArticle = { onEvent(NewsUiEvent.OpenArticle(pageIndex)) },
            onShareArticle = { onEvent(NewsUiEvent.ShareArticle(pageIndex)) },
            onSaveArticle = { onEvent(NewsUiEvent.SaveArticle(pageIndex)) }
        )
    }
}
