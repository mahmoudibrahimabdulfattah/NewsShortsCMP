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
import com.mk.newsshorts.auth.AuthClient
import com.mk.newsshorts.auth.AuthResult
import com.mk.newsshorts.data.local.SavedArticlesStore
import com.mk.newsshorts.data.local.SeenArticlesStore
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.local.currentTimeMillis
import com.mk.newsshorts.domain.ranking.deprioritiseSeen
import com.mk.newsshorts.domain.sync.mergeSavedArticles
import com.mk.newsshorts.sync.RemoteSyncClient
import com.mk.newsshorts.sync.SyncFetch
import com.mk.newsshorts.sync.SyncedSettings
import com.mk.newsshorts.data.remote.RemoteConfigClient
import com.mk.newsshorts.security.DeviceIntegrityInspector
import com.mk.newsshorts.security.IntegrityPolicy
import com.mk.newsshorts.security.SecurityNotice
import com.mk.newsshorts.security.securityNoticeFor
import com.mk.newsshorts.security.securityReasonFor
import com.mk.newsshorts.data.remote.isDebugBuild
import com.mk.newsshorts.data.remote.requiredUpdateFor
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
import com.mk.newsshorts.presentation.mvi.ArticleOpenOrigin
import com.mk.newsshorts.presentation.mvi.CountryOption
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.presentation.mvi.NewsUiEffect
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState
import com.mk.newsshorts.presentation.mvi.NotificationTier
import com.mk.newsshorts.presentation.mvi.Overlay
import com.mk.newsshorts.presentation.mvi.ThemeMode

class NewsViewModel(
    private val getTopHeadlinesUseCase: GetTopHeadlinesUseCase,
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val pushSubscriber: PushSubscriber,
    private val deepLinkBus: DeepLinkBus,
    private val savedArticlesStore: SavedArticlesStore,
    private val seenArticlesStore: SeenArticlesStore,
    private val remoteConfigClient: RemoteConfigClient,
    private val deviceIntegrityInspector: DeviceIntegrityInspector,
    private val authClient: AuthClient,
    private val remoteSyncClient: RemoteSyncClient,
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
        checkForRequiredUpdate()
        observeAuthState()
    }

    /**
     * One listener covers sign-in, sign-out and deletion, since [AuthClient]
     * reports all three through the same flow. A newly-non-null uid — whether
     * from an interactive sign-in or a session Firebase restored at cold
     * start — triggers a sync; the point is the transition into "signed in",
     * not the particular action that caused it.
     */
    private fun observeAuthState() {
        viewModelScope.launch {
            var previousUid: String? = null
            authClient.currentUser.collect { user ->
                mutableState.update { it.copy(authUser = user, authInProgress = false) }
                if (user != null && user.uid != previousUid) {
                    syncOnSignIn(user.uid)
                }
                previousUid = user?.uid
            }
        }
    }

    /**
     * Union the saved articles, let settings follow the account. Never the
     * other way — see `mergeSavedArticles` for why a union is the only
     * direction that cannot lose a bookmark.
     */
    private suspend fun syncOnSignIn(uid: String) {
        when (val remoteSaved = remoteSyncClient.fetchSavedArticles(uid)) {
            is SyncFetch.Found -> {
                val merged = mergeSavedArticles(local = mutableState.value.savedArticles, remote = remoteSaved.value)
                mutableState.update { it.copy(savedArticles = merged) }
                persistSavedArticles(merged)
                remoteSyncClient.pushSavedArticles(uid, merged)
            }
            SyncFetch.NotFound -> remoteSyncClient.pushSavedArticles(uid, mutableState.value.savedArticles)
            // Offline or a transient failure: neither side is touched, and the
            // next launch (or the next save) tries again.
            SyncFetch.Unavailable -> Unit
        }

        when (val remoteSettings = remoteSyncClient.fetchSettings(uid)) {
            is SyncFetch.Found -> applySyncedSettings(remoteSettings.value)
            SyncFetch.NotFound -> remoteSyncClient.pushSettings(uid, currentSyncedSettings())
            SyncFetch.Unavailable -> Unit
        }
    }

    private fun currentSyncedSettings(): SyncedSettings {
        val state = mutableState.value
        return SyncedSettings(
            newsLanguage = state.selectedLanguage.code,
            appLocale = state.appLocale.code,
            selectedCountry = state.selectedCountry.code,
            themeMode = state.themeMode.name.lowercase(),
            notificationsEnabled = state.notificationsEnabled,
            notifyBreaking = state.notifyBreaking,
            notifyTopStory = state.notifyTopStory,
            notifyReminder = state.notifyReminder,
        )
    }

    /** The remote copy becomes the local one — this is the "remote wins" side of sync. */
    private suspend fun applySyncedSettings(settings: SyncedSettings) {
        val newsLanguage = LanguageOption.entries.find { it.code == settings.newsLanguage }
            ?: mutableState.value.selectedLanguage
        val appLocale = AppLocale.fromCode(settings.appLocale)
        val country = CountryOption.entries.find { it.code == settings.selectedCountry }
            ?: mutableState.value.selectedCountry
        val themeMode = ThemeMode.entries.find { it.name.equals(settings.themeMode, ignoreCase = true) }
            ?: mutableState.value.themeMode

        settingsManager.saveNewsLanguage(newsLanguage.code)
        settingsManager.saveAppLocale(appLocale.code)
        settingsManager.saveSelectedCountry(country.code)
        settingsManager.saveThemeMode(themeMode.name.lowercase())
        settingsManager.setNotificationsEnabled(settings.notificationsEnabled)
        settingsManager.setNotifyBreaking(settings.notifyBreaking)
        settingsManager.setNotifyTopStory(settings.notifyTopStory)
        settingsManager.setNotifyReminder(settings.notifyReminder)

        val languageChanged = newsLanguage != mutableState.value.selectedLanguage
        mutableState.update { state ->
            state.copy(
                selectedLanguage = newsLanguage,
                appLocale = appLocale,
                selectedCountry = country,
                themeMode = themeMode,
                notificationsEnabled = settings.notificationsEnabled,
                notifyBreaking = settings.notifyBreaking,
                notifyTopStory = settings.notifyTopStory,
                notifyReminder = settings.notifyReminder,
            )
        }
        if (settings.notificationsEnabled) {
            pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(newsLanguage.code))
        } else {
            pushSubscriber.unsubscribeAll()
        }
        if (languageChanged) loadNewsWithCache()
    }

    /** Fire-and-forget: a signed-out reader is a no-op, a signed-in one pushes in the background. */
    private fun pushSavedArticlesIfSignedIn(articles: List<NewsArticle>) {
        val uid = mutableState.value.authUser?.uid ?: return
        viewModelScope.launch { remoteSyncClient.pushSavedArticles(uid, articles) }
    }

    private fun pushSettingsIfSignedIn() {
        val uid = mutableState.value.authUser?.uid ?: return
        val settings = currentSyncedSettings()
        viewModelScope.launch { remoteSyncClient.pushSettings(uid, settings) }
    }

    private fun handleSignInWithGoogle() {
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            when (val result = authClient.signInWithGoogle()) {
                AuthResult.Success -> handleCloseOverlay()
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    private fun handleSignInWithEmail(email: String, password: String) {
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            when (val result = authClient.signInWithEmail(email, password)) {
                AuthResult.Success -> handleCloseOverlay()
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    private fun handleSignUpWithEmail(email: String, password: String) {
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            when (val result = authClient.signUpWithEmail(email, password)) {
                AuthResult.Success -> handleCloseOverlay()
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    /**
     * Local bookmarks and settings are left exactly as they are: a guest is
     * not a second-class reader here, and the data on this device belongs to
     * this device regardless of whose account was just attached to it.
     */
    private fun handleSignOut() {
        viewModelScope.launch { authClient.signOut() }
    }

    /**
     * Deletes the server side first, while the reader is still authenticated —
     * Firestore's security rules require `request.auth.uid == uid`, which is
     * no longer true the moment `deleteAccount()` succeeds. Reversing this
     * order would leave the synced copy behind with no way to reach it again.
     */
    private fun handleDeleteAccount() {
        val uid = mutableState.value.authUser?.uid ?: return
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            remoteSyncClient.deleteUserData(uid)
            when (val result = authClient.deleteAccount()) {
                AuthResult.Success -> handleCloseOverlay()
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    private fun handleDismissAuthError() {
        mutableState.update { it.copy(authError = null) }
    }

    /**
     * Runs alongside the feed load rather than before it: these checks are
     * safeguards for rare cases, and making every launch wait on a network call
     * to find out everything is fine would be a cost paid by everyone.
     *
     * The device is inspected regardless of whether the config arrives — a
     * blocked network is exactly the state an attacker would arrange if the
     * response decided whether the check ran. What the config decides is only
     * the response to it, and the default is the mildest one.
     */
    private fun checkForRequiredUpdate() {
        viewModelScope.launch {
            val config = remoteConfigClient.fetch()

            val update = config?.let { requiredUpdateFor(it, BuildConfig.VERSION_CODE) }
            if (update != null) {
                analytics.logEvent(AnalyticsEvent.UpdateRequired(BuildConfig.VERSION_CODE))
                mutableState.update { state -> state.copy(requiredUpdate = update) }
                // An unsupported build is the more urgent of the two screens,
                // and it is the one the reader can act on.
                return@launch
            }

            // A debug build never enforces any of this, so it does not run the
            // checks either — the whole feature is invisible while developing.
            if (isDebugBuild()) return@launch

            val integrity = deviceIntegrityInspector.inspect()
            if (!integrity.isCompromised && !integrity.isDeveloperEnvironment) return@launch

            analytics.logEvent(
                AnalyticsEvent.DeviceIntegrityFailed(
                    rooted = integrity.isRooted,
                    debugger = integrity.isDebuggerAttached,
                    tampered = integrity.isTampered,
                    emulator = integrity.isEmulator,
                    developerOptions = integrity.isDeveloperOptionsEnabled,
                )
            )
            val notice = securityNoticeFor(
                integrity = integrity,
                policy = IntegrityPolicy.fromWire(config?.rootPolicy),
                environmentPolicy = IntegrityPolicy.fromWire(
                    config?.emulatorPolicy,
                    default = IntegrityPolicy.BLOCK,
                ),
                warningAlreadySeen = settingsManager.securityWarningSeen(),
                enforce = true,
            )
            if (notice != SecurityNotice.NONE) {
                mutableState.update { state ->
                    state.copy(
                        securityNotice = notice,
                        securityReason = securityReasonFor(integrity),
                    )
                }
            }
        }
    }

    /** The warning is shown once; dismissing it records that it was seen. */
    private fun handleDismissSecurityWarning() {
        viewModelScope.launch {
            settingsManager.markSecurityWarningSeen()
            mutableState.update { state -> state.copy(securityNotice = SecurityNotice.NONE) }
        }
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
            val savedThemeMode: String = settingsManager.themeModeFlow.first()
            val notificationsEnabled: Boolean = settingsManager.notificationsEnabledFlow.first()
            val notifyBreaking: Boolean = settingsManager.notifyBreakingFlow.first()
            val notifyTopStory: Boolean = settingsManager.notifyTopStoryFlow.first()
            val notifyReminder: Boolean = settingsManager.notifyReminderFlow.first()
            val newsLanguage: LanguageOption = LanguageOption.entries.find { it.code == savedNewsLanguage }
                ?: LanguageOption.ENGLISH
            val appLocale: AppLocale = AppLocale.fromCode(savedAppLocale)
            val country: CountryOption = CountryOption.entries.find { it.code == savedCountry }
                ?: CountryOption.UNITED_STATES
            val themeMode: ThemeMode = ThemeMode.entries.find { it.name.equals(savedThemeMode, ignoreCase = true) }
                ?: ThemeMode.SYSTEM
            mutableState.update { state ->
                state.copy(
                    selectedLanguage = newsLanguage,
                    appLocale = appLocale,
                    selectedCountry = country,
                    themeMode = themeMode,
                    notificationsEnabled = notificationsEnabled,
                    notifyBreaking = notifyBreaking,
                    notifyTopStory = notifyTopStory,
                    notifyReminder = notifyReminder,
                    isFirstLaunch = false
                )
            }
            mutableState.update { state -> state.copy(savedArticles = savedArticlesStore.load()) }
            if (notificationsEnabled) {
                pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(newsLanguage.code))
            }
            loadNewsWithCache()
        }
    }

    /**
     * Read-then-newest-first is not enough on its own — a returning reader
     * would just see yesterday's top story again. Applied at every site that
     * assigns [NewsUiState.articles], never to the list already on screen: a
     * reorder under a reader's thumb would move the card they are mid-swipe on.
     */
    private fun applyRanking(articles: List<NewsArticle>): List<NewsArticle> =
        articles.deprioritiseSeen(seenArticlesStore.load())

    fun processEvent(event: NewsUiEvent) {
        when (event) {
            is NewsUiEvent.SelectCategory -> handleSelectCategory(event.category)
            is NewsUiEvent.SelectCountry -> handleSelectCountry(event.country)
            is NewsUiEvent.SelectLanguage -> handleSelectLanguage(event.language)
            is NewsUiEvent.SelectAppLocale -> handleSelectAppLocale(event.locale)
            is NewsUiEvent.SelectTab -> handleSelectTab(event.tab)
            is NewsUiEvent.ScrollToArticle -> handleScrollToArticle(event.index)
            is NewsUiEvent.OpenArticleDetails -> handleOpenArticleDetails(event.article, event.origin)
            NewsUiEvent.CloseArticleDetails -> handleCloseOverlay()
            is NewsUiEvent.OpenOverlay -> handleOpenOverlay(event.overlay)
            NewsUiEvent.CloseOverlay -> handleCloseOverlay()
            NewsUiEvent.DismissSecurityWarning -> handleDismissSecurityWarning()
            NewsUiEvent.OpenArticleSource -> handleOpenArticleSource()
            is NewsUiEvent.OpenDeepLink -> handleOpenDeepLink(event.link)
            is NewsUiEvent.ShareArticle -> handleShareArticle(event.article)
            is NewsUiEvent.SaveArticle -> handleSaveArticle(event.article)
            is NewsUiEvent.RemoveSavedArticle -> handleRemoveSavedArticle(event.article)
            NewsUiEvent.RefreshNews -> handleRefreshNews()
            NewsUiEvent.RetryLoading -> handleRetryLoading()
            NewsUiEvent.DismissError -> handleDismissError()
            is NewsUiEvent.SelectThemeMode -> handleSelectThemeMode(event.mode)
            NewsUiEvent.ToggleNotificationsEnabled -> handleToggleNotificationsEnabled()
            is NewsUiEvent.ToggleNotificationTier -> handleToggleNotificationTier(event.tier)
            NewsUiEvent.RequestNotificationPermissionIfDue -> handleRequestNotificationPermissionIfDue()
            NewsUiEvent.SignInWithGoogle -> handleSignInWithGoogle()
            is NewsUiEvent.SignInWithEmail -> handleSignInWithEmail(event.email, event.password)
            is NewsUiEvent.SignUpWithEmail -> handleSignUpWithEmail(event.email, event.password)
            NewsUiEvent.SignOut -> handleSignOut()
            NewsUiEvent.DeleteAccount -> handleDeleteAccount()
            NewsUiEvent.DismissAuthError -> handleDismissAuthError()
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
        pushSettingsIfSignedIn()
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
                    articles = applyRanking(cachedResult.data),
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
        pushSettingsIfSignedIn()
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
        pushSettingsIfSignedIn()
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
        // A skim doesn't count — only a card the reader actually spent time on
        // is deprioritised on the next load.
        if (wasRead) seenArticlesStore.markSeen(article.articleUrl.value)
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
        mutableState.update { state -> state.copy(overlays = state.overlays + overlay) }
    }

    /** Pops whatever is on top — the details screen, Settings, or Saved. */
    private fun handleCloseOverlay() {
        mutableState.update { state -> state.copy(overlays = state.overlays.dropLast(1)) }
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

    /** Bookmarks are only useful if they outlive the session. */
    private fun persistSavedArticles(articles: List<NewsArticle>) {
        savedArticlesStore.save(articles)
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
            persistSavedArticles(currentSaved)
            pushSavedArticlesIfSignedIn(currentSaved)
            viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.ShowToast(strings().articleRemoved))
            }
        } else {
            currentSaved.add(0, article)
            mutableState.update { state ->
                state.copy(savedArticles = currentSaved)
            }
            persistSavedArticles(currentSaved)
            pushSavedArticlesIfSignedIn(currentSaved)
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
        persistSavedArticles(currentSaved)
        pushSavedArticlesIfSignedIn(currentSaved)
        viewModelScope.launch {
            mutableEffect.emit(NewsUiEffect.ShowToast(strings().articleRemoved))
        }
    }

    private fun handleSelectThemeMode(mode: ThemeMode) {
        if (mode == mutableState.value.themeMode) return
        mutableState.update { state -> state.copy(themeMode = mode) }
        viewModelScope.launch { settingsManager.saveThemeMode(mode.name.lowercase()) }
        pushSettingsIfSignedIn()
    }

    private fun handleToggleNotificationsEnabled() {
        val enabling = !mutableState.value.notificationsEnabled
        mutableState.update { state -> state.copy(notificationsEnabled = enabling) }
        viewModelScope.launch {
            settingsManager.setNotificationsEnabled(enabling)
            if (enabling) {
                pushSubscriber.subscribeToLanguage(
                    FeedLanguage.resolve(mutableState.value.selectedLanguage.code)
                )
                // Turning the switch on is the moment consent is meaningful —
                // the OS dialog belongs right here, not at cold start.
                mutableEffect.emit(NewsUiEffect.RequestNotificationPermission)
            } else {
                pushSubscriber.unsubscribeAll()
            }
        }
        pushSettingsIfSignedIn()
    }

    private fun handleToggleNotificationTier(tier: NotificationTier) {
        val state = mutableState.value
        when (tier) {
            NotificationTier.BREAKING -> {
                val enabling = !state.notifyBreaking
                mutableState.update { it.copy(notifyBreaking = enabling) }
                viewModelScope.launch { settingsManager.setNotifyBreaking(enabling) }
            }
            NotificationTier.TOP_STORY -> {
                val enabling = !state.notifyTopStory
                mutableState.update { it.copy(notifyTopStory = enabling) }
                viewModelScope.launch { settingsManager.setNotifyTopStory(enabling) }
            }
            NotificationTier.REMINDER -> {
                val enabling = !state.notifyReminder
                mutableState.update { it.copy(notifyReminder = enabling) }
                viewModelScope.launch { settingsManager.setNotifyReminder(enabling) }
            }
        }
        pushSettingsIfSignedIn()
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
                    articles = applyRanking(cachedResult.data),
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
                        articles = applyRanking(result.data),
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
                            articles = applyRanking(result.data),
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

        /** Cards deep before the notification permission is worth asking for. */
        const val PERMISSION_PROMPT_DEPTH: Int = 5
    }
}
