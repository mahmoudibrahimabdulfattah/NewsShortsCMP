package com.mk.newsshorts.feature.inbox

import com.mk.newsshorts.core.data.local.InboxReadState
import com.mk.newsshorts.core.data.local.NotificationInboxStore
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import com.mk.newsshorts.core.data.local.articleKey
import com.mk.newsshorts.core.data.remote.NotificationInboxClient
import com.mk.newsshorts.core.model.FeedLanguage
import com.mk.newsshorts.core.model.deeplink.ArticleDeepLink
import com.mk.newsshorts.core.model.deeplink.ArticleDeepLinks
import com.mk.newsshorts.core.model.inbox.InboxNotification
import com.mk.newsshorts.navigation.NotificationBus
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    /** What the backend has pushed in the reader's language, newest first. */
    val notifications: List<InboxNotification> = emptyList(),
    /**
     * Which notifications the reader has dealt with. Mirrored into state rather
     * than read from the store per frame, so the marks hold still while the
     * screen is up instead of flickering off row by row.
     */
    val read: InboxReadState = InboxReadState(),
    /**
     * Articles swiped away on this device. The list is one file published for
     * every reader, so a dismissal can only ever hide a row here.
     */
    val dismissed: Set<Int> = emptySet(),
    val isRefreshing: Boolean = false,
) {
    /**
     * The list as the reader sees it: published order, minus what they have
     * swiped away. Kept separate from [notifications] so a refresh can replace
     * the published list without having to remember what was hidden.
     */
    val visibleNotifications: List<InboxNotification>
        get() = notifications.filterNot { articleKey(it.articleUrl) in dismissed }

    val unreadIds: Set<Long>
        get() = visibleNotifications
            .filterNot { read.isRead(it.sentAt, it.articleUrl) }
            .map { it.sentAt }
            .toSet()

    val unreadCount: Int get() = unreadIds.size
}

sealed interface InboxUiEvent {
    /** Opens the inbox. Deliberately does not mark anything read. */
    data object Opened : InboxUiEvent
    /**
     * A row in the inbox. It carries the link and nothing else: the same
     * handler serves a tap here and a tap on the notification in the tray, so
     * both clear the mark by the same route.
     */
    data class OpenNotification(val deepLink: String) : InboxUiEvent
    /** The other one. Clears every mark currently in the list. */
    data object MarkAllRead : InboxUiEvent
    /** Pull-to-refresh on the inbox. */
    data object Refresh : InboxUiEvent
    /**
     * Swiped away. Local only - the list is published for every reader, so this
     * hides the row on this device and nothing more.
     */
    data class DismissNotification(val articleUrl: String) : InboxUiEvent
    /** What the snackbar's undo does. */
    data class RestoreNotification(val articleUrl: String) : InboxUiEvent
}

sealed interface InboxUiEffect {
    data object OpenInboxOverlay : InboxUiEffect
    data class OpenNotification(val link: ArticleDeepLink) : InboxUiEffect
}

class InboxViewModel(
    private val notificationInboxClient: NotificationInboxClient,
    private val notificationInboxStore: NotificationInboxStore,
    private val notificationBus: NotificationBus,
    private val settingsManager: SettingsPersistence,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(
        InboxUiState(
            read = notificationInboxStore.read(),
            dismissed = notificationInboxStore.dismissed(),
        )
    )
    val uiState: StateFlow<InboxUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<InboxUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<InboxUiEffect> = effectChannel.receiveAsFlow()

    private val inboxScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private var observedNewsLanguage: String? = null

    init {
        observeNewsLanguage()
        observeReadMarks()
        observeDismissals()
        observeArrivingNotifications()
    }

    fun processEvent(event: InboxUiEvent) {
        when (event) {
            InboxUiEvent.Opened -> handleOpenInbox()
            is InboxUiEvent.OpenNotification -> handleOpenNotification(event.deepLink)
            InboxUiEvent.MarkAllRead -> handleMarkAllRead()
            InboxUiEvent.Refresh -> refreshNotificationInbox(pulled = true)
            is InboxUiEvent.DismissNotification -> handleDismissNotification(event.articleUrl)
            is InboxUiEvent.RestoreNotification -> handleRestoreNotification(event.articleUrl)
        }
    }

    /**
     * Loads what has been pushed in the reader's language.
     *
     * Called on launch and not only when the inbox is opened, because the bell
     * carries the unread mark and a mark that only appears after you look is
     * not a mark. One small file, and a failure leaves the previous list in
     * place rather than emptying the screen.
     */
    private fun refreshNotificationInbox(pulled: Boolean = false) {
        refreshNotificationInbox(language = currentNewsLanguage(), pulled = pulled)
    }

    private fun refreshNotificationInbox(language: String, pulled: Boolean = false) {
        if (pulled) mutableState.update { it.copy(isRefreshing = true) }
        inboxScope.launch {
            val sent = notificationInboxClient.fetch(language)
            // An empty answer is a failure as often as it is an empty inbox -
            // the client cannot tell them apart - so the list stands rather
            // than being wiped by a bad connection. The spinner still stops.
            if (sent.isEmpty()) {
                mutableState.update { it.copy(isRefreshing = false) }
                return@launch
            }
            mutableState.update { state ->
                state.copy(
                    notifications = sent.map {
                        InboxNotification(
                            sentAt = it.sentAt,
                            title = it.title,
                            body = it.body,
                            deepLink = it.deepLink,
                            articleUrl = ArticleDeepLinks.parse(it.deepLink)?.url.orEmpty(),
                        )
                    },
                    read = notificationInboxStore.read(),
                    dismissed = notificationInboxStore.dismissed(),
                    isRefreshing = false,
                )
            }
        }
    }

    /**
     * Opens the inbox and marks nothing.
     *
     * Looking at a list is not the same as having read what is in it. A reader
     * opens this to find the story they were told about and have not been into
     * yet, so the marks have to survive the act of looking - they come off when
     * a notification is opened, or when the reader says so for all of them.
     */
    private fun handleOpenInbox() {
        inboxScope.launch {
            effectChannel.send(InboxUiEffect.OpenInboxOverlay)
        }
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
    private fun handleDismissNotification(articleUrl: String) {
        notificationInboxStore.dismiss(articleUrl)
    }

    /**
     * Undo. A swipe is one gesture away from a story the reader wanted, and
     * the row cannot be recovered from anywhere else once it is hidden.
     */
    private fun handleRestoreNotification(articleUrl: String) {
        notificationInboxStore.restore(articleUrl)
    }

    /** One of the two things that clears a mark; see [handleOpenNotification]. */
    private fun handleMarkAllRead() {
        val newest = mutableState.value.visibleNotifications.maxOfOrNull { it.sentAt } ?: return
        notificationInboxStore.markAllRead(newest)
    }

    /**
     * A row carries the notification's own link, so this is the same path a
     * notification tap takes - including the details screen it lands on and the
     * origin it is reported under.
     */
    private fun handleOpenNotification(deepLink: String) {
        val link = ArticleDeepLinks.parse(deepLink) ?: return
        inboxScope.launch {
            effectChannel.send(InboxUiEffect.OpenNotification(link))
        }
    }

    private fun observeNewsLanguage() {
        inboxScope.launch {
            settingsManager.preferences.collect { preferences ->
                val language = FeedLanguage.resolve(preferences.newsLanguage)
                if (language == observedNewsLanguage) return@collect
                observedNewsLanguage = language
                refreshNotificationInbox(language)
            }
        }
    }

    private fun observeReadMarks() {
        inboxScope.launch {
            notificationInboxStore.readState.collect { read ->
                mutableState.update { it.copy(read = read) }
            }
        }
    }

    private fun observeDismissals() {
        inboxScope.launch {
            notificationInboxStore.dismissedState.collect { dismissed ->
                mutableState.update { it.copy(dismissed = dismissed) }
            }
        }
    }

    /**
     * Merges a notification into the inbox the moment it arrives, without
     * waiting for the backend to republish.
     *
     * The published file is written in the same run that sends the push, but it
     * reaches a reader through a static deploy that takes minutes - long enough
     * for someone who taps straight into the app to look for the notification
     * they were just shown and not find it.
     *
     * Merged rather than prepended blindly: the published list arrives too, and
     * both describe the same send.
     */
    private fun observeArrivingNotifications() {
        inboxScope.launch {
            notificationBus.latest.collect { arrived ->
                if (arrived == null) return@collect
                mutableState.update { state ->
                    if (state.notifications.any { it.sentAt == arrived.sentAt }) state
                    else state.copy(
                        notifications = (listOf(arrived) + state.notifications)
                            .sortedByDescending { it.sentAt },
                    )
                }
            }
        }
    }

    private fun currentNewsLanguage(): String =
        FeedLanguage.resolve(settingsManager.preferences.value.newsLanguage)
}
