package com.mk.newsshorts.feature.feed

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.core.data.local.SeenArticlesStore
import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.core.model.time.currentTimeMillis
import com.mk.newsshorts.core.domain.feed.FeedInvalidator
import com.mk.newsshorts.core.domain.feed.InvalidationReason
import com.mk.newsshorts.core.domain.feed.appendPage
import com.mk.newsshorts.core.domain.feed.shouldLoadNextPage
import com.mk.newsshorts.core.domain.ranking.deprioritiseSeen
import com.mk.newsshorts.core.domain.sync.SyncPublisher
import com.mk.newsshorts.core.model.sync.toSyncedSettings
import com.mk.newsshorts.core.model.FeedPage
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.domain.preferences.openingCategory
import com.mk.newsshorts.core.domain.preferences.orderedCategories
import com.mk.newsshorts.core.model.NewsResult
import com.mk.newsshorts.core.domain.use_case.GetTopHeadlinesRequest
import com.mk.newsshorts.core.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.core.model.analytics.AnalyticsEvent
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.core.model.feed.CountryOption
import com.mk.newsshorts.core.model.feed.LanguageOption
import com.mk.newsshorts.navigation.NavigationTab
import com.mk.newsshorts.navigation.Navigator
import com.mk.newsshorts.feature.feed.FeedUiEffect
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.feature.feed.FeedUiState
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel

internal data class RememberedCategoryFeed(
    val articles: List<NewsArticle>,
    val nextPageFile: String?,
    val currentArticleIndex: Int,
)

private data class CategoryFeedKey(
    val category: NewsCategory,
    val language: String,
)

/**
 * A category is a place the reader can leave and return to, so its position
 * survives that short trip. The cap keeps this session convenience from
 * quietly becoming a second, unbounded feed cache.
 */
internal class CategoryFeedMemory(
    private val maxEntries: Int = MAX_REMEMBERED_CATEGORY_FEEDS,
) {
    private val feeds = linkedMapOf<CategoryFeedKey, RememberedCategoryFeed>()

    init {
        require(maxEntries > 0) { "Category feed memory must hold at least one feed." }
    }

    fun rememberAndFind(
        currentState: FeedUiState,
        selectedCategory: NewsCategory,
        currentTab: NavigationTab,
    ): RememberedCategoryFeed? {
        remember(currentState, currentTab)
        return find(selectedCategory, currentState.selectedLanguage.code)
    }

    fun clear() {
        feeds.clear()
    }

    private fun remember(state: FeedUiState, currentTab: NavigationTab) {
        if (currentTab != NavigationTab.FOR_YOU || state.articles.isEmpty()) return
        val key = CategoryFeedKey(state.selectedCategory, state.selectedLanguage.code)
        feeds.remove(key)
        feeds[key] = RememberedCategoryFeed(
            articles = state.articles,
            nextPageFile = state.nextPageFile,
            currentArticleIndex = state.currentArticleIndex,
        )
        while (feeds.size > maxEntries) {
            feeds.remove(feeds.keys.first())
        }
    }

    private fun find(category: NewsCategory, language: String): RememberedCategoryFeed? {
        val key = CategoryFeedKey(category, language)
        val remembered = feeds.remove(key) ?: return null
        // A category just revisited is less likely to be the next one evicted.
        feeds[key] = remembered
        return remembered
    }

    private companion object {
        const val MAX_REMEMBERED_CATEGORY_FEEDS: Int = 4
    }
}

internal fun FeedUiState.withSelectedCategory(
    category: NewsCategory,
    remembered: RememberedCategoryFeed?,
): FeedUiState {
    if (remembered == null) {
        return copy(
            selectedCategory = category,
            currentArticleIndex = 0,
            errorMessage = null,
        )
    }
    return copy(
        isLoading = false,
        articles = remembered.articles,
        selectedCategory = category,
        categoryRestoreRevision = categoryRestoreRevision + 1,
        currentArticleIndex = remembered.currentArticleIndex.coerceIn(
            minimumValue = 0,
            maximumValue = remembered.articles.lastIndex.coerceAtLeast(0),
        ),
        errorMessage = null,
        isRefreshing = false,
        isBackgroundRefreshing = true,
        isOfflineMode = false,
        nextPageFile = remembered.nextPageFile,
        isLoadingNextPage = false,
        nextPageFailed = false,
    )
}

internal fun FeedUiState.withLoadedFeed(
    articles: List<NewsArticle>,
    nextPageFile: String?,
    preserveReaderPosition: Boolean,
): FeedUiState {
    val fallbackIndex = currentArticleIndex.coerceIn(
        minimumValue = 0,
        maximumValue = articles.lastIndex.coerceAtLeast(0),
    )
    val preservedIndex = if (preserveReaderPosition) {
        // An index is only a position in the old list, which no longer exists
        // after a refresh. The URL is the only part of the reader's place that
        // survives into the replacement list.
        this.articles.getOrNull(currentArticleIndex)?.articleUrl
            ?.let { currentUrl ->
                articles.indexOfFirst { article -> article.articleUrl == currentUrl }
                    .takeIf { it >= 0 }
            }
            ?: fallbackIndex
    } else {
        0
    }
    return copy(
        isLoading = false,
        isRefreshing = false,
        isBackgroundRefreshing = false,
        feedRevision = if (preserveReaderPosition) feedRevision else feedRevision + 1,
        articles = articles,
        nextPageFile = nextPageFile,
        isLoadingNextPage = false,
        nextPageFailed = false,
        errorMessage = null,
        currentArticleIndex = preservedIndex,
        isOfflineMode = false,
    )
}

class FeedViewModel(
    private val getTopHeadlinesUseCase: GetTopHeadlinesUseCase,
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val savedArticles: SavedArticles,
    private val syncPublisher: SyncPublisher,
    private val feedInvalidator: FeedInvalidator,
    private val seenArticlesStore: SeenArticlesStore,
    private val navigator: Navigator,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {

    private val feedScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private val mutableState: MutableStateFlow<FeedUiState> = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = mutableState.asStateFlow()

    private val mutableEffect: MutableSharedFlow<FeedUiEffect> = MutableSharedFlow()
    val uiEffect: SharedFlow<FeedUiEffect> = mutableEffect.asSharedFlow()

    private val categoryFeedMemory = CategoryFeedMemory()
    private var currentTab: NavigationTab = navigator.tab.value

    /** Toast text is built here rather than in the UI, so it needs the locale too. */
    private fun strings(): AppStrings =
        getStrings(AppLocale.fromCode(settingsManager.preferences.value.appLocale))

    init {
        loadSavedSettings()
        observeFeedInvalidations()
        observeTabSelections()
    }


    private fun observeFeedInvalidations() {
        feedScope.launch {
            feedInvalidator.signals.collect { reason ->
                handleFeedInvalidation(reason)
            }
        }
    }

    private fun handleFeedInvalidation(reason: InvalidationReason) {
        val preferences = settingsManager.preferences.value
        val newsLanguage = LanguageOption.entries.find { it.code == preferences.newsLanguage }
            ?: mutableState.value.selectedLanguage
        val country = CountryOption.entries.find { it.code == preferences.selectedCountry }
            ?: mutableState.value.selectedCountry
        val languageChanged = newsLanguage != mutableState.value.selectedLanguage

        if (languageChanged) categoryFeedMemory.clear()
        mutableState.update { state ->
            state.copy(
                selectedLanguage = newsLanguage,
                selectedCountry = country,
                currentArticleIndex = 0,
                errorMessage = null,
            )
        }
        resetArticleTracking()
        when (reason) {
            InvalidationReason.CountryChanged -> loadNewsForCountryWithCache(country)
            InvalidationReason.LanguageChanged,
            InvalidationReason.SyncApplied -> loadNewsWithCache()
            InvalidationReason.OnboardingFinished -> {
                // The reader has just chosen their categories, so the order and
                // the opening category are re-read rather than pushed here.
                // Onboarding writes the choice to settings and says only that
                // the feed is stale; what that means for the feed is the feed's
                // own business.
                applyPreferredCategories()
                loadNewsWithCache()
            }
        }
    }

    /** The reader's category choice, as the feed reads it from the store. */
    private fun applyPreferredCategories() {
        val preferred: List<String> = settingsManager.preferredCategories()
        mutableState.update { state ->
            state.copy(
                categoryOrder = orderedCategories(preferred),
                selectedCategory = openingCategory(preferred),
            )
        }
    }

    private fun loadSavedSettings() {
        feedScope.launch {
            // One snapshot: reading nine separate flows left a window where
            // half of them had been answered and half had not.
            val stored = settingsManager.preferences.value
            val newsLanguage: LanguageOption = LanguageOption.entries.find { it.code == stored.newsLanguage }
                ?: LanguageOption.ENGLISH
            val country: CountryOption = CountryOption.entries.find { it.code == stored.selectedCountry }
                ?: CountryOption.UNITED_STATES
            val preferred: List<String> = settingsManager.preferredCategories()
            mutableState.update { state ->
                state.copy(
                    selectedCategory = openingCategory(preferred),
                    categoryOrder = orderedCategories(preferred),
                    selectedLanguage = newsLanguage,
                    selectedCountry = country,
                )
            }
            savedArticles.load()
            when (currentTab) {
                NavigationTab.COUNTRIES -> loadNewsForCountryWithCache(country)
                NavigationTab.FOR_YOU -> loadNewsWithCache()
                NavigationTab.PROFILE -> Unit
            }
        }
    }

    /**
     * Read-then-newest-first is not enough on its own — a returning reader
     * would just see yesterday's top story again. Applied at every site that
     * assigns [FeedUiState.articles], never to the list already on screen: a
     * reorder under a reader's thumb would move the card they are mid-swipe on.
     *
     * A later page is ranked the same way, but only within itself — see
     * [handleNextPageLoaded]. Ranking the whole feed again once a page arrives
     * would be exactly the reorder this avoids.
     */
    private fun applyRanking(articles: List<NewsArticle>): List<NewsArticle> =
        articles.deprioritiseSeen(seenArticlesStore.load())

    /**
     * Which feed the articles on screen belong to.
     *
     * A page load is a request that outlives the feed that started it: pull to
     * refresh, or switch category, while page three is in flight, and it
     * arrives to a feed that no longer has anything to do with it. Bumped
     * whenever the feed is replaced, and checked before a page is appended.
     */
    private var feedGeneration: Int = 0

    /** Marks the start of a new feed and returns the generation to check against. */
    private fun startNewFeed(): Int = ++feedGeneration

    fun processEvent(event: FeedUiEvent) {
        when (event) {
            is FeedUiEvent.SelectCategory -> handleSelectCategory(event.category)
            is FeedUiEvent.SelectCountry -> handleSelectCountry(event.country)
            is FeedUiEvent.SelectLanguage -> handleSelectLanguage(event.language)
            is FeedUiEvent.ScrollToArticle -> handleScrollToArticle(event.index)
            FeedUiEvent.RefreshNews -> handleRefreshNews()
            FeedUiEvent.RetryLoading -> handleRetryLoading()
            FeedUiEvent.RetryNextPage -> handleRetryNextPage()
            FeedUiEvent.DismissError -> handleDismissError()
            FeedUiEvent.RequestNotificationPermissionIfDue -> handleRequestNotificationPermissionIfDue()
        }
    }

    private fun observeTabSelections() {
        feedScope.launch {
            navigator.tabSelections.collect { tab ->
                handleSelectTab(tab)
            }
        }
    }

    private fun handleSelectCategory(category: NewsCategory) {
        if (category == mutableState.value.selectedCategory) return
        val remembered = categoryFeedMemory.rememberAndFind(
            currentState = mutableState.value,
            selectedCategory = category,
            currentTab = currentTab,
        )
        // The previous generation is invalidated before the restored feed is
        // published, so an answer from the category just left cannot land in
        // the gap and replace it.
        val restoreGeneration: Int? = remembered?.let { startNewFeed() }
        mutableState.update { state ->
            state.withSelectedCategory(category, remembered)
        }
        analytics.logEvent(AnalyticsEvent.CategorySelected(category.apiValue))
        resetArticleTracking()
        if (restoreGeneration == null) {
            loadNewsWithCache()
        } else {
            val request = currentRequest()
            feedScope.launch {
                fetchNewsInBackground(
                    request = request,
                    generation = restoreGeneration,
                    preserveReaderPosition = true,
                )
            }
        }
    }

    private fun publishSettingsIfSignedIn() {
        syncPublisher.publishSettings(settingsManager.preferences.value.toSyncedSettings())
    }

    private fun handleSelectCountry(country: CountryOption) {
        if (country == mutableState.value.selectedCountry) return
        analytics.logEvent(AnalyticsEvent.CountrySelected(country.code))
        resetArticleTracking()
        feedScope.launch {
            settingsManager.saveSelectedCountry(country.code)
            publishSettingsIfSignedIn()
            feedInvalidator.invalidate(InvalidationReason.CountryChanged)
        }
    }

    private fun loadNewsForCountryWithCache(country: CountryOption) {
        val currentState: FeedUiState = mutableState.value
        val request = GetTopHeadlinesRequest(
            category = currentState.selectedCategory,
            country = country.code,
            countryName = country.displayName,
            language = currentState.selectedLanguage.code,
            useCountry = true
        )
        val generation = startNewFeed()
        showCachedFeed(request)
        feedScope.launch {
            fetchNewsInBackground(request, generation)
        }
    }

    private fun handleSelectLanguage(language: LanguageOption) {
        if (language == mutableState.value.selectedLanguage) return
        feedScope.launch {
            analytics.logEvent(AnalyticsEvent.NewsLanguageChanged(language.code))
            analytics.setProperty("news_language", language.code)
            settingsManager.saveNewsLanguage(language.code)
            publishSettingsIfSignedIn()
            feedInvalidator.invalidate(InvalidationReason.LanguageChanged)
            mutableEffect.emit(FeedUiEffect.ShowToast(strings().languageNames[language.code] ?: language.displayName))
        }
    }

    private fun handleSelectTab(tab: NavigationTab) {
        if (tab == currentTab) {
            // Tapping the tab you are already on is how every feed app spells
            // "take me back to the top", and forty cards deep that is otherwise
            // forty swipes. Refreshing rather than only scrolling, because a
            // reader who has come all the way back up is asking what is new —
            // and the scroll falls out of it, since a refresh replaces the feed
            // and the pager follows [FeedUiState.feedRevision] to the top.
            if (tab != NavigationTab.PROFILE) handleRefreshNews()
            return
        }
        currentTab = tab
        val needsLoading: Boolean = tab != NavigationTab.PROFILE
        mutableState.update { state ->
            state.copy(
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        if (needsLoading) {
            when (tab) {
                NavigationTab.COUNTRIES -> {
                    loadNewsForCountryWithCache(mutableState.value.selectedCountry)
                }
                NavigationTab.FOR_YOU -> {
                    loadNewsWithCache()
                }
                NavigationTab.PROFILE -> {
                    // No loading needed
                }
            }
        }
    }

    /** Start of the current card's time on screen, for the viewed/skipped split. */
    private var articleShownAtMillis: Long = currentTimeMillis()
    private var deepestArticleIndex: Int = 0

    private fun handleScrollToArticle(index: Int) {
        val previousIndex: Int = mutableState.value.currentArticleIndex
        val target: Int = index.coerceIn(0, mutableState.value.articles.lastIndex.coerceAtLeast(0))
        if (target != previousIndex) reportArticleLeft(previousIndex)
        mutableState.update { state -> state.copy(currentArticleIndex = target) }
        reportDepth(target)
        // Ahead of the reader rather than at the end of the feed: a page has to
        // be there before the last card is, or the swipe that would have
        // reached it stops dead instead.
        maybeLoadNextPage(target)
    }

    /**
     * A card left the screen: report it as read or skipped by how long it was
     * visible. The ratio is what says whether the ranking is any good.
     */
    private fun reportArticleLeft(index: Int) {
        val now: Long = currentTimeMillis()
        val visibleMillis: Long = now - articleShownAtMillis
        articleShownAtMillis = now

        val article = mutableState.value.articles.getOrNull(index) ?: return
        val category: String = article.category.apiValue
        val source: String = article.source.name.value
        val wasRead: Boolean = visibleMillis >= READ_THRESHOLD_MILLIS
        analytics.logEvent(
            if (wasRead) {
                AnalyticsEvent.ArticleViewed(
                    category = category,
                    source = source,
                    language = mutableState.value.selectedLanguage.code,
                )
            } else {
                AnalyticsEvent.ArticleSkipped(category = category, source = source)
            }
        )
        // Shown is shown. The three-second threshold splits read from skipped
        // for analytics, and it used to gate this too — which meant a reader
        // moving quickly marked nothing, and every refresh handed them back the
        // same cards in the same order because the ranking had nothing to sink.
        // A story they swiped past is a story they have already been offered.
        seenArticlesStore.markSeen(article.articleUrl.value)
    }

    /**
     * Reports how far a session gets, at milestones rather than every card —
     * this is the number that decides whether pagination is worth building.
     */
    private fun reportDepth(index: Int) {
        if (index <= deepestArticleIndex) return
        deepestArticleIndex = index
        // A reader who has scrolled this far has already decided the app is
        // worth their time — this is a far better moment to ask for the
        // permission than the cold start, before a single headline was on
        // screen. Independent of the analytics milestone below, and it fires
        // at most once, guarded inside the handler itself.
        if (index == PERMISSION_PROMPT_DEPTH) {
            processEvent(FeedUiEvent.RequestNotificationPermissionIfDue)
        }
        if (index % DEPTH_MILESTONE != 0) return
        analytics.logEvent(
            AnalyticsEvent.FeedDepthReached(
                depth = index,
                category = mutableState.value.selectedCategory.apiValue,
            )
        )
    }

    /**
     * Fired from [reportDepth] once a reader has read enough to make an
     * informed choice — asking before a single headline is on screen is where
     * opt-in rates go to die. Fires at most once, ever.
     */
    private fun handleRequestNotificationPermissionIfDue() {
        feedScope.launch {
            if (settingsManager.notificationPromptSeen()) return@launch
            settingsManager.markNotificationPromptSeen()
            mutableEffect.emit(FeedUiEffect.RequestNotificationPermission)
        }
    }

    private fun handleRefreshNews() {
        mutableState.update { state -> state.copy(isRefreshing = true) }
        loadNews()
    }

    private fun handleRetryLoading() {
        mutableState.update { state ->
            state.copy(isLoading = true, errorMessage = null)
        }
        loadNews()
    }

    private fun handleDismissError() {
        mutableState.update { state -> state.copy(errorMessage = null) }
    }

    private fun handleNewsError(errorMessage: String) {
        val servedFromCache: Boolean = mutableState.value.articles.isNotEmpty()
        analytics.logEvent(AnalyticsEvent.FeedLoadFailed(errorMessage, servedFromCache))
        if (!servedFromCache) analytics.recordError("Feed load failed: $errorMessage")
        mutableState.update { state ->
            state.copy(
                isLoading = false,
                isRefreshing = false,
                isBackgroundRefreshing = false,
                errorMessage = errorMessage,
                isOfflineMode = true
            )
        }
    }

    private fun loadNewsWithCache() {
        val request = currentRequest()
        val generation = startNewFeed()
        showCachedFeed(request)
        feedScope.launch {
            fetchNewsInBackground(request, generation)
        }
    }

    private fun currentRequest(): GetTopHeadlinesRequest {
        val currentState: FeedUiState = mutableState.value
        return GetTopHeadlinesRequest(
            category = currentState.selectedCategory,
            country = currentState.selectedCountry.code,
            countryName = currentState.selectedCountry.displayName,
            language = currentState.selectedLanguage.code,
            useCountry = currentTab == NavigationTab.COUNTRIES
        )
    }

    /**
     * Something to read while the network answers. The cached copy carries its
     * own next-page link, so a reader who opened the app offline can still
     * scroll past the end of it once the connection comes back.
     */
    private fun showCachedFeed(request: GetTopHeadlinesRequest) {
        val cachedResult = getTopHeadlinesUseCase.getCached(request)
        if (cachedResult is NewsResult.Success && cachedResult.data.articles.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    isLoading = false,
                    feedRevision = state.feedRevision + 1,
                    articles = applyRanking(cachedResult.data.articles),
                    nextPageFile = cachedResult.data.nextPage,
                    isLoadingNextPage = false,
                    nextPageFailed = false,
                    errorMessage = null,
                    isBackgroundRefreshing = true
                )
            }
        } else {
            mutableState.update { state ->
                state.copy(
                    isLoading = true,
                    articles = emptyList(),
                    nextPageFile = null,
                    isLoadingNextPage = false,
                    nextPageFailed = false
                )
            }
        }
    }

    private suspend fun fetchNewsInBackground(
        request: GetTopHeadlinesRequest,
        generation: Int,
        preserveReaderPosition: Boolean = false,
    ) {
        val result = getTopHeadlinesUseCase.execute(request)
        if (generation != feedGeneration) return
        when (result) {
            is NewsResult.Success -> {
                mutableState.update { state ->
                    state.withLoadedFeed(
                        articles = applyRanking(result.data.articles),
                        nextPageFile = result.data.nextPage,
                        preserveReaderPosition = preserveReaderPosition,
                    )
                }
            }
            is NewsResult.Error -> {
                val hasArticles: Boolean = mutableState.value.articles.isNotEmpty()
                if (hasArticles) {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isBackgroundRefreshing = false,
                            isOfflineMode = true
                        )
                    }
                } else {
                    handleNewsError(result.error.message)
                }
            }
        }
    }

    private fun loadNews() {
        val request = currentRequest()
        val generation = startNewFeed()
        feedScope.launch {
            val result = getTopHeadlinesUseCase.execute(request)
            // A refresh that landed after the reader had already moved on
            // belongs to a feed that no longer exists, whether it succeeded or
            // failed.
            if (generation != feedGeneration) return@launch
            when (result) {
                is NewsResult.Success -> {
                    mutableState.update { state ->
                        state.withLoadedFeed(
                            articles = applyRanking(result.data.articles),
                            nextPageFile = result.data.nextPage,
                            preserveReaderPosition = false,
                        )
                    }
                    resetArticleTracking()
                }
                is NewsResult.Error -> {
                    handleNewsError(result.error.message)
                }
            }
        }
    }

    /**
     * Fetches the page below what is loaded, if the reader is close enough to
     * the end of it to need one. Called on every card change, and cheap when
     * the answer is no.
     */
    private fun maybeLoadNextPage(index: Int) {
        val state = mutableState.value
        val pageFile = state.nextPageFile ?: return
        val due = shouldLoadNextPage(
            currentIndex = index,
            loadedCount = state.articles.size,
            hasNextPage = true,
            isLoading = state.isLoadingNextPage,
            failed = state.nextPageFailed,
        )
        if (!due) return
        loadNextPage(pageFile)
    }

    private fun loadNextPage(pageFile: String) {
        val generation = feedGeneration
        mutableState.update { it.copy(isLoadingNextPage = true, nextPageFailed = false) }
        feedScope.launch {
            when (val result = getTopHeadlinesUseCase.nextPage(pageFile)) {
                is NewsResult.Success -> handleNextPageLoaded(result.data, pageFile, generation)
                is NewsResult.Error -> {
                    if (generation != feedGeneration) return@launch
                    analytics.logEvent(
                        AnalyticsEvent.FeedLoadFailed(result.error.message, servedFromCache = true)
                    )
                    // The feed on screen is untouched and keeps its cursor: the
                    // reader carries on reading what they have, and reaching the
                    // last card tries the same page again.
                    mutableState.update {
                        it.copy(isLoadingNextPage = false, nextPageFailed = true)
                    }
                }
            }
        }
    }

    /**
     * Appends a page. Two things are deliberate here: the articles already on
     * screen are passed through untouched, and the ranking is applied to the
     * new page alone. Re-ranking the whole feed would move the card under the
     * reader's thumb, which is the one thing a vertical pager cannot do.
     */
    private fun handleNextPageLoaded(page: FeedPage, requestedFrom: String, generation: Int) {
        // The feed moved on while this was in flight — a refresh landed, or the
        // reader switched category — so this page belongs to a list that is no
        // longer on screen. Whatever replaced it has already cleared the
        // in-flight flag on its own way in.
        if (generation != feedGeneration) return
        if (mutableState.value.nextPageFile != requestedFrom) return
        mutableState.update { state ->
            state.copy(
                articles = appendPage(state.articles, applyRanking(page.articles)),
                nextPageFile = page.nextPage,
                isLoadingNextPage = false,
                nextPageFailed = false
            )
        }
    }

    private fun handleRetryNextPage() {
        val pageFile = mutableState.value.nextPageFile ?: return
        if (mutableState.value.isLoadingNextPage) return
        loadNextPage(pageFile)
    }

    /** A new feed is a new session for depth purposes. */
    private fun resetArticleTracking() {
        articleShownAtMillis = currentTimeMillis()
        deepestArticleIndex = 0
    }

    private companion object {
        /** Below this, a card counts as skipped rather than read. */
        const val READ_THRESHOLD_MILLIS: Long = 3_000

        /** Depth is reported every this many cards, not on every swipe. */
        const val DEPTH_MILESTONE: Int = 10

        /** Cards deep before the notification permission is worth asking for. */
        const val PERMISSION_PROMPT_DEPTH: Int = 5

    }
}
