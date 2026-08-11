package com.mk.newsshorts.presentation.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.local.currentTimeMillis
import com.mk.newsshorts.domain.model.FeedLanguage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesRequest
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.navigation.ArticleDeepLink
import com.mk.newsshorts.navigation.ArticleDeepLinks
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.navigation.toNewsArticle
import com.mk.newsshorts.notifications.PushSubscriber
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.presentation.mvi.ArticleDetails
import com.mk.newsshorts.presentation.mvi.ArticleOpenOrigin
import com.mk.newsshorts.presentation.mvi.CountryOption
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.presentation.mvi.NewsUiEffect
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState

class NewsViewModel(
    private val getTopHeadlinesUseCase: GetTopHeadlinesUseCase,
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val pushSubscriber: PushSubscriber,
    private val deepLinkBus: DeepLinkBus,
) : BaseViewModel() {

    private val mutableState: MutableStateFlow<NewsUiState> = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = mutableState.asStateFlow()

    private val mutableEffect: MutableSharedFlow<NewsUiEffect> = MutableSharedFlow()
    val uiEffect: SharedFlow<NewsUiEffect> = mutableEffect.asSharedFlow()

    /** Toast text is built here rather than in the UI, so it needs the locale too. */
    private fun strings(): AppStrings = getStrings(mutableState.value.appLocale)

    init {
        loadSavedSettings()
        observeDeepLinks()
    }

    private fun observeDeepLinks() {
        viewModelScope.launch {
            deepLinkBus.pending.collect { link ->
                if (link == null) return@collect
                processEvent(NewsUiEvent.OpenDeepLink(link))
                // Both this and the ViewModel outlive the Activity, so an
                // unconsumed link would reopen the screen on every resume.
                deepLinkBus.consume()
            }
        }
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val savedNewsLanguage: String = settingsManager.newsLanguageFlow.first()
            val savedAppLocale: String = settingsManager.appLocaleFlow.first()
            val savedCountry: String = settingsManager.selectedCountryFlow.first()
            val newsLanguage: LanguageOption = LanguageOption.entries.find { it.code == savedNewsLanguage }
                ?: LanguageOption.ENGLISH
            val appLocale: AppLocale = AppLocale.fromCode(savedAppLocale)
            val country: CountryOption = CountryOption.entries.find { it.code == savedCountry }
                ?: CountryOption.UNITED_STATES
            mutableState.update { state ->
                state.copy(
                    selectedLanguage = newsLanguage,
                    appLocale = appLocale,
                    selectedCountry = country,
                    isFirstLaunch = false
                )
            }
            pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(newsLanguage.code))
            loadNewsWithCache()
        }
    }

    fun processEvent(event: NewsUiEvent) {
        when (event) {
            is NewsUiEvent.SelectCategory -> handleSelectCategory(event.category)
            is NewsUiEvent.SelectCountry -> handleSelectCountry(event.country)
            is NewsUiEvent.SelectLanguage -> handleSelectLanguage(event.language)
            is NewsUiEvent.SelectAppLocale -> handleSelectAppLocale(event.locale)
            is NewsUiEvent.SelectTab -> handleSelectTab(event.tab)
            is NewsUiEvent.ScrollToArticle -> handleScrollToArticle(event.index)
            is NewsUiEvent.OpenArticleDetails -> handleOpenArticleDetails(event.article, event.origin)
            NewsUiEvent.CloseArticleDetails -> handleCloseArticleDetails()
            NewsUiEvent.OpenArticleSource -> handleOpenArticleSource()
            is NewsUiEvent.OpenDeepLink -> handleOpenDeepLink(event.link)
            is NewsUiEvent.ShareArticle -> handleShareArticle(event.article)
            is NewsUiEvent.SaveArticle -> handleSaveArticle(event.article)
            is NewsUiEvent.RemoveSavedArticle -> handleRemoveSavedArticle(event.article)
            NewsUiEvent.RefreshNews -> handleRefreshNews()
            NewsUiEvent.RetryLoading -> handleRetryLoading()
            NewsUiEvent.DismissError -> handleDismissError()
            NewsUiEvent.NavigateToSavedArticles -> handleNavigateToSavedArticles()
            NewsUiEvent.NavigateToLanguageSettings -> handleNavigateToLanguageSettings()
        }
    }

    private fun handleSelectCategory(category: NewsCategory) {
        if (category == mutableState.value.selectedCategory) return
        mutableState.update { state ->
            state.copy(
                selectedCategory = category,
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        analytics.logEvent(AnalyticsEvent.CategorySelected(category.apiValue))
        resetArticleTracking()
        loadNewsWithCache()
    }

    private fun handleSelectCountry(country: CountryOption) {
        if (country == mutableState.value.selectedCountry) return
        mutableState.update { state ->
            state.copy(
                selectedCountry = country,
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        analytics.logEvent(AnalyticsEvent.CountrySelected(country.code))
        resetArticleTracking()
        viewModelScope.launch {
            settingsManager.saveSelectedCountry(country.code)
        }
        loadNewsForCountryWithCache(country)
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
        val cachedResult = getTopHeadlinesUseCase.getCached(request)
        if (cachedResult is NewsResult.Success && cachedResult.data.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    isLoading = false,
                    articles = cachedResult.data,
                    errorMessage = null,
                    isBackgroundRefreshing = true
                )
            }
        } else {
            mutableState.update { state ->
                state.copy(isLoading = true, articles = emptyList())
            }
        }
        viewModelScope.launch {
            fetchNewsInBackground(request)
        }
    }

    private fun handleSelectLanguage(language: LanguageOption) {
        if (language == mutableState.value.selectedLanguage) return
        mutableState.update { state ->
            state.copy(
                selectedLanguage = language,
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.NewsLanguageChanged(language.code))
            analytics.setProperty("news_language", language.code)
            pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(language.code))
            settingsManager.saveNewsLanguage(language.code)
            mutableEffect.emit(NewsUiEffect.ShowToast(strings().languageNames[language.code] ?: language.displayName))
        }
        loadNewsWithCache()
    }

    private fun handleSelectAppLocale(locale: AppLocale) {
        if (locale == mutableState.value.appLocale) return
        mutableState.update { state ->
            state.copy(appLocale = locale)
        }
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.AppLanguageChanged(locale.code))
            analytics.setProperty("app_language", locale.code)
            settingsManager.saveAppLocale(locale.code)
            val newStrings = getStrings(locale)
            val languageName = newStrings.languageNames[locale.code] ?: locale.displayName
            mutableEffect.emit(
                NewsUiEffect.ShowToast("${newStrings.languageChangedTo} $languageName")
            )
        }
    }

    private fun handleSelectTab(tab: NavigationTab) {
        if (tab == mutableState.value.currentTab) return
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
        analytics.logEvent(
            if (visibleMillis >= READ_THRESHOLD_MILLIS) {
                AnalyticsEvent.ArticleViewed(
                    category = category,
                    source = source,
                    language = mutableState.value.selectedLanguage.code,
                )
            } else {
                AnalyticsEvent.ArticleSkipped(category = category, source = source)
            }
        )
    }

    /**
     * Reports how far a session gets, at milestones rather than every card —
     * this is the number that decides whether pagination is worth building.
     */
    private fun reportDepth(index: Int) {
        if (index <= deepestArticleIndex) return
        deepestArticleIndex = index
        if (index % DEPTH_MILESTONE != 0) return
        analytics.logEvent(
            AnalyticsEvent.FeedDepthReached(
                depth = index,
                category = mutableState.value.selectedCategory.apiValue,
            )
        )
    }

    private fun handleOpenArticleDetails(article: NewsArticle, origin: ArticleOpenOrigin) {
        mutableState.update { state ->
            state.copy(articleDetails = ArticleDetails(article, origin))
        }
        analytics.logEvent(
            AnalyticsEvent.ArticleDetailsOpened(
                category = article.category.apiValue,
                source = article.source.name.value,
                origin = origin.analyticsValue,
            )
        )
    }

    private fun handleCloseArticleDetails() {
        mutableState.update { state -> state.copy(articleDetails = null) }
    }

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
     * Prefers a copy already in the feed or the saved list — those carry the
     * real image and timestamp — and falls back to rebuilding the article from
     * the link, which is all a cold start has.
     */
    private fun handleOpenDeepLink(link: ArticleDeepLink) {
        val state = mutableState.value
        val article = state.articles.firstOrNull { it.articleUrl.value == link.url }
            ?: state.savedArticles.firstOrNull { it.articleUrl.value == link.url }
            ?: link.toNewsArticle()
            ?: return
        // A shared link marks itself, so notification_opened stays a count of
        // notifications rather than of every way into the details screen.
        val fromShare = link.referrer == ArticleDeepLinks.SHARE_REFERRER
        if (!fromShare) {
            analytics.logEvent(
                AnalyticsEvent.NotificationOpened(article.category.apiValue, article.source.name.value)
            )
        }
        handleOpenArticleDetails(
            article,
            if (fromShare) ArticleOpenOrigin.SHARE else ArticleOpenOrigin.PUSH,
        )
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

    private fun handleSaveArticle(article: NewsArticle) {
        val currentSaved = mutableState.value.savedArticles.toMutableList()
        val savedIndex: Int = currentSaved.indexOfFirst { it.articleUrl == article.articleUrl }
        val isAlreadySaved: Boolean = savedIndex != -1
        if (isAlreadySaved) {
            currentSaved.removeAt(savedIndex)
            mutableState.update { state ->
                state.copy(savedArticles = currentSaved)
            }
            viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.ShowToast(strings().articleRemoved))
            }
        } else {
            currentSaved.add(0, article)
            mutableState.update { state ->
                state.copy(savedArticles = currentSaved)
            }
            analytics.logEvent(AnalyticsEvent.ArticleSaved(article.category.apiValue))
            viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.ShowToast(strings().articleSaved))
            }
        }
    }

    private fun handleRemoveSavedArticle(article: NewsArticle) {
        val currentSaved = mutableState.value.savedArticles.toMutableList()
        // Matched by URL, the only stable identity an article has.
        val savedIndex = currentSaved.indexOfFirst { it.articleUrl == article.articleUrl }
        if (savedIndex == -1) return
        currentSaved.removeAt(savedIndex)
        mutableState.update { state ->
            state.copy(savedArticles = currentSaved)
        }
        viewModelScope.launch {
            mutableEffect.emit(NewsUiEffect.ShowToast(strings().articleRemoved))
        }
    }

    private fun handleNavigateToSavedArticles() {
        mutableState.update { state ->
            state.copy(currentTab = NavigationTab.PROFILE)
        }
    }

    private fun handleNavigateToLanguageSettings() {
        mutableState.update { state ->
            state.copy(currentTab = NavigationTab.PROFILE)
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
        val currentState: NewsUiState = mutableState.value
        val isCountriesTab: Boolean = currentState.currentTab == NavigationTab.COUNTRIES
        val request = GetTopHeadlinesRequest(
            category = currentState.selectedCategory,
            country = currentState.selectedCountry.code,
            countryName = currentState.selectedCountry.displayName,
            language = currentState.selectedLanguage.code,
            useCountry = isCountriesTab
        )
        val cachedResult = getTopHeadlinesUseCase.getCached(request)
        if (cachedResult is NewsResult.Success && cachedResult.data.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    isLoading = false,
                    articles = cachedResult.data,
                    errorMessage = null,
                    isBackgroundRefreshing = true
                )
            }
        } else {
            mutableState.update { state ->
                state.copy(isLoading = true, articles = emptyList())
            }
        }
        viewModelScope.launch {
            fetchNewsInBackground(request)
        }
    }

    private suspend fun fetchNewsInBackground(request: GetTopHeadlinesRequest) {
        when (val result = getTopHeadlinesUseCase.execute(request)) {
            is NewsResult.Success -> {
                mutableState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isBackgroundRefreshing = false,
                        articles = result.data,
                        errorMessage = null,
                        currentArticleIndex = 0,
                        isOfflineMode = false
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
        viewModelScope.launch {
            val currentState: NewsUiState = mutableState.value
            val isCountriesTab: Boolean = currentState.currentTab == NavigationTab.COUNTRIES
            val request = GetTopHeadlinesRequest(
                category = currentState.selectedCategory,
                country = currentState.selectedCountry.code,
                countryName = currentState.selectedCountry.displayName,
                language = currentState.selectedLanguage.code,
                useCountry = isCountriesTab
            )
            when (val result = getTopHeadlinesUseCase.execute(request)) {
                is NewsResult.Success -> {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            articles = result.data,
                            errorMessage = null,
                            currentArticleIndex = 0,
                            isOfflineMode = false
                        )
                    }
                }
                is NewsResult.Error -> {
                    handleNewsError(result.error.message)
                }
            }
        }
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
    }
}
