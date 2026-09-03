package com.mk.newsshorts.navigation

import com.mk.newsshorts.core.model.deeplink.ArticleDeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What arrived, and how much work is left before an article can be shown. */
sealed interface PendingLink {

    /** Ready to open: a notification, or a link that carries its own article. */
    data class Article(val link: ArticleDeepLink) : PendingLink

    /**
     * A per-article landing page, which names a story rather than describing
     * one. Someone has to fetch it before there is anything to open — see
     * [com.mk.newsshorts.core.data.remote.SharePageResolver].
     */
    data class SharePage(val url: String) : PendingLink
}

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

    private val mutablePending = MutableStateFlow<PendingLink?>(null)
    val pending: StateFlow<PendingLink?> = mutablePending.asStateFlow()

    fun post(link: ArticleDeepLink) {
        mutablePending.value = PendingLink.Article(link)
    }

    fun postSharePage(url: String) {
        mutablePending.value = PendingLink.SharePage(url)
    }

    fun consume() {
        mutablePending.value = null
    }
}
