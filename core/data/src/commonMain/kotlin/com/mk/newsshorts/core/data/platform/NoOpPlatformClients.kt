package com.mk.newsshorts.core.data.platform

import com.mk.newsshorts.core.domain.analytics.AnalyticsReporter
import com.mk.newsshorts.core.domain.auth.AuthClient
import com.mk.newsshorts.core.domain.notifications.PushSubscriber
import com.mk.newsshorts.core.domain.security.DeviceIntegrityInspector
import com.mk.newsshorts.core.domain.sync.RemoteSyncClient
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.analytics.AnalyticsEvent
import com.mk.newsshorts.core.model.auth.AuthFailure
import com.mk.newsshorts.core.model.auth.AuthResult
import com.mk.newsshorts.core.model.auth.AuthUser
import com.mk.newsshorts.core.model.security.DeviceIntegrity
import com.mk.newsshorts.core.model.sync.SyncDelete
import com.mk.newsshorts.core.model.sync.SyncFetch
import com.mk.newsshorts.core.model.sync.SyncedSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Discards everything. Used on targets without Firebase. */
object NoOpAnalyticsReporter : AnalyticsReporter {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun setProperty(name: String, value: String) = Unit
    override fun recordError(message: String, cause: Throwable?) = Unit
}

/** Used on targets without push support. */
object NoOpPushSubscriber : PushSubscriber {
    override fun subscribeToLanguage(language: String) = Unit
    override fun unsubscribeAll() = Unit
}

/** Used on every target except Android, where no sign-in provider ships. */
object NoOpAuthClient : AuthClient {
    override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(null).asStateFlow()

    private val unsupported = AuthResult.Error(AuthFailure.UNSUPPORTED_PLATFORM)

    override suspend fun signInWithGoogle(): AuthResult = unsupported
    override suspend fun sendSignInLink(email: String, languageCode: String): AuthResult =
        unsupported
    override fun isSignInLink(link: String): Boolean = false
    override suspend fun completeSignInWithLink(email: String, link: String): AuthResult = unsupported
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): AuthResult = unsupported
}

/** Used on every target without a Firestore backend; there is no remote copy to strand. */
object NoOpRemoteSyncClient : RemoteSyncClient {
    override suspend fun fetchSavedArticles(uid: String): SyncFetch<List<NewsArticle>> = SyncFetch.Unavailable
    override suspend fun pushSavedArticles(uid: String, articles: List<NewsArticle>) = Unit
    override suspend fun fetchSettings(uid: String): SyncFetch<SyncedSettings> = SyncFetch.Unavailable
    override suspend fun pushSettings(uid: String, settings: SyncedSettings) = Unit
    override suspend fun deleteUserData(uid: String): SyncDelete = SyncDelete.Success
}

/** Used on targets with nothing meaningful to check. */
object NoOpDeviceIntegrityInspector : DeviceIntegrityInspector {
    override fun inspect(): DeviceIntegrity = DeviceIntegrity()
}
