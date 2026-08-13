package com.mk.newsshorts.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands a followed sign-in link to the ViewModel.
 *
 * Separate from [DeepLinkBus] because the payloads are not the same kind of
 * thing — an article link is parsed into a known shape here, while a sign-in
 * link is an opaque string only Firebase can judge — but the mechanics are
 * identical, and for the same reason: a link followed from a mail app almost
 * always starts the process, so the intent lands before the ViewModel exists.
 * A [StateFlow] holds it until something is there to read it.
 *
 * [consume] matters just as much. The bus outlives the Activity, so a link left
 * in place would be redeemed again on every return to the foreground — and a
 * sign-in link is single-use, so the second attempt would surface as a spurious
 * "this link no longer works".
 */
class SignInLinkBus {

    private val mutablePending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = mutablePending.asStateFlow()

    fun post(link: String) {
        mutablePending.value = link
    }

    fun consume() {
        mutablePending.value = null
    }
}
