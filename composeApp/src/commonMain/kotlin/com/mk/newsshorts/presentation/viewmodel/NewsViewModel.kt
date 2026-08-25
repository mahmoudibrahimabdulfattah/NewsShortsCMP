package com.mk.newsshorts.presentation.viewmodel

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import com.mk.newsshorts.auth.AuthFailure
import com.mk.newsshorts.auth.AuthResult
import com.mk.newsshorts.data.local.NotificationInboxStore
import com.mk.newsshorts.data.local.PendingSignInEmailStore
import com.mk.newsshorts.data.local.RecentSearchesStore
import com.mk.newsshorts.data.repository.SavedArticlesRepository
import com.mk.newsshorts.data.repository.ToggleResult
import com.mk.newsshorts.data.local.SeenArticlesStore
import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.local.currentTimeMillis
import com.mk.newsshorts.data.local.isPlausibleEmail
import com.mk.newsshorts.domain.feed.appendPage
import com.mk.newsshorts.domain.feed.shouldLoadNextPage
import com.mk.newsshorts.domain.ranking.deprioritiseSeen
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
import com.mk.newsshorts.domain.model.FeedPage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.domain.preferences.openingCategory
import com.mk.newsshorts.domain.preferences.orderedCategories
import com.mk.newsshorts.domain.model.NewsResult
import com.mk.newsshorts.domain.search.isSearchable
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesRequest
import com.mk.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.domain.use_case.DeleteAccountUseCase
import com.mk.newsshorts.domain.use_case.SearchNewsRequest
import com.mk.newsshorts.domain.use_case.SearchNewsUseCase
import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.navigation.ArticleDeepLink
import com.mk.newsshorts.navigation.ArticleDeepLinks
import com.mk.newsshorts.data.remote.NotificationInboxClient
import com.mk.newsshorts.data.remote.SharePageResolver
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.navigation.NotificationBus
import com.mk.newsshorts.navigation.PendingLink
import com.mk.newsshorts.navigation.SignInLinkBus
import com.mk.newsshorts.navigation.toNewsArticle
import com.mk.newsshorts.notifications.PushSubscriber
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
import com.mk.newsshorts.presentation.mvi.NotificationTier
import com.mk.newsshorts.presentation.mvi.OnboardingStep
import com.mk.newsshorts.presentation.mvi.TextScale
import com.mk.newsshorts.presentation.mvi.InboxNotification
import com.mk.newsshorts.presentation.mvi.Overlay
import com.mk.newsshorts.presentation.mvi.ThemeMode
import com.mk.newsshorts.presentation.mvi.afterUnsuccessfulAuth

class NewsViewModel(
    private val getTopHeadlinesUseCase: GetTopHeadlinesUseCase,
    private val searchNewsUseCase: SearchNewsUseCase,
    private val recentSearchesStore: RecentSearchesStore,
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val pushSubscriber: PushSubscriber,
    private val deepLinkBus: DeepLinkBus,
    private val signInLinkBus: SignInLinkBus,
    private val savedArticlesRepository: SavedArticlesRepository,
    private val seenArticlesStore: SeenArticlesStore,
    private val pendingSignInEmailStore: PendingSignInEmailStore,
    private val remoteConfigClient: RemoteConfigClient,
    private val deviceIntegrityInspector: DeviceIntegrityInspector,
    private val authClient: AuthClient,
    private val remoteSyncClient: RemoteSyncClient,
    private val sharePageResolver: SharePageResolver,
    private val notificationInboxClient: NotificationInboxClient,
    private val notificationInboxStore: NotificationInboxStore,
    private val notificationBus: NotificationBus,
) : BaseViewModel() {

    private val mutableState: MutableStateFlow<NewsUiState> = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = mutableState.asStateFlow()

    private val mutableEffect: MutableSharedFlow<NewsUiEffect> = MutableSharedFlow()
    val uiEffect: SharedFlow<NewsUiEffect> = mutableEffect.asSharedFlow()

    private val deleteAccountUseCase = DeleteAccountUseCase(authClient, remoteSyncClient)

    /** Toast text is built here rather than in the UI, so it needs the locale too. */
    private fun strings(): AppStrings = getStrings(mutableState.value.appLocale)

    init {
        observeSavedArticles()
        loadSavedSettings()
        observeDeepLinks()
        observeArrivingNotifications()
        observeSignInLinks()
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
    /**
     * The repository owns the list; the state object mirrors it so the UI keeps
     * reading one place. Anything inside the ViewModel that needs the current
     * bookmarks reads the repository directly instead of this mirror, which
     * lags it by a dispatch.
     */
    private fun observeSavedArticles() {
        viewModelScope.launch {
            savedArticlesRepository.saved.collect { articles ->
                mutableState.update { it.copy(savedArticles = articles) }
            }
        }
    }

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
                val merged = savedArticlesRepository.mergeWithRemote(remoteSaved.value)
                remoteSyncClient.pushSavedArticles(uid, merged)
            }
            SyncFetch.NotFound -> remoteSyncClient.pushSavedArticles(uid, savedArticlesRepository.saved.value)
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

    /**
     * Success here means the mail is on its way, not that anyone is signed in —
     * the reader now has to leave the app entirely, so the address is written
     * to disk before the state changes. If they never come back, nothing was
     * created; there is no half-made account waiting for a verification that
     * may never happen, which is the whole reason this replaced passwords.
     */
    private fun handleSendSignInLink(email: String) {
        val address = email.trim()
        if (!isPlausibleEmail(address)) {
            mutableState.update { it.copy(authError = AuthFailure.INVALID_EMAIL) }
            return
        }
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            val language = mutableState.value.appLocale.code
            when (val result = authClient.sendSignInLink(address, language)) {
                AuthResult.Success -> {
                    pendingSignInEmailStore.save(address)
                    mutableState.update {
                        it.copy(authInProgress = false, pendingSignInEmail = address)
                    }
                }
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    /**
     * A followed link, handed over by the OS. The address it belongs to is not
     * in the link — see [AuthClient.completeSignInWithLink] — so a link opened
     * on a device that never asked for one is held for the screen to ask about
     * rather than dropped.
     */
    private fun handleSignInLinkOpened(link: String) {
        if (!authClient.isSignInLink(link)) return
        val storedEmail = pendingSignInEmailStore.load()
        if (storedEmail == null) {
            mutableState.update { state ->
                // The link may arrive with the app cold, so the sign-in screen
                // is opened rather than assumed — but never stacked twice, or
                // one back press would leave a duplicate behind.
                val overlays = state.overlays.takeIf { Overlay.SignIn in it }
                    ?: (state.overlays + Overlay.SignIn)
                state.copy(unclaimedSignInLink = link, overlays = overlays)
            }
            return
        }
        completeLinkSignIn(email = storedEmail, link = link)
    }

    private fun handleSupplyLinkEmail(email: String) {
        val link = mutableState.value.unclaimedSignInLink ?: return
        val address = email.trim()
        if (!isPlausibleEmail(address)) {
            mutableState.update { it.copy(authError = AuthFailure.INVALID_EMAIL) }
            return
        }
        completeLinkSignIn(email = address, link = link)
    }

    private fun completeLinkSignIn(email: String, link: String) {
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            when (val result = authClient.completeSignInWithLink(email, link)) {
                AuthResult.Success -> {
                    pendingSignInEmailStore.clear()
                    mutableState.update {
                        it.copy(pendingSignInEmail = null, unclaimedSignInLink = null)
                    }
                    handleCloseOverlay()
                }
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                // The link stays held on failure: an expired one is worth
                // saying so about, and the reader may simply have mistyped the
                // address on a second device.
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    private fun handleCancelPendingSignInLink() {
        pendingSignInEmailStore.clear()
        mutableState.update {
            it.copy(pendingSignInEmail = null, unclaimedSignInLink = null, authError = null)
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
     * see [DeleteAccountUseCase], which enforces that order and refuses to
     * delete the account when the synced copy may still exist.
     */
    private fun handleDeleteAccount() {
        val uid = mutableState.value.authUser?.uid ?: return
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        viewModelScope.launch {
            when (val result = deleteAccountUseCase(uid)) {
                AuthResult.Success -> handleCloseOverlay()
                else -> mutableState.update { it.afterUnsuccessfulAuth(result) }
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

    /**
     * Merges a notification into the inbox the moment it arrives, without
     * waiting for the backend to republish.
     *
     * The published file is written in the same run that sends the push, but it
     * reaches a reader through a static deploy that takes minutes — long enough
     * for someone who taps straight into the app to look for the notification
     * they were just shown and not find it.
     *
     * Merged rather than prepended blindly: the published list arrives too, and
     * both describe the same send.
     */
    private fun observeArrivingNotifications() {
        viewModelScope.launch {
            notificationBus.latest.collect { arrived ->
                if (arrived == null) return@collect
                mutableState.update { state ->
                    if (state.inboxNotifications.any { it.sentAt == arrived.sentAt }) state
                    else state.copy(
                        inboxNotifications = (listOf(arrived) + state.inboxNotifications)
                            .sortedByDescending { it.sentAt },
                    )
                }
            }
        }
    }

    private fun observeSignInLinks() {
        viewModelScope.launch {
            signInLinkBus.pending.collect { link ->
                if (link == null) return@collect
                processEvent(NewsUiEvent.SignInLinkOpened(link))
                // Consumed whether or not it worked: these are single-use, so
                // retrying the same one only ever produces a worse error.
                signInLinkBus.consume()
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
            val textScale: TextScale = TextScale.fromStored(settingsManager.textScaleFlow.first())
            val preferred: List<String> = settingsManager.preferredCategories()
            // Read once, at the only moment it can be true. Asked again later
            // it would re-open the flow the reader has just finished.
            val needsOnboarding: Boolean = !settingsManager.onboardingComplete()
            mutableState.update { state ->
                state.copy(
                    onboarding = if (needsOnboarding) OnboardingStep.LANGUAGE else null,
                    textScale = textScale,
                    selectedCategory = openingCategory(preferred),
                    categoryOrder = orderedCategories(preferred),
                    selectedLanguage = newsLanguage,
                    appLocale = appLocale,
                    selectedCountry = country,
                    themeMode = themeMode,
                    notificationsEnabled = notificationsEnabled,
                    notifyBreaking = notifyBreaking,
                    notifyTopStory = notifyTopStory,
                    notifyReminder = notifyReminder,
                )
            }
            savedArticlesRepository.load()
            if (notificationsEnabled) {
                pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(newsLanguage.code))
            }
            // After the language is in state and not from init: the inbox is
            // published per language, and asking before settings load would
            // fetch the default one and mark its notifications unread.
            refreshNotificationInbox()
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
            is NewsUiEvent.SelectAppLocale -> handleSelectAppLocale(event.locale)
            is NewsUiEvent.SelectTab -> handleSelectTab(event.tab)
            is NewsUiEvent.ScrollToArticle -> handleScrollToArticle(event.index)
            is NewsUiEvent.OpenArticleDetails -> handleOpenArticleDetails(event.article, event.origin)
            NewsUiEvent.CloseArticleDetails -> handleCloseOverlay()
            is NewsUiEvent.OpenOverlay -> handleOpenOverlay(event.overlay)
            NewsUiEvent.CloseOverlay -> handleCloseOverlay()
            NewsUiEvent.DismissSecurityWarning -> handleDismissSecurityWarning()
            NewsUiEvent.OpenArticleSource -> handleOpenArticleSource()
            NewsUiEvent.OpenPrivacyPolicy -> viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.OpenUrl(privacyPolicyUrl()))
            }
            is NewsUiEvent.OpenDeepLink -> handleOpenDeepLink(event.link)
            is NewsUiEvent.OpenSharePage -> handleOpenSharePage(event.url)
            is NewsUiEvent.ShareArticle -> handleShareArticle(event.article)
            is NewsUiEvent.SaveArticle -> handleSaveArticle(event.article)
            is NewsUiEvent.RemoveSavedArticle -> handleRemoveSavedArticle(event.article)
            NewsUiEvent.OpenSearch -> handleOpenSearch()
            NewsUiEvent.OpenNotificationInbox -> handleOpenNotificationInbox()
            is NewsUiEvent.OpenInboxNotification -> handleOpenInboxNotification(event.deepLink)
            NewsUiEvent.MarkAllNotificationsRead -> handleMarkAllNotificationsRead()
            NewsUiEvent.RefreshNotificationInbox -> refreshNotificationInbox(pulled = true)
            is NewsUiEvent.DismissInboxNotification -> handleDismissInboxNotification(event.articleUrl)
            is NewsUiEvent.RestoreInboxNotification -> handleRestoreInboxNotification(event.articleUrl)
            is NewsUiEvent.SearchQueryChanged -> handleSearchQueryChanged(event.query)
            is NewsUiEvent.RunSearch -> handleRunSearch(event.query)
            NewsUiEvent.ClearSearchQuery -> handleSearchQueryChanged("")
            is NewsUiEvent.RemoveRecentSearch -> handleRemoveRecentSearch(event.query)
            NewsUiEvent.ClearRecentSearches -> handleClearRecentSearches()
            NewsUiEvent.RefreshNews -> handleRefreshNews()
            NewsUiEvent.RetryLoading -> handleRetryLoading()
            NewsUiEvent.RetryNextPage -> handleRetryNextPage()
            NewsUiEvent.DismissError -> handleDismissError()
            is NewsUiEvent.SelectThemeMode -> handleSelectThemeMode(event.mode)
            NewsUiEvent.ToggleNotificationsEnabled -> handleToggleNotificationsEnabled()
            is NewsUiEvent.ToggleNotificationTier -> handleToggleNotificationTier(event.tier)
            NewsUiEvent.RequestNotificationPermissionIfDue -> handleRequestNotificationPermissionIfDue()
            NewsUiEvent.SignInWithGoogle -> handleSignInWithGoogle()
            is NewsUiEvent.SendSignInLink -> handleSendSignInLink(event.email)
            is NewsUiEvent.SignInLinkOpened -> handleSignInLinkOpened(event.link)
            is NewsUiEvent.SupplyLinkEmail -> handleSupplyLinkEmail(event.email)
            NewsUiEvent.CancelPendingSignInLink -> handleCancelPendingSignInLink()
            NewsUiEvent.SignOut -> handleSignOut()
            NewsUiEvent.DeleteAccount -> handleDeleteAccount()
            NewsUiEvent.DismissAuthError -> handleDismissAuthError()
            is NewsUiEvent.OnboardingToggleCategory -> handleOnboardingToggleCategory(event.category)
            NewsUiEvent.OnboardingNext -> handleOnboardingNext()
            NewsUiEvent.OnboardingSkip -> handleOnboardingSkip()
            is NewsUiEvent.SelectTextScale -> handleSelectTextScale(event.scale)
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
        showCachedFeed(request)
        val generation = startNewFeed()
        viewModelScope.launch {
            fetchNewsInBackground(request, generation)
        }
    }

    private fun handleSelectLanguage(language: LanguageOption) {
        if (language == mutableState.value.selectedLanguage) return
        mutableState.update { state ->
            state.copy(
                selectedLanguage = language,
                currentArticleIndex = 0,
                errorMessage = null,
                // There is a corpus per language, so results found in the old
                // one describe a search that can no longer be repeated.
                searchResults = emptyList(),
                searchSettled = false,
                searchFailed = false
            )
        }
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.NewsLanguageChanged(language.code))
            analytics.setProperty("news_language", language.code)
            pushSubscriber.subscribeToLanguage(FeedLanguage.resolve(language.code))
            settingsManager.saveNewsLanguage(language.code)
            // The inbox is per language too, and the old list describes
            // notifications this reader will no longer be sent.
            refreshNotificationInbox()
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

    /**
     * Advances onboarding, or finishes it on the last step.
     *
     * Finishing writes what was chosen; there is no separate confirm, because
     * every step already applied its own answer the moment it was tapped —
     * language rewrites the screen under the reader's hand, and a category is
     * ticked, not submitted.
     */
    private fun handleOnboardingNext() {
        val current: OnboardingStep = mutableState.value.onboarding ?: return
        val next: OnboardingStep? = current.next
        if (next != null) {
            mutableState.update { it.copy(onboarding = next) }
            return
        }
        finishOnboarding(answeredNotifications = true)
    }

    /**
     * Leaves early, keeping anything already ticked. The notification
     * permission is deliberately *not* requested here: a reader who skipped
     * past the question has not answered it, so the contextual prompt after a
     * few articles stays armed, which is the whole point of having it.
     */
    private fun handleOnboardingSkip() {
        finishOnboarding(answeredNotifications = false)
    }

    /**
     * [answeredNotifications] is whether the reader reached the last step and
     * pressed through it — which is an answer either way, including "no".
     *
     * Only that closes the question. A "no" here used to leave the contextual
     * prompt armed, so the reader who had just declined got the system dialog
     * anyway a few articles later; declining that is what permanently blocks
     * the app from ever asking again, so the reask was not merely rude, it
     * spent the one thing it was trying to win. Skipping leaves it armed on
     * purpose: a reader who skipped past the question has not answered it.
     */
    private fun finishOnboarding(answeredNotifications: Boolean) {
        val chosen: List<NewsCategory> = mutableState.value.onboardingCategories
        val preferred: List<String> = chosen.map { it.apiValue }
        mutableState.update { state ->
            state.copy(
                onboarding = null,
                categoryOrder = orderedCategories(preferred),
                selectedCategory = openingCategory(preferred),
                currentArticleIndex = 0,
            )
        }
        viewModelScope.launch {
            settingsManager.savePreferredCategories(preferred)
            settingsManager.markOnboardingComplete()
            if (answeredNotifications) {
                // Marked seen on either answer, so the after-a-few-articles
                // prompt never asks a question the reader has already answered.
                settingsManager.markNotificationPromptSeen()
                // The system dialog only for a yes. Showing it to someone who
                // just said no would collect the denial that locks the
                // permission for good.
                if (mutableState.value.notificationsEnabled) {
                    mutableEffect.emit(NewsUiEffect.RequestNotificationPermission)
                }
            }
        }
        // The category may have changed, so the feed is reloaded rather than
        // left showing whatever the default had already fetched behind us.
        loadNewsWithCache()
    }

    private fun handleOnboardingToggleCategory(category: NewsCategory) {
        mutableState.update { state ->
            val chosen = state.onboardingCategories
            state.copy(
                onboardingCategories = if (category in chosen) {
                    chosen - category
                } else {
                    chosen + category
                }
            )
        }
    }

    private fun handleSelectTextScale(scale: TextScale) {
        if (scale == mutableState.value.textScale) return
        mutableState.update { it.copy(textScale = scale) }
        viewModelScope.launch { settingsManager.saveTextScale(scale.stored) }
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
        // Opening a result is the strongest signal that this query was the one
        // the reader meant, so it is what puts it in their recent searches.
        if (origin == ArticleOpenOrigin.SEARCH) rememberSearch(mutableState.value.searchQuery)
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

    /** Pops whatever is on top — the details screen, Settings, Saved, or Search. */
    private fun handleCloseOverlay() {
        val closing = mutableState.value.overlays.lastOrNull()
        // Leaving search ends it: a result list from a query the reader has
        // moved on from is not what they want to find waiting the next time
        // they open the field. Opening a result does not go through here — it
        // pushes the details screen *above* search, so the list survives a
        // read-and-come-back.
        if (closing == Overlay.Search) {
            searchJob?.cancel()
            searchJob = null
        }
        mutableState.update { state ->
            val remaining = state.overlays.dropLast(1)
            if (closing == Overlay.Search) {
                state.copy(
                    overlays = remaining,
                    searchQuery = "",
                    searchResults = emptyList(),
                    isSearching = false,
                    searchSettled = false,
                    searchFailed = false,
                )
            } else {
                state.copy(overlays = remaining)
            }
        }
    }

    /** In flight or waiting out the debounce; cancelled by the next keystroke. */
    private var searchJob: Job? = null

    /**
     * Loads what has been pushed in the reader's language.
     *
     * Called on launch and not only when the inbox is opened, because the bell
     * carries the unread mark and a mark that only appears after you look is
     * not a mark. One small file, and a failure leaves the previous list in
     * place rather than emptying the screen.
     */
    private fun refreshNotificationInbox(pulled: Boolean = false) {
        if (pulled) mutableState.update { it.copy(isRefreshingInbox = true) }
        viewModelScope.launch {
            val language = FeedLanguage.resolve(mutableState.value.selectedLanguage.code)
            val sent = notificationInboxClient.fetch(language)
            // An empty answer is a failure as often as it is an empty inbox —
            // the client cannot tell them apart — so the list stands rather
            // than being wiped by a bad connection. The spinner still stops.
            if (sent.isEmpty()) {
                mutableState.update { it.copy(isRefreshingInbox = false) }
                return@launch
            }
            mutableState.update { state ->
                state.copy(
                    inboxNotifications = sent.map {
                        InboxNotification(
                            sentAt = it.sentAt,
                            title = it.title,
                            body = it.body,
                            deepLink = it.deepLink,
                            articleUrl = ArticleDeepLinks.parse(it.deepLink)?.url.orEmpty(),
                        )
                    },
                    inboxRead = notificationInboxStore.read(),
                    inboxDismissed = notificationInboxStore.dismissed(),
                    isRefreshingInbox = false,
                )
            }
        }
    }

    /**
     * Opens the inbox and marks nothing.
     *
     * Looking at a list is not the same as having read what is in it. A reader
     * opens this to find the story they were told about and have not been into
     * yet, so the marks have to survive the act of looking — they come off when
     * a notification is opened, or when the reader says so for all of them.
     */
    private fun handleOpenNotificationInbox() {
        handleOpenOverlay(Overlay.NotificationInbox)
        // The list on screen may be a session old. Refreshing behind the open
        // screen costs one small file and cannot reorder anything the reader is
        // looking at, because the sort is by time.
        refreshNotificationInbox()
    }

    /**
     * Hides one row on this device.
     *
     * There is nothing else it could do: the list is a single file published
     * for every reader, so a dismissal is local by construction. Written
     * through the store rather than held in state so it survives the next
     * refresh, which replaces the published list wholesale.
     */
    private fun handleDismissInboxNotification(articleUrl: String) {
        notificationInboxStore.dismiss(articleUrl)
        mutableState.update { it.copy(inboxDismissed = notificationInboxStore.dismissed()) }
    }

    /**
     * Undo. A swipe is one gesture away from a story the reader wanted, and
     * the row cannot be recovered from anywhere else once it is hidden.
     */
    private fun handleRestoreInboxNotification(articleUrl: String) {
        notificationInboxStore.restore(articleUrl)
        mutableState.update { it.copy(inboxDismissed = notificationInboxStore.dismissed()) }
    }

    /** One of the two things that clears a mark; see [handleOpenInboxNotification]. */
    private fun handleMarkAllNotificationsRead() {
        val newest = mutableState.value.visibleInboxNotifications.maxOfOrNull { it.sentAt } ?: return
        notificationInboxStore.markAllRead(newest)
        mutableState.update { it.copy(inboxRead = notificationInboxStore.read()) }
    }

    /**
     * A row carries the notification's own link, so this is the same path a
     * notification tap takes — including the details screen it lands on and the
     * origin it is reported under.
     */
    private fun handleOpenInboxNotification(deepLink: String) {
        val link = ArticleDeepLinks.parse(deepLink) ?: return
        handleOpenDeepLink(link)
    }

    private fun handleOpenSearch() {
        mutableState.update { it.copy(recentSearches = recentSearchesStore.load()) }
        handleOpenOverlay(Overlay.Search)
    }

    private fun handleSearchQueryChanged(query: String) {
        searchJob?.cancel()
        mutableState.update { it.copy(searchQuery = query) }
        if (!isSearchable(query)) {
            // Back to the empty state rather than to stale results for a
            // longer query the reader has just deleted half of.
            mutableState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchSettled = false,
                    searchFailed = false,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            runSearch(query)
        }
    }

    private fun handleRunSearch(query: String) {
        searchJob?.cancel()
        mutableState.update { it.copy(searchQuery = query) }
        rememberSearch(query)
        if (!isSearchable(query)) return
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        mutableState.update { it.copy(isSearching = true, searchFailed = false) }
        val language = FeedLanguage.resolve(mutableState.value.selectedLanguage.code)
        val result = searchNewsUseCase.execute(
            SearchNewsRequest(query = query, language = language)
        )
        // The first search of a session downloads the corpus, which is long
        // enough for the reader to have typed another word by the time it
        // lands. Those results belong to a query that is no longer in the field.
        if (mutableState.value.searchQuery != query) return
        when (result) {
            is NewsResult.Success -> {
                analytics.logEvent(
                    // Counts and a length, never the text — see SearchPerformed.
                    AnalyticsEvent.SearchPerformed(
                        resultCount = result.data.size,
                        queryLength = query.trim().length,
                        language = language,
                    )
                )
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = result.data,
                        searchSettled = true,
                        searchFailed = false,
                    )
                }
            }
            is NewsResult.Error -> {
                // The message is the network's, never the query's.
                analytics.recordError("Search failed: ${result.error.message}")
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = emptyList(),
                        searchSettled = true,
                        searchFailed = true,
                    )
                }
            }
        }
    }

    /**
     * Records a query the reader clearly meant: they pressed search, or they
     * opened something they found. A debounced keystroke is not that — half the
     * history would otherwise be prefixes of the word they were typing.
     */
    private fun rememberSearch(query: String) {
        val trimmed = query.trim()
        if (!isSearchable(trimmed)) return
        mutableState.update { it.copy(recentSearches = recentSearchesStore.add(trimmed)) }
    }

    private fun handleRemoveRecentSearch(query: String) {
        mutableState.update { it.copy(recentSearches = recentSearchesStore.remove(query)) }
    }

    private fun handleClearRecentSearches() {
        recentSearchesStore.clear()
        mutableState.update { it.copy(recentSearches = emptyList()) }
    }

    /** The policy page picks its language from this, not from the browser. */
    private fun privacyPolicyUrl(): String =
        urlInLanguage(BuildConfig.PRIVACY_POLICY_URL, mutableState.value.appLocale.code)

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

    private fun handleOpenDeepLink(link: ArticleDeepLink) {
        val state = mutableState.value
        val article = state.articles.firstOrNull { it.articleUrl.value == link.url }
            ?: savedArticlesRepository.saved.value.firstOrNull { it.articleUrl.value == link.url }
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
        // The reader has gone into the story, so the inbox row for it is read —
        // whether they came from a row, from the notification still sitting in
        // the tray, or from a shared link that happened to also be pushed.
        //
        // Marked before the screen opens rather than after it closes: a mark
        // that waited for them to come back would still be there if they left
        // from the details screen instead. And written to the store first, so a
        // cold start from a tray tap records it even though the published list
        // has not arrived yet — when it does, the row is already read.
        notificationInboxStore.markRead(article.articleUrl.value)
        mutableState.update { it.copy(inboxRead = notificationInboxStore.read()) }

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
        val result = savedArticlesRepository.toggle(article)
        val saved = savedArticlesRepository.saved.value
        pushSavedArticlesIfSignedIn(saved)
        if (result == ToggleResult.SAVED) {
            analytics.logEvent(AnalyticsEvent.ArticleSaved(article.category.apiValue))
        }
        val message = when (result) {
            ToggleResult.SAVED -> strings().articleSaved
            ToggleResult.REMOVED -> strings().articleRemoved
        }
        viewModelScope.launch {
            mutableEffect.emit(NewsUiEffect.ShowToast(message))
        }
    }

    private fun handleRemoveSavedArticle(article: NewsArticle) {
        if (!savedArticlesRepository.remove(article)) return
        pushSavedArticlesIfSignedIn(savedArticlesRepository.saved.value)
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
        val request = currentRequest()
        showCachedFeed(request)
        val generation = startNewFeed()
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

    private suspend fun fetchNewsInBackground(request: GetTopHeadlinesRequest, generation: Int) {
        when (val result = getTopHeadlinesUseCase.execute(request)) {
            is NewsResult.Success -> {
                if (generation != feedGeneration) return
                mutableState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isBackgroundRefreshing = false,
                        feedRevision = state.feedRevision + 1,
                        articles = applyRanking(result.data.articles),
                        nextPageFile = result.data.nextPage,
                        isLoadingNextPage = false,
                        nextPageFailed = false,
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
        val generation = startNewFeed()
        viewModelScope.launch {
            when (val result = getTopHeadlinesUseCase.execute(currentRequest())) {
                is NewsResult.Success -> {
                    // A refresh that landed after the reader had already moved
                    // on belongs to a feed that no longer exists.
                    if (generation != feedGeneration) return@launch
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            feedRevision = state.feedRevision + 1,
                        articles = applyRanking(result.data.articles),
                            nextPageFile = result.data.nextPage,
                            isLoadingNextPage = false,
                            nextPageFailed = false,
                            errorMessage = null,
                            currentArticleIndex = 0,
                            isOfflineMode = false
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

        /**
         * How long typing has to stop before a search runs. Long enough that a
         * word typed at speed is one search rather than six, short enough that
         * results feel like they are keeping up.
         */
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300
    }
}
