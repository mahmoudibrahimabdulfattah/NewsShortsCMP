package com.mk.newsshorts.android

import com.mk.newsshorts.NewsShortsApplication

/** Hands the shared application the values only the app module's BuildConfig carries. */
class NewsShortsAndroidApplication : NewsShortsApplication() {
    override val isDebugBuild: Boolean = BuildConfig.DEBUG
    override val expectedSigningSha256: String = BuildConfig.EXPECTED_SIGNING_SHA256
}
