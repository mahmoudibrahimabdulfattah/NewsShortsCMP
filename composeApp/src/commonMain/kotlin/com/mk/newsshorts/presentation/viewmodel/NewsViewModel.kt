package com.mk.newsshorts.presentation.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.mk.newsshorts.auth.AuthSession
import com.mk.newsshorts.data.repository.SavedArticles
import com.mk.newsshorts.data.local.SeenArticlesStore
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.local.currentTimeMillis
import com.mk.newsshorts.domain.feed.FeedInvalidator
import com.mk.newsshorts.domain.feed.InvalidationReason
import com.mk.newsshorts.domain.feed.appendPage
import com.mk.newsshorts.domain.feed.shouldLoadNextPage
import com.mk.newsshorts.domain.ranking.deprioritiseSeen
import com.mk.newsshorts.domain.repository.ArticleLookup
import com.mk.newsshorts.domain.repository.InboxReadMarker
import com.mk.newsshorts.sync.AccountSyncUseCase
import com.mk.newsshorts.sync.SyncOutcome
import com.mk.newsshorts.sync.SyncPublisher
import com.mk.newsshorts.sync.SyncedSettings
import com.mk.newsshorts.sync.apply
import com.mk.newsshorts.sync.toSyncedSettings
import com.mk.newsshorts.data.remote.RemoteConfigClient
import com.mk.newsshorts.security.DeviceIntegrityInspector
import com.mk.newsshorts.security.IntegrityPolicy
import com.mk.newsshorts.security.SecurityNotice
import com.mk.newsshorts.security.securityNoticeFor
import com.mk.newsshorts.security.securityReasonFor
import com.mk.newsshorts.data.remote.isDebugBuild
import com.mk.newsshorts.data.remote.requiredUpdateFor
import com.mk.newsshorts.domain.model.FeedLanguage
import com.mk.newsshorts.domain.model.FeedPage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.preferences.openingCategory
import com.mk.newsshorts.domain.preferences.orderedCategories
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesRequest
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.navigation.ArticleDeepLink
import com.mk.newsshorts.navigation.ArticleDeepLinks
import com.mk.newsshorts.data.remote.SharePageResolver
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.navigation.PendingLink
import com.mk.newsshorts.navigation.toNewsArticle
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.presentation.localization.urlInLanguage
import com.mk.newsshorts.presentation.mvi.ArticleOpenOrigin
import com.mk.newsshorts.presentation.mvi.CountryOption
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.presentation.mvi.NewsUiEffect
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState
import com.mk.newsshorts.presentation.mvi.OnboardingStep
import com.mk.newsshorts.presentation.mvi.Overlay

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
        currentState: NewsUiState,
        selectedCategory: NewsCategory,
    ): RememberedCategoryFeed? {
        remember(currentState)
        return find(selectedCategory, currentState.selectedLanguage.code)
    }

    fun clear() {
        feeds.clear()
    }

    private fun remember(state: NewsUiState) {
        if (state.currentTab != NavigationTab.FOR_YOU || state.articles.isEmpty()) return
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

internal fun NewsUiState.withSelectedCategory(
    category: NewsCategory,
    remembered: RememberedCategoryFeed?,
): NewsUiState {
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

internal fun NewsUiState.withLoadedFeed(
    articles: List<NewsArticle>,
    nextPageFile: String?,
    preserveReaderPosition: Boolean,
): NewsUiState {
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

class NewsViewModel(
    private val getTopHeadlinesUseCase: GetTopHeadlinesUseCase,
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val deepLinkBus: DeepLinkBus,
    private val savedArticles: SavedArticles,
    private val accountSync: AccountSyncUseCase,
    private val authSession: AuthSession,
    private val syncPublisher: SyncPublisher,
    private val feedInvalidator: FeedInvalidator,
    private val articleLookup: ArticleLookup,
    private val seenArticlesStore: SeenArticlesStore,
    private val remoteConfigClient: RemoteConfigClient,
    private val deviceIntegrityInspector: DeviceIntegrityInspector,
    private val sharePageResolver: SharePageResolver,
    private val inboxReadMarker: InboxReadMarker,
) : BaseViewModel() {

    private val mutableState: MutableStateFlow<NewsUiState> = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = mutableState.asStateFlow()

    private val mutableEffect: MutableSharedFlow<NewsUiEffect> = MutableSharedFlow()
    val uiEffect: SharedFlow<NewsUiEffect> = mutableEffect.asSharedFlow()

    private val categoryFeedMemory = CategoryFeedMemory()

    /** Toast text is built here rather than in the UI, so it needs the locale too. */
    private fun strings(): AppStrings =
        getStrings(AppLocale.fromCode(settingsManager.preferences.value.appLocale))

    init {
        loadSavedSettings()
        observeDeepLinks()
        observeFeedInvalidations()
        observeAuthState()
    }

    private var accountSyncJob: Job? = null
    private var activeAccountSyncUid: String? = null

    private fun observeAuthState() {
        viewModelScope.launch {
            authSession.user.collect { user ->
                val uid = user?.uid
                if (uid != null && uid == activeAccountSyncUid) return@collect
                accountSyncJob?.cancel()
                // Anything still queued or in the air belongs to the account
                // that just went away. A write that was legitimately current
                // when it left can still land minutes later, under whoever is
                // signed in by then.
                syncPublisher.discardQueued()
                activeAccountSyncUid = uid
                accountSyncJob = if (uid == null) {
                    null
                } else {
                    launch {
                        val outcome = accountSync()
                        if (authSession.user.value?.uid == uid) applySyncOutcome(outcome)
                    }
                }
            }
        }
    }

    /**
     * Read from the store, never from [mutableState]. The UI state starts on
     * hardcoded defaults and is filled in by `loadSavedSettings` in its own
     * coroutine, so a sign-in that lands first would have pushed English, US
     * and "system" over whatever the reader had actually chosen. The store has
     * the real values from the moment it is constructed.
     */
    private fun currentSyncedSettings(): SyncedSettings =
        settingsManager.preferences.value.toSyncedSettings()

    private suspend fun applySyncOutcome(outcome: SyncOutcome) {
        if (outcome.settings == null) {
            replaceSavedArticlesFromSync(outcome.saved)
            return
        }
        applySyncedSettings(settings = outcome.settings, saved = outcome.saved)
    }

    /** The remote copy becomes the local one — this is the "remote wins" side of sync. */
    private suspend fun applySyncedSettings(settings: SyncedSettings, saved: List<NewsArticle>) {
        settingsManager.apply(settings)
        savedArticles.replaceAll(saved)
        feedInvalidator.invalidate(InvalidationReason.SyncApplied)
    }

    private fun replaceSavedArticlesFromSync(articles: List<NewsArticle>) {
        if (articles != savedArticles.saved.value) {
            savedArticles.replaceAll(articles)
        }
    }

    private fun publishSettingsIfSignedIn() {
        syncPublisher.publishSettings(currentSyncedSettings())
    }

    private fun observeDeepLinks() {
        viewModelScope.launch {
            deepLinkBus.pending.collect { pending ->
                when (pending) {
                    null -> return@collect
                    is PendingLink.Article -> processEvent(NewsUiEvent.OpenDeepLink(pending.link))
                    is PendingLink.SharePage -> processEvent(NewsUiEvent.OpenSharePage(pending.url))
                }
                // Both this and the ViewModel outlive the Activity, so an
                // unconsumed link would reopen the screen on every resume.
                deepLinkBus.consume()
            }
        }
    }

    private fun observeFeedInvalidations() {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
            loadNewsWithCache()
        }
    }

    /**
     * Read-then-newest-first is not enough on its own — a returning reader
     * would just see yesterday's top story again. Applied at every site that
     * assigns [NewsUiState.articles], never to the list already on screen: a
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

    fun processEvent(event: NewsUiEvent) {
        when (event) {
            is NewsUiEvent.SelectCategory -> handleSelectCategory(event.category)
            is NewsUiEvent.SelectCountry -> handleSelectCountry(event.country)
            is NewsUiEvent.SelectLanguage -> handleSelectLanguage(event.language)
            is NewsUiEvent.SelectTab -> handleSelectTab(event.tab)
            is NewsUiEvent.ScrollToArticle -> handleScrollToArticle(event.index)
            is NewsUiEvent.OpenArticleDetails -> handleOpenArticleDetails(event.article, event.origin)
            NewsUiEvent.CloseArticleDetails -> handleCloseOverlay()
            is NewsUiEvent.OpenOverlay -> handleOpenOverlay(event.overlay)
            NewsUiEvent.CloseOverlay -> handleCloseOverlay()
            NewsUiEvent.OpenArticleSource -> handleOpenArticleSource()
            NewsUiEvent.OpenPrivacyPolicy -> viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.OpenUrl(privacyPolicyUrl()))
            }
            is NewsUiEvent.OpenDeepLink -> handleOpenDeepLink(event.link)
            is NewsUiEvent.OpenSharePage -> handleOpenSharePage(event.url)
            is NewsUiEvent.ShareArticle -> handleShareArticle(event.article)
            is NewsUiEvent.SaveArticle -> Unit
            is NewsUiEvent.RemoveSavedArticle -> Unit
            NewsUiEvent.OpenSearch -> handleOpenSearch()
            NewsUiEvent.RefreshNews -> handleRefreshNews()
            NewsUiEvent.RetryLoading -> handleRetryLoading()
            NewsUiEvent.RetryNextPage -> handleRetryNextPage()
            NewsUiEvent.DismissError -> handleDismissError()
            NewsUiEvent.RequestNotificationPermissionIfDue -> handleRequestNotificationPermissionIfDue()
        }
    }

    private fun handleSelectCategory(category: NewsCategory) {
        if (category == mutableState.value.selectedCategory) return
        val remembered = categoryFeedMemory.rememberAndFind(mutableState.value, category)
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
            viewModelScope.launch {
                fetchNewsInBackground(
                    request = request,
                    generation = restoreGeneration,
                    preserveReaderPosition = true,
                )
            }
        }
    }

    private fun handleSelectCountry(country: CountryOption) {
        if (country == mutableState.value.selectedCountry) return
        analytics.logEvent(AnalyticsEvent.CountrySelected(country.code))
        resetArticleTracking()
        viewModelScope.launch {
            settingsManager.saveSelectedCountry(country.code)
            publishSettingsIfSignedIn()
            feedInvalidator.invalidate(InvalidationReason.CountryChanged)
        }
    }

    private fun loadNewsForCountryWithCache(country: CountryOption) {
        val currentState: NewsUiState = mutableState.value
        val request = GetTopHeadlinesRequest(
            category = currentState.selectedCategory,
            country = country.code,
            countryName = country.displayName,
            language = currentState.selectedLanguage.code,
            useCountry = true
        )
        val generation = startNewFeed()
        showCachedFeed(request)
        viewModelScope.launch {
            fetchNewsInBackground(request, generation)
        }
    }

    private fun handleSelectLanguage(language: LanguageOption) {
        if (language == mutableState.value.selectedLanguage) return
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.NewsLanguageChanged(language.code))
            analytics.setProperty("news_language", language.code)
            settingsManager.saveNewsLanguage(language.code)
            publishSettingsIfSignedIn()
            feedInvalidator.invalidate(InvalidationReason.LanguageChanged)
            mutableEffect.emit(NewsUiEffect.ShowToast(strings().languageNames[language.code] ?: language.displayName))
        }
    }

    private fun handleSelectTab(tab: NavigationTab) {
        if (tab == mutableState.value.currentTab) {
            // Tapping the tab you are already on is how every feed app spells
            // "take me back to the top", and forty cards deep that is otherwise
            // forty swipes. Refreshing rather than only scrolling, because a
            // reader who has come all the way back up is asking what is new —
            // and the scroll falls out of it, since a refresh replaces the feed
            // and the pager follows [NewsUiState.feedRevision] to the top.
            if (tab != NavigationTab.PROFILE) handleRefreshNews()
            return
        }
        val needsLoading: Boolean = tab != NavigationTab.PROFILE
        mutableState.update { state ->
            state.copy(
                currentTab = tab,
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
            processEvent(NewsUiEvent.RequestNotificationPermissionIfDue)
        }
        if (index % DEPTH_MILESTONE != 0) return
        analytics.logEvent(
            AnalyticsEvent.FeedDepthReached(
                depth = index,
                category = mutableState.value.selectedCategory.apiValue,
            )
        )
    }

    private fun handleOpenArticleDetails(article: NewsArticle, origin: ArticleOpenOrigin) {
        handleOpenOverlay(Overlay.Details(article, origin))
        analytics.logEvent(
            AnalyticsEvent.ArticleDetailsOpened(
                category = article.category.apiValue,
                source = article.source.name.value,
                origin = origin.analyticsValue,
            )
        )
    }

    private fun handleOpenOverlay(overlay: Overlay) {
        mutableState.update { state ->
            if (overlay == Overlay.SignIn && Overlay.SignIn in state.overlays) {
                state
            } else {
                state.copy(overlays = state.overlays + overlay)
            }
        }
    }

    /** Pops whatever is on top — the details screen, Settings, Saved, or Search. */
    private fun handleCloseOverlay() {
        mutableState.update { state ->
            state.copy(overlays = state.overlays.dropLast(1))
        }
    }

    private fun handleOpenSearch() {
        handleOpenOverlay(Overlay.Search)
    }

    /** The policy page picks its language from this, not from the browser. */
    private fun privacyPolicyUrl(): String =
        urlInLanguage(BuildConfig.PRIVACY_POLICY_URL, settingsManager.preferences.value.appLocale)

    private fun handleOpenArticleSource() {
        val article = mutableState.value.articleDetails?.article ?: return
        analytics.logEvent(
            AnalyticsEvent.ArticleSourceOpened(article.category.apiValue, article.source.name.value)
        )
        viewModelScope.launch {
            mutableEffect.emit(NewsUiEffect.OpenUrl(article.articleUrl.value))
        }
    }

    /**
     * Turns a shared landing page into the article it names, and opens it.
     *
     * Falls back to opening the page itself, which is not a failure state so
     * much as the experience everyone without the app already gets: it renders
     * the story, offers the source, and offers the app. That covers a reader
     * who is offline, a link older than the published archive, and a site
     * mid-deploy — none of which should end at a blank feed.
     */
    private fun handleOpenSharePage(pageUrl: String) {
        viewModelScope.launch {
            val link = sharePageResolver.resolve(pageUrl)
            if (link != null) processEvent(NewsUiEvent.OpenDeepLink(link))
            else mutableEffect.emit(NewsUiEffect.OpenUrl(pageUrl))
        }
    }

    /**
     * Prefers a copy already in the feed, saved list, or cached feed — those carry
     * the real image and timestamp — and falls back to rebuilding the article from
     * the link, which is all a cold start has.
     */
    private fun handleOpenDeepLink(link: ArticleDeepLink) {
        viewModelScope.launch {
            val state = mutableState.value
            val article = state.articles.firstOrNull { it.articleUrl.value == link.url }
                ?: savedArticles.saved.value.firstOrNull { it.articleUrl.value == link.url }
                ?: articleLookup.find(link.url)
                ?: link.toNewsArticle()
                ?: return@launch
            // A shared link marks itself, so notification_opened stays a count of
            // notifications rather than of every way into the details screen.
            val fromShare = link.referrer == ArticleDeepLinks.SHARE_REFERRER
            if (!fromShare) {
                analytics.logEvent(
                    AnalyticsEvent.NotificationOpened(article.category.apiValue, article.source.name.value)
                )
            }
            // The reader has gone into the story, so the inbox row for it is read —
            // whether they came from a row, from the notification still sitting in
            // the tray, or from a shared link that happened to also be pushed.
            //
            // Marked before the screen opens rather than after it closes: a mark
            // that waited for them to come back would still be there if they left
            // from the details screen instead. And written to the store first, so a
            // cold start from a tray tap records it even though the published list
            // has not arrived yet — when it does, the row is already read.
            inboxReadMarker.markRead(article.articleUrl.value)

            handleOpenArticleDetails(
                article,
                if (fromShare) ArticleOpenOrigin.SHARE else ArticleOpenOrigin.PUSH,
            )
        }
    }

    private fun handleShareArticle(article: NewsArticle) {
        analytics.logEvent(AnalyticsEvent.ArticleShared(article.category.apiValue))
        viewModelScope.launch {
            mutableEffect.emit(
                NewsUiEffect.ShareContent(
                    title = article.title.value,
                    // The share link opens the app rather than the publisher,
                    // so a shared story brings the reader back here.
                    url = ArticleDeepLinks.shareUrl(
                        article = article,
                        baseUrl = BuildConfig.SHARE_BASE_URL,
                        // The article's language, so the landing page matches it
                        // rather than defaulting to Arabic.
                        language = FeedLanguage.resolve(
                            mutableState.value.selectedLanguage.code
                        ),
                    ),
                    chooserTitle = strings().shareArticle,
                )
            )
        }
    }

    /**
     * Fired from [reportDepth] once a reader has read enough to make an
     * informed choice — asking before a single headline is on screen is where
     * opt-in rates go to die. Fires at most once, ever.
     */
    private fun handleRequestNotificationPermissionIfDue() {
        viewModelScope.launch {
            if (settingsManager.notificationPromptSeen()) return@launch
            settingsManager.markNotificationPromptSeen()
            mutableEffect.emit(NewsUiEffect.RequestNotificationPermission)
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
        viewModelScope.launch {
            fetchNewsInBackground(request, generation)
        }
    }

    private fun currentRequest(): GetTopHeadlinesRequest {
        val currentState: NewsUiState = mutableState.value
        return GetTopHeadlinesRequest(
            category = currentState.selectedCategory,
            country = currentState.selectedCountry.code,
            countryName = currentState.selectedCountry.displayName,
            language = currentState.selectedLanguage.code,
            useCountry = currentState.currentTab == NavigationTab.COUNTRIES
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
