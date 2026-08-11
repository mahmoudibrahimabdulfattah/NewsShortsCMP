package com.mk.newsshorts.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands a tapped notification or deep link to the ViewModel.
 *
 * Deliberately a [StateFlow] and not a SharedFlow: on a cold start the platform
 * delivers the intent before composition has built the ViewModel, so a
 * replayless stream would drop the link — intermittently, and more often on
 * slower devices. Holding the value means the delivery order stops mattering.
 *
 * [consume] is equally load-bearing. The ViewModel and this bus are process-wide
 * singletons that outlive the Activity, so a link left in place would re-open
 * the details screen every time the app came back to the foreground.
 */
class DeepLinkBus {

    private val mutablePending = MutableStateFlow<ArticleDeepLink?>(null)
    val pending: StateFlow<ArticleDeepLink?> = mutablePending.asStateFlow()

    fun post(link: ArticleDeepLink) {
        mutablePending.value = link
    }

    fun consume() {
        mutablePending.value = null
    }
}
