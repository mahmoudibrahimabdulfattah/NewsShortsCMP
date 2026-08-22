package com.mk.newsshorts.testing

import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.sync.RemoteSyncClient
import com.mk.newsshorts.sync.SyncDelete
import com.mk.newsshorts.sync.SyncFetch
import com.mk.newsshorts.sync.SyncedSettings
import kotlinx.coroutines.delay

class FakeRemoteSyncClient : RemoteSyncClient {
    var savedArticles: SyncFetch<List<NewsArticle>> = SyncFetch.NotFound
    var settings: SyncFetch<SyncedSettings> = SyncFetch.NotFound
    var deleteUserDataResult: SyncDelete = SyncDelete.Success

    var fetchSavedArticlesError: Throwable? = null
    var pushSavedArticlesError: Throwable? = null
    var fetchSettingsError: Throwable? = null
    var pushSettingsError: Throwable? = null
    var deleteUserDataError: Throwable? = null

    var fetchSavedArticlesDelayMs: Long = 0
    var pushSavedArticlesDelayMs: Long = 0
    var fetchSettingsDelayMs: Long = 0
    var pushSettingsDelayMs: Long = 0
    var deleteUserDataDelayMs: Long = 0

    val calls = mutableListOf<RemoteSyncCall>()
    val completedCalls = mutableListOf<RemoteSyncCall>()
    val pushedSavedArticles = mutableListOf<Pair<String, List<NewsArticle>>>()
    val pushedSettings = mutableListOf<Pair<String, SyncedSettings>>()
    val deletedUids = mutableListOf<String>()

    override suspend fun fetchSavedArticles(uid: String): SyncFetch<List<NewsArticle>> {
        val call = RemoteSyncCall.FetchSavedArticles(uid)
        calls += call
        delay(fetchSavedArticlesDelayMs)
        fetchSavedArticlesError?.let { throw it }
        val result = savedArticles
        completedCalls += call
        return result
    }

    override suspend fun pushSavedArticles(uid: String, articles: List<NewsArticle>) {
        val call = RemoteSyncCall.PushSavedArticles(uid, articles)
        calls += call
        delay(pushSavedArticlesDelayMs)
        pushSavedArticlesError?.let { throw it }
        pushedSavedArticles += uid to articles
        completedCalls += call
    }

    override suspend fun fetchSettings(uid: String): SyncFetch<SyncedSettings> {
        val call = RemoteSyncCall.FetchSettings(uid)
        calls += call
        delay(fetchSettingsDelayMs)
        fetchSettingsError?.let { throw it }
        val result = settings
        completedCalls += call
        return result
    }

    override suspend fun pushSettings(uid: String, settings: SyncedSettings) {
        val call = RemoteSyncCall.PushSettings(uid, settings)
        calls += call
        delay(pushSettingsDelayMs)
        pushSettingsError?.let { throw it }
        pushedSettings += uid to settings
        completedCalls += call
    }

    override suspend fun deleteUserData(uid: String): SyncDelete {
        val call = RemoteSyncCall.DeleteUserData(uid)
        calls += call
        delay(deleteUserDataDelayMs)
        deleteUserDataError?.let { throw it }
        val result = deleteUserDataResult
        if (result == SyncDelete.Success) {
            deletedUids += uid
        }
        completedCalls += call
        return result
    }

    fun reset() {
        savedArticles = SyncFetch.NotFound
        settings = SyncFetch.NotFound
        deleteUserDataResult = SyncDelete.Success
        fetchSavedArticlesError = null
        pushSavedArticlesError = null
        fetchSettingsError = null
        pushSettingsError = null
        deleteUserDataError = null
        fetchSavedArticlesDelayMs = 0
        pushSavedArticlesDelayMs = 0
        fetchSettingsDelayMs = 0
        pushSettingsDelayMs = 0
        deleteUserDataDelayMs = 0
        calls.clear()
        completedCalls.clear()
        pushedSavedArticles.clear()
        pushedSettings.clear()
        deletedUids.clear()
    }
}

sealed interface RemoteSyncCall {
    data class FetchSavedArticles(val uid: String) : RemoteSyncCall
    data class PushSavedArticles(
        val uid: String,
        val articles: List<NewsArticle>,
    ) : RemoteSyncCall
    data class FetchSettings(val uid: String) : RemoteSyncCall
    data class PushSettings(val uid: String, val settings: SyncedSettings) : RemoteSyncCall
    data class DeleteUserData(val uid: String) : RemoteSyncCall
}
