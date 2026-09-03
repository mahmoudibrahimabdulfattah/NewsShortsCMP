package com.mk.newsshorts.core.model.config

import com.mk.newsshorts.core.contract.config.AppConfigDto

/** Present only when this build is below the minimum the backend still serves. */
data class RequiredUpdate(val storeUrl: String)

/**
 * The decision itself, kept free of the network so the rule that can lock every
 * reader out of the app is testable on its own.
 *
 * Returns null in every ambiguous case. A reader offline, on a flaky
 * connection, or hitting a half-published file must not lose the app they
 * already have — this gate only closes on a clear answer from the server.
 */
fun requiredUpdateFor(config: AppConfigDto, currentVersionCode: Int): RequiredUpdate? {
    if (currentVersionCode >= config.minSupportedVersionCode) return null
    // Blocking with no way forward would be worse than the outdated build, so
    // an unusable store link means no gate.
    val storeUrl = config.storeUrl.takeIf { it.startsWith("https://") } ?: return null
    return RequiredUpdate(storeUrl = storeUrl)
}
