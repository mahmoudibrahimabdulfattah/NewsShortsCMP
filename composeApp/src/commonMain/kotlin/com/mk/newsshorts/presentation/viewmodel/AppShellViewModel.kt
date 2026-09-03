package com.mk.newsshorts.presentation.viewmodel

import com.mk.newsshorts.core.model.analytics.AnalyticsEvent
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.domain.auth.AuthSession
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.core.data.remote.SharePageResolver
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.core.domain.feed.FeedInvalidator
import com.mk.newsshorts.core.domain.feed.InvalidationReason
import com.mk.newsshorts.core.model.FeedLanguage
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.domain.repository.ArticleLookup
import com.mk.newsshorts.core.domain.repository.InboxReadMarker
import com.mk.newsshorts.core.model.deeplink.ArticleDeepLink
import com.mk.newsshorts.core.model.deeplink.ArticleDeepLinks
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.navigation.PendingLink
import com.mk.newsshorts.core.model.deeplink.toNewsArticle
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.presentation.localization.urlInLanguage
import com.mk.newsshorts.core.model.article.ArticleOpenOrigin
import com.mk.newsshorts.navigation.Overlay
import com.mk.newsshorts.navigation.Navigator
import com.mk.newsshorts.core.domain.sync.AccountSyncUseCase
import com.mk.newsshorts.core.domain.sync.SyncOutcome
import com.mk.newsshorts.core.domain.sync.SyncPublisher
import com.mk.newsshorts.core.model.sync.SyncedSettings
import com.mk.newsshorts.core.data.sync.apply
import com.mk.newsshorts.core.model.sync.toSyncedSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface AppShellUiEvent {
    data class OpenOverlay(val overlay: Overlay) : AppShellUiEvent
    /** Pops the top of the stack — one back-press rule for every overlay. */
    data object CloseOverlay : AppShellUiEvent
    data object OpenSearch : AppShellUiEvent

    data class OpenArticleDetails(
        val article: NewsArticle,
        val origin: ArticleOpenOrigin,
    ) : AppShellUiEvent

    /** Takes no argument so there is one source of truth for which URL opens. */
    data object OpenArticleSource : AppShellUiEvent
    data object OpenPrivacyPolicy : AppShellUiEvent
    data class ShareArticle(val article: NewsArticle) : AppShellUiEvent

    data class OpenDeepLink(val link: ArticleDeepLink) : AppShellUiEvent

    /**
     * A shared link that names a landing page rather than carrying the article.
     * Resolving it is a network round trip, so it arrives as its own event and
     * becomes an [OpenDeepLink] once the article is in hand.
     */
    data class OpenSharePage(val url: String) : AppShellUiEvent
}

sealed interface AppShellUiEffect {
    data class OpenUrl(val url: String) : AppShellUiEffect
    data class ShareContent(
        val title: String,
        val url: String,
        val chooserTitle: String,
    ) : AppShellUiEffect
}

class AppShellViewModel(
    private val settingsManager: SettingsManager,
    private val analytics: AnalyticsReporter,
    private val deepLinkBus: DeepLinkBus,
    private val savedArticles: SavedArticles,
    private val accountSync: AccountSyncUseCase,
    private val authSession: AuthSession,
    private val syncPublisher: SyncPublisher,
    private val feedInvalidator: FeedInvalidator,
    private val articleLookup: ArticleLookup,
    private val sharePageResolver: SharePageResolver,
    private val inboxReadMarker: InboxReadMarker,
    private val navigator: Navigator,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {

    private val shellScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private val effectChannel = Channel<AppShellUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<AppShellUiEffect> = effectChannel.receiveAsFlow()

    private var accountSyncJob: Job? = null
    private var activeAccountSyncUid: String? = null

    init {
        observeDeepLinks()
        observeAuthState()
    }

    /** Toast text is built here rather than in the UI, so it needs the locale too. */
    private fun strings(): AppStrings =
        getStrings(AppLocale.fromCode(settingsManager.preferences.value.appLocale))

    fun processEvent(event: AppShellUiEvent) {
        when (event) {
            is AppShellUiEvent.OpenOverlay -> openOverlay(event.overlay)
            AppShellUiEvent.CloseOverlay -> closeOverlay()
            AppShellUiEvent.OpenSearch -> openOverlay(Overlay.Search)
            is AppShellUiEvent.OpenArticleDetails ->
                openArticleDetails(event.article, event.origin)
            AppShellUiEvent.OpenArticleSource -> openArticleSource()
            AppShellUiEvent.OpenPrivacyPolicy -> openUrl(privacyPolicyUrl())
            is AppShellUiEvent.ShareArticle -> shareArticle(event.article)
            is AppShellUiEvent.OpenDeepLink -> openDeepLink(event.link)
            is AppShellUiEvent.OpenSharePage -> openSharePage(event.url)
        }
    }

    private fun openOverlay(overlay: Overlay) {
        navigator.open(overlay)
    }

    /** Pops whatever is on top — the details screen, Settings, Saved, or Search. */
    private fun closeOverlay() {
        navigator.close()
    }

    private fun openArticleDetails(article: NewsArticle, origin: ArticleOpenOrigin) {
        openOverlay(Overlay.Details(article, origin))
        analytics.logEvent(
            AnalyticsEvent.ArticleDetailsOpened(
                category = article.category.apiValue,
                source = article.source.name.value,
                origin = origin.analyticsValue,
            )
        )
    }

    private fun openArticleSource() {
        val article = (navigator.overlays.value.lastOrNull() as? Overlay.Details)?.article ?: return
        analytics.logEvent(
            AnalyticsEvent.ArticleSourceOpened(article.category.apiValue, article.source.name.value)
        )
        openUrl(article.articleUrl.value)
    }

    /** The policy page picks its language from this, not from the browser. */
    private fun privacyPolicyUrl(): String =
        urlInLanguage(BuildConfig.PRIVACY_POLICY_URL, settingsManager.preferences.value.appLocale)

    private fun openUrl(url: String) {
        shellScope.launch { effectChannel.send(AppShellUiEffect.OpenUrl(url)) }
    }

    private fun shareArticle(article: NewsArticle) {
        analytics.logEvent(AnalyticsEvent.ArticleShared(article.category.apiValue))
        shellScope.launch {
            effectChannel.send(
                AppShellUiEffect.ShareContent(
                    title = article.title.value,
                    // The share link opens the app rather than the publisher,
                    // so a shared story brings the reader back here.
                    url = ArticleDeepLinks.shareUrl(
                        article = article,
                        baseUrl = BuildConfig.SHARE_BASE_URL,
                        // The article's language, so the landing page matches it
                        // rather than defaulting to Arabic.
                        language = FeedLanguage.resolve(
                            settingsManager.preferences.value.newsLanguage
                        ),
                    ),
                    chooserTitle = strings().shareArticle,
                )
            )
        }
    }

    private fun observeDeepLinks() {
        shellScope.launch {
            deepLinkBus.pending.collect { pending ->
                when (pending) {
                    null -> return@collect
                    is PendingLink.Article -> openDeepLink(pending.link)
                    is PendingLink.SharePage -> openSharePage(pending.url)
                }
                // Both this and the ViewModel outlive the Activity, so an
                // unconsumed link would reopen the screen on every resume.
                deepLinkBus.consume()
            }
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
    private fun openSharePage(pageUrl: String) {
        shellScope.launch {
            val link = sharePageResolver.resolve(pageUrl)
            if (link != null) openDeepLink(link)
            else effectChannel.send(AppShellUiEffect.OpenUrl(pageUrl))
        }
    }

    /**
     * Prefers a copy already saved or in the cached feed — those carry the real
     * image and timestamp — and falls back to rebuilding the article from the
     * link, which is all a cold start has.
     *
     * The feed's own in-memory list is deliberately not consulted: it would
     * only help when the article is already on screen, and reaching into it
     * would be the shell reading a feature's state.
     */
    private fun openDeepLink(link: ArticleDeepLink) {
        shellScope.launch {
            val article = savedArticles.saved.value
                .firstOrNull { it.articleUrl.value == link.url }
                ?: articleLookup.find(link.url)
                ?: link.toNewsArticle()
                ?: return@launch
            // A shared link marks itself, so notification_opened stays a count of
            // notifications rather than of every way into the details screen.
            val fromShare = link.referrer == ArticleDeepLinks.SHARE_REFERRER
            if (!fromShare) {
                analytics.logEvent(
                    AnalyticsEvent.NotificationOpened(
                        article.category.apiValue,
                        article.source.name.value,
                    )
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

            openArticleDetails(
                article,
                if (fromShare) ArticleOpenOrigin.SHARE else ArticleOpenOrigin.PUSH,
            )
        }
    }

    private fun observeAuthState() {
        shellScope.launch {
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
     * Read from the store, never from a ViewModel's state. The UI state starts
     * on hardcoded defaults and is filled in asynchronously, so a sign-in that
     * lands first would have pushed English, US and "system" over whatever the
     * reader had actually chosen. The store has the real values from the moment
     * it is constructed.
     */
    private fun currentSyncedSettings(): SyncedSettings =
        settingsManager.preferences.value.toSyncedSettings()

    private suspend fun applySyncOutcome(outcome: SyncOutcome) {
        // Read once so the null guard and the apply use the same object.
        val settings = outcome.settings
        if (settings == null) {
            if (outcome.saved != savedArticles.saved.value) {
                savedArticles.replaceAll(outcome.saved)
            }
            return
        }
        // The remote copy becomes the local one — the "remote wins" side of sync.
        settingsManager.apply(settings)
        savedArticles.replaceAll(outcome.saved)
        feedInvalidator.invalidate(InvalidationReason.SyncApplied)
    }
}
