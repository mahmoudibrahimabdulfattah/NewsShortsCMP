package com.mk.newsshorts.notifications

import com.mk.newsshorts.data.local.SettingsPersistence
import com.mk.newsshorts.domain.model.FeedLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PushSubscriptionSynchronizer(
    private val settingsManager: SettingsPersistence,
    private val pushSubscriber: PushSubscriber,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    init {
        syncScope.launch {
            settingsManager.preferences
                .map { preferences ->
                    PushSubscription(
                        enabled = preferences.notificationsEnabled,
                        language = preferences.newsLanguage,
                    )
                }
                .distinctUntilChanged()
                .collect { subscription ->
                    if (subscription.enabled) {
                        pushSubscriber.subscribeToLanguage(
                            FeedLanguage.resolve(subscription.language),
                        )
                    } else {
                        pushSubscriber.unsubscribeAll()
                    }
                }
        }
    }
}

private data class PushSubscription(
    val enabled: Boolean,
    val language: String,
)
