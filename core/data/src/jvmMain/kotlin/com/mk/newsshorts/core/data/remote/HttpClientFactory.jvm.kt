package com.mk.newsshorts.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(Java)
}

/**
 * Defaults to release, because this flag fails dangerously in one direction.
 *
 * `AppGateViewModel` skips the device-integrity check when this is true, so a
 * build that wrongly reports debug ships with the root and emulator policy
 * switched off, and Ktor logs whole response bodies. These actuals used to
 * return a hardcoded `true` on the grounds that no build of the target had
 * shipped yet — which is true right up until one does, and nothing would have
 * failed to say so.
 */
actual fun isDebugBuild(): Boolean =
    // Opt in explicitly when running the desktop app from a dev machine; a
    // property nobody sets cannot be on by accident in a distributed build.
    System.getProperty("newsshorts.debug") == "true"
