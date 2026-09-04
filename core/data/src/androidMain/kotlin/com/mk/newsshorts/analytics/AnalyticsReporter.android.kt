package com.mk.newsshorts.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mk.newsshorts.core.data.platform.NoOpAnalyticsReporter
import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.model.analytics.AnalyticsEvent

/**
 * Reports to Firebase Analytics and Crashlytics.
 *
 * Firebase is only initialized when google-services.json was present at build
 * time, so [createAnalyticsReporter] falls back to discarding events rather
 * than failing a launch that doesn't have it.
 */
private class FirebaseAnalyticsReporter(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : AnalyticsReporter {

    override fun logEvent(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            event.params.forEach { (key, value) -> putString(key, value) }
        }
        analytics.logEvent(event.name, bundle)
    }

    override fun setProperty(name: String, value: String) {
        analytics.setUserProperty(name, value)
        crashlytics.setCustomKey(name, value)
    }

    override fun recordError(message: String, cause: Throwable?) {
        crashlytics.log(message)
        cause?.let { crashlytics.recordException(it) }
    }
}

/**
 * Lint flags `MissingPermission` here because it checks this module's manifest
 * in isolation, and a library has none — but INTERNET, ACCESS_NETWORK_STATE and
 * WAKE_LOCK are all present in the app's merged manifest (the first from
 * :composeApp, the other two merged in by firebase-analytics itself). Verified
 * against composeApp's processDebugMainManifest output rather than assumed.
 */
@SuppressLint("MissingPermission")
fun createAnalyticsReporter(context: Context): AnalyticsReporter {
    if (FirebaseApp.getApps(context).isEmpty()) return NoOpAnalyticsReporter
    return FirebaseAnalyticsReporter(
        analytics = FirebaseAnalytics.getInstance(context),
        crashlytics = FirebaseCrashlytics.getInstance(),
    )
}
