package com.mk.newsshorts.presentation.ui.screen

import com.mk.newsshorts.presentation.viewmodel.AppShellUiEvent
import com.mk.newsshorts.presentation.viewmodel.AppShellViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.mk.newsshorts.presentation.ui.theme.UnreadMark
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.feature.auth.AuthUiEvent
import com.mk.newsshorts.feature.auth.AuthUiState
import com.mk.newsshorts.feature.auth.AuthViewModel
import com.mk.newsshorts.feature.inbox.InboxUiEffect
import com.mk.newsshorts.feature.inbox.InboxUiEvent
import com.mk.newsshorts.feature.inbox.InboxUiState
import com.mk.newsshorts.feature.inbox.InboxViewModel
import com.mk.newsshorts.feature.saved.SavedArticlesUiEffect
import com.mk.newsshorts.feature.saved.SavedArticlesUiEvent
import com.mk.newsshorts.feature.saved.SavedArticlesUiState
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchUiEvent
import com.mk.newsshorts.feature.search.SearchUiState
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsUiEffect
import com.mk.newsshorts.feature.settings.SettingsUiEvent
import com.mk.newsshorts.feature.settings.SettingsUiState
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.countryName
import com.mk.newsshorts.core.model.article.ArticleOpenOrigin
import com.mk.newsshorts.navigation.NavigationTab
import com.mk.newsshorts.feature.feed.FeedUiEffect
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.feature.feed.FeedUiState
import com.mk.newsshorts.navigation.Overlay
import com.mk.newsshorts.navigation.Navigator
import com.mk.newsshorts.presentation.ui.OverlayHost
import com.mk.newsshorts.presentation.ui.components.BottomNavigationBar
import com.mk.newsshorts.presentation.ui.components.CategoryRow
import com.mk.newsshorts.presentation.ui.components.CountrySelector
import com.mk.newsshorts.presentation.ui.components.ErrorScreen
import com.mk.newsshorts.presentation.ui.components.LoadingScreen
import com.mk.newsshorts.presentation.ui.components.NewsCard
import com.mk.newsshorts.presentation.ui.components.ProfileScreen
import com.mk.newsshorts.presentation.ui.theme.NewsShortsTheme
import com.mk.newsshorts.presentation.ui.theme.ImageryScrim
import com.mk.newsshorts.presentation.ui.theme.OnImagery
import com.mk.newsshorts.presentation.ui.theme.PillShape
import com.mk.newsshorts.feature.feed.FeedViewModel

@Composable
fun NewsScreen(
    viewModel: FeedViewModel,
    shellViewModel: AppShellViewModel,
    navigator: Navigator,
    authViewModel: AuthViewModel,
    inboxViewModel: InboxViewModel,
    searchViewModel: SearchViewModel,
    savedArticlesViewModel: SavedArticlesViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenUrl: (String) -> Unit = {},
    onShareContent: (String, String, String) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState: FeedUiState by viewModel.uiState.collectAsState()
    val currentTab: NavigationTab by navigator.tab.collectAsState()
    val overlays: List<Overlay> by navigator.overlays.collectAsState()
    val authUiState: AuthUiState by authViewModel.uiState.collectAsState()
    val inboxUiState: InboxUiState by inboxViewModel.uiState.collectAsState()
    val searchUiState: SearchUiState by searchViewModel.uiState.collectAsState()
    val savedArticlesUiState: SavedArticlesUiState by savedArticlesViewModel.uiState.collectAsState()
    val settingsUiState: SettingsUiState by settingsViewModel.uiState.collectAsState()
    // Read through rememberUpdatedState, not captured directly: the collector
    // below is started once and never restarts, so it would otherwise hold the
    // handlers from the first composition for the life of the process. That
    // costs nothing for three of these, but `onOpenUrl` closes over the
    // resolved app theme — which at first composition is still the SYSTEM
    // default, before settings have loaded. A reader on Light with the phone
    // on Dark got a dark browser toolbar every time, and changing Appearance
    // afterwards never reached it.
    val openUrl by rememberUpdatedState(onOpenUrl)
    val shareContent by rememberUpdatedState(onShareContent)
    val showToast by rememberUpdatedState(onShowToast)
    val requestNotificationPermission by rememberUpdatedState(onRequestNotificationPermission)
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is FeedUiEffect.OpenUrl -> openUrl(effect.url)
                is FeedUiEffect.ShareContent ->
                    shareContent(effect.title, effect.url, effect.chooserTitle)
                is FeedUiEffect.ShowToast -> showToast(effect.message)
                FeedUiEffect.RequestNotificationPermission -> requestNotificationPermission()
            }
        }
    }
    LaunchedEffect(Unit) {
        settingsViewModel.uiEffect.collect { effect ->
            when (effect) {
                is SettingsUiEffect.ShowToast -> showToast(effect.message)
                SettingsUiEffect.RequestNotificationPermission -> requestNotificationPermission()
            }
        }
    }
    LaunchedEffect(Unit) {
        savedArticlesViewModel.uiEffect.collect { effect ->
            when (effect) {
                is SavedArticlesUiEffect.ShowToast -> showToast(effect.message)
            }
        }
    }
    LaunchedEffect(Unit) {
        inboxViewModel.uiEffect.collect { effect ->
            when (effect) {
                is InboxUiEffect.OpenNotification -> {
                    shellViewModel.processEvent(AppShellUiEvent.OpenDeepLink(effect.link))
                }
            }
        }
    }
    var wasSearchOpen: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(Overlay.Search in overlays, uiState.selectedLanguage.code) {
        val isSearchOpen = Overlay.Search in overlays
        when {
            isSearchOpen && !wasSearchOpen ->
                searchViewModel.processEvent(SearchUiEvent.Opened(uiState.selectedLanguage.code))
            !isSearchOpen && wasSearchOpen ->
                searchViewModel.processEvent(SearchUiEvent.Closed)
        }
        wasSearchOpen = isSearchOpen
    }
    val onFeedEvent: (FeedUiEvent) -> Unit = { event -> viewModel.processEvent(event) }
    val onSavedEvent: (SavedArticlesUiEvent) -> Unit = { event ->
        savedArticlesViewModel.processEvent(event)
    }
    val onShellEvent: (AppShellUiEvent) -> Unit = { event ->
        shellViewModel.processEvent(event)
    }
    val onSettingsEvent: (SettingsUiEvent) -> Unit = { event ->
        settingsViewModel.processEvent(event)
    }
    val onAuthEvent: (AuthUiEvent) -> Unit = { event ->
        authViewModel.processEvent(event)
    }
    val onSearchEvent: (SearchUiEvent) -> Unit = { event ->
        when (event) {
            SearchUiEvent.Closed -> shellViewModel.processEvent(AppShellUiEvent.CloseOverlay)
            is SearchUiEvent.ResultOpened -> {
                searchViewModel.processEvent(event)
                shellViewModel.processEvent(
                    AppShellUiEvent.OpenArticleDetails(event.article, ArticleOpenOrigin.SEARCH)
                )
            }
            else -> searchViewModel.processEvent(event)
        }
    }
    val onInboxEvent: (InboxUiEvent) -> Unit = { event ->
        inboxViewModel.processEvent(event)
    }
    NewsScreenContent(
        uiState = uiState,
        currentTab = currentTab,
        authUiState = authUiState,
        inboxUiState = inboxUiState,
        overlays = overlays,
        navigator = navigator,
        searchUiState = searchUiState,
        savedArticlesUiState = savedArticlesUiState,
        settingsUiState = settingsUiState,
        onFeedEvent = onFeedEvent,
        onShellEvent = onShellEvent,
        onSavedEvent = onSavedEvent,
        onAuthEvent = onAuthEvent,
        onInboxEvent = onInboxEvent,
        onSettingsEvent = onSettingsEvent,
        onSearchEvent = onSearchEvent,
        modifier = modifier
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NewsScreenContent(
    uiState: FeedUiState,
    currentTab: NavigationTab,
    authUiState: AuthUiState,
    inboxUiState: InboxUiState,
    overlays: List<Overlay>,
    navigator: Navigator,
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
    modifier: Modifier = Modifier
) {
    // With no overlay open, back off a secondary tab returns to the feed
    // rather than leaving the app. This is what Android readers expect — the
    // first tab is the app's home, and closing from Countries or Profile feels
    // like losing your place. Disabled on the feed itself, which hands back to
    // the system so the app still closes from there on the next press.
    BackHandler(enabled = overlays.isEmpty() && currentTab != NavigationTab.FOR_YOU) {
        navigator.handleBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (currentTab) {
            NavigationTab.PROFILE -> {
                ProfileScreen(
                    authUser = authUiState.authUser,
                    savedArticlesUiState = savedArticlesUiState,
                    onShellEvent = onShellEvent,
                    onSavedEvent = onSavedEvent,
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
                                    onRetry = { onFeedEvent(FeedUiEvent.RetryLoading) }
                                )
                            }
                            uiState.hasArticles -> {
                                NewsArticlesPager(
                                    uiState = uiState,
                                    savedArticlesUiState = savedArticlesUiState,
                                    onEvent = onFeedEvent,
                                    onShellEvent = onShellEvent,
                                    onSavedEvent = onSavedEvent,
                                )
                            }
                        }
                        TopGradientOverlay()
                        NewsScreenHeader(
                            uiState = uiState,
                            currentTab = currentTab,
                            inboxUiState = inboxUiState,
                            onEvent = onFeedEvent,
                            onShellEvent = onShellEvent,
                            onInboxEvent = onInboxEvent,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        NextPageStatus(
                            uiState = uiState,
                            onEvent = onFeedEvent,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 96.dp)
                        )
                    }
                }
            }
        }
        if (overlays.isEmpty()) {
            BottomNavigationBar(
                selectedTab = currentTab,
                onTabSelected = navigator::selectTab,
                // Every tab but Profile is the feed, drawn over photographs.
                isOverImagery = currentTab != NavigationTab.PROFILE,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        OverlayHost(
            overlays = overlays,
            navigator = navigator,
            uiState = uiState,
            authUiState = authUiState,
            inboxUiState = inboxUiState,
            searchUiState = searchUiState,
            savedArticlesUiState = savedArticlesUiState,
            settingsUiState = settingsUiState,
            onFeedEvent = onFeedEvent,
            onShellEvent = onShellEvent,
            onSavedEvent = onSavedEvent,
            onAuthEvent = onAuthEvent,
            onInboxEvent = onInboxEvent,
            onSettingsEvent = onSettingsEvent,
            onSearchEvent = onSearchEvent,
        )
    }
}

/**
 * What is happening below the last loaded card, shown only when there is
 * something to say.
 *
 * A prefetch that succeeds is silent: the feed simply keeps going, which is the
 * point. This appears when the reader has actually caught up with the loading —
 * they are on one of the last few cards — or when a page failed and the feed
 * would otherwise look like it had ended for no reason.
 */
@Composable
private fun NextPageStatus(
    uiState: FeedUiState,
    onEvent: (FeedUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val nearTheEnd: Boolean = uiState.hasArticles &&
        uiState.currentArticleIndex >= uiState.articles.size - 2
    val label: String? = when {
        uiState.nextPageFailed && nearTheEnd -> strings.tryAgain
        uiState.isLoadingNextPage && nearTheEnd -> strings.loading
        else -> null
    }
    AnimatedVisibility(
        visible = label != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = label.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = OnImagery.content,
            modifier = Modifier
                .background(
                    color = ImageryScrim.copy(alpha = 0.55f),
                    shape = PillShape
                )
                .clickable(enabled = uiState.nextPageFailed) {
                    onEvent(FeedUiEvent.RetryNextPage)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
            .background(brush = OnImagery.topScrim)
    )
}

@Composable
private fun NewsScreenHeader(
    uiState: FeedUiState,
    currentTab: NavigationTab,
    inboxUiState: InboxUiState,
    onEvent: (FeedUiEvent) -> Unit,
    onShellEvent: (AppShellUiEvent) -> Unit,
    onInboxEvent: (InboxUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = OnImagery.content,
                modifier = Modifier.weight(1f)
            )
            // Both live on the feed rather than in the tab bar: they are
            // things you do to the news, not further places the news lives.
            // The bell is here and not in Profile because it carries the
            // unread mark, and a mark nobody passes is not a mark.
            IconButton(onClick = { onInboxEvent(InboxUiEvent.Opened) }) {
                // Wider than the glyph so the badge has a corner of its own to
                // sit in. It used to be nudged out with an offset, which put it
                // past the icon button's bounds — and that clips, so the circle
                // came out with two flat edges.
                Box(modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = strings.notificationInbox,
                        tint = OnImagery.content,
                        modifier = Modifier.align(Alignment.BottomStart).size(24.dp),
                    )
                    val unread = inboxUiState.unreadCount
                    if (unread > 0) {
                        // A minimum size rather than a fixed one: at one digit
                        // this is a circle, and "9+" widens it into a capsule
                        // instead of squeezing two glyphs into a round hole.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .clip(CircleShape)
                                .background(UnreadMark)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = strings.unreadNotifications(unread),
                                // Trimmed and centred, which is what actually
                                // puts the digit in the middle. Centring the Box
                                // only centres the *line box*, and a line box
                                // shorter than the font's own metrics leaves the
                                // glyph sitting high inside it — the number rode
                                // above the centre of the circle.
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                    // A count is a number, and numbers read
                                    // left to right in both languages. Left to
                                    // the paragraph direction, the plus in "9+"
                                    // is a neutral character and Arabic bidi
                                    // moved it to the far side — the badge said
                                    // "+9".
                                    textDirection = TextDirection.Ltr,
                                ),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { onShellEvent(AppShellUiEvent.OpenSearch) }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = strings.search,
                    tint = OnImagery.content,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = getHeaderSubtitle(uiState, currentTab, strings),
            style = MaterialTheme.typography.bodySmall,
            color = OnImagery.contentMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (currentTab) {
            NavigationTab.FOR_YOU -> {
                CategoryRow(
                    categories = uiState.categoryOrder,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { category ->
                        onEvent(FeedUiEvent.SelectCategory(category))
                    }
                )
            }
            NavigationTab.COUNTRIES -> {
                CountrySelector(
                    selectedCountry = uiState.selectedCountry,
                    onCountrySelected = { country ->
                        onEvent(FeedUiEvent.SelectCountry(country))
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
    uiState: FeedUiState,
    currentTab: NavigationTab,
    strings: com.mk.newsshorts.presentation.localization.AppStrings
): String {
    return when (currentTab) {
        NavigationTab.FOR_YOU -> strings.swipeUpForMore
        NavigationTab.COUNTRIES ->
            "${strings.newsFromCountry} ${countryName(uiState.selectedCountry.code, uiState.selectedCountry.displayName)}"
        NavigationTab.PROFILE -> strings.settingsPreferences
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NewsArticlesPager(
    uiState: FeedUiState,
    savedArticlesUiState: SavedArticlesUiState,
    onEvent: (FeedUiEvent) -> Unit,
    onShellEvent: (AppShellUiEvent) -> Unit,
    onSavedEvent: (SavedArticlesUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentArticleIndex,
        pageCount = { uiState.articles.size }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onEvent(FeedUiEvent.ScrollToArticle(page))
        }
    }
    // The revision now means the feed was genuinely replaced as a new reading
    // session, such as by an explicit refresh or a first category visit.
    // Re-selecting a remembered category and appending a page deliberately
    // leave it alone, because both must preserve the reader's position.
    LaunchedEffect(uiState.feedRevision) {
        if (uiState.articles.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }
    // A first visit changes category while the previous category's articles
    // may still be on screen. This revision changes only after remembered
    // articles and their index have been published together.
    LaunchedEffect(uiState.categoryRestoreRevision) {
        if (uiState.categoryRestoreRevision > 0 && uiState.articles.isNotEmpty()) {
            pagerState.scrollToPage(uiState.currentArticleIndex)
        }
    }
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { onEvent(FeedUiEvent.RefreshNews) },
        modifier = modifier.fillMaxSize()
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            // A stable identity keeps the visible story anchored when a
            // background refresh inserts or re-ranks cards before it.
            key = { pageIndex -> uiState.articles[pageIndex].articleUrl.value },
        ) { pageIndex ->
            val article = uiState.articles[pageIndex]
            val isArticleSaved: Boolean = savedArticlesUiState.articles.any {
                it.articleUrl == article.articleUrl
            }
            NewsCard(
                article = article,
                isSaved = isArticleSaved,
                onOpenArticle = {
                    onShellEvent(AppShellUiEvent.OpenArticleDetails(article, ArticleOpenOrigin.FEED))
                },
                onShareArticle = { onShellEvent(AppShellUiEvent.ShareArticle(article)) },
                onSaveArticle = { onSavedEvent(SavedArticlesUiEvent.Toggle(article)) }
            )
        }
    }
}
