package com.mk.newsshorts.core.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mk.newsshorts.core.data.platform.NoOpRemoteSyncClient
import com.mk.newsshorts.core.domain.sync.RemoteSyncClient
import com.mk.newsshorts.core.model.sync.SyncDelete
import com.mk.newsshorts.core.model.sync.SyncFetch
import com.mk.newsshorts.core.model.sync.SyncedSettings
import com.mk.newsshorts.core.model.ArticleAuthor
import com.mk.newsshorts.core.model.ArticleContent
import com.mk.newsshorts.core.model.ArticleDescription
import com.mk.newsshorts.core.model.ArticleId
import com.mk.newsshorts.core.model.ArticleTitle
import com.mk.newsshorts.core.model.ArticleUrl
import com.mk.newsshorts.core.model.ImageUrl
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsSource
import com.mk.newsshorts.core.model.PublishedTimestamp
import com.mk.newsshorts.core.model.SourceId
import com.mk.newsshorts.core.model.SourceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * One document per reader (`users/{uid}`), holding a `savedArticles` array and
 * a `settings` map — not the subcollection-per-kind shape the write-up first
 * sketched. A single document means one read and one write per sync point
 * instead of two, and the security rule is one line instead of two.
 *
 * Reads and writes plain field maps rather than `toObject()`/POJO mapping on
 * purpose: no reflective model class, so no R8 keep rule is needed for it —
 * see `proguard-rules.pro`.
 */
private class FirestoreSyncClient(private val db: FirebaseFirestore) : RemoteSyncClient {

    override suspend fun fetchSavedArticles(uid: String): SyncFetch<List<NewsArticle>> {
        val snapshot = fetchDocument(uid) ?: return SyncFetch.Unavailable
        // `contains`, not `exists` on the whole document: the settings half
        // may have synced already without the saved-articles half ever
        // having been pushed, and the two must not be conflated.
        if (!snapshot.contains(FIELD_SAVED_ARTICLES)) return SyncFetch.NotFound
        @Suppress("UNCHECKED_CAST")
        val rawArticles = snapshot.get(FIELD_SAVED_ARTICLES) as? List<Map<String, Any?>> ?: emptyList()
        return SyncFetch.Found(rawArticles.mapNotNull { it.toNewsArticle() })
    }

    override suspend fun pushSavedArticles(uid: String, articles: List<NewsArticle>) {
        runCatching {
            db.collection(USERS).document(uid)
                .set(mapOf(FIELD_SAVED_ARTICLES to articles.map { it.toFieldMap() }), SetOptions.merge())
                .await()
        }
    }

    override suspend fun fetchSettings(uid: String): SyncFetch<SyncedSettings> {
        val snapshot = fetchDocument(uid) ?: return SyncFetch.Unavailable
        if (!snapshot.contains(FIELD_SETTINGS)) return SyncFetch.NotFound
        @Suppress("UNCHECKED_CAST")
        val raw = snapshot.get(FIELD_SETTINGS) as? Map<String, Any?> ?: return SyncFetch.NotFound
        val settings = SyncedSettings(
            newsLanguage = raw["newsLanguage"] as? String ?: return SyncFetch.NotFound,
            appLocale = raw["appLocale"] as? String ?: return SyncFetch.NotFound,
            selectedCountry = raw["selectedCountry"] as? String ?: return SyncFetch.NotFound,
            themeMode = raw["themeMode"] as? String ?: return SyncFetch.NotFound,
            notificationsEnabled = raw["notificationsEnabled"] as? Boolean ?: true,
            notifyBreaking = raw["notifyBreaking"] as? Boolean ?: true,
            notifyTopStory = raw["notifyTopStory"] as? Boolean ?: true,
            notifyReminder = raw["notifyReminder"] as? Boolean ?: true,
        )
        return SyncFetch.Found(settings)
    }

    override suspend fun pushSettings(uid: String, settings: SyncedSettings) {
        runCatching {
            val map = mapOf(
                FIELD_SETTINGS to mapOf(
                    "newsLanguage" to settings.newsLanguage,
                    "appLocale" to settings.appLocale,
                    "selectedCountry" to settings.selectedCountry,
                    "themeMode" to settings.themeMode,
                    "notificationsEnabled" to settings.notificationsEnabled,
                    "notifyBreaking" to settings.notifyBreaking,
                    "notifyTopStory" to settings.notifyTopStory,
                    "notifyReminder" to settings.notifyReminder,
                )
            )
            db.collection(USERS).document(uid).set(map, SetOptions.merge()).await()
        }
    }

    // Firestore resolves a delete of a missing document successfully, so a
    // retry after a partial failure can finish instead of getting stuck.
    override suspend fun deleteUserData(uid: String): SyncDelete =
        try {
            db.collection(USERS).document(uid).delete().await()
            SyncDelete.Success
        } catch (cancellation: CancellationException) {
            // A cancelled scope is not a failed delete. Reporting it as one
            // would show the reader a deletion error for a screen they just
            // left, and DeleteAccountUseCase rethrows cancellation on purpose.
            throw cancellation
        } catch (failure: Throwable) {
            SyncDelete.Failed
        }

    private suspend fun fetchDocument(uid: String): DocumentSnapshot? =
        runCatching { db.collection(USERS).document(uid).get().await() }.getOrNull()

    private fun NewsArticle.toFieldMap(): Map<String, Any?> = mapOf(
        "title" to title.value,
        "summary" to description.value,
        "url" to articleUrl.value,
        "imageUrl" to imageUrl?.value,
        "sourceName" to source.name.value,
        "category" to category.apiValue,
        "publishedAt" to publishedAt.epochMillis,
    )

    /** Null when the row can't make a valid article; the value classes reject blanks. */
    private fun Map<String, Any?>.toNewsArticle(): NewsArticle? = runCatching {
        val url = this["url"] as String
        val title = this["title"] as String
        val summary = this["summary"] as? String ?: ""
        val sourceName = this["sourceName"] as? String ?: ""
        val imageUrl = this["imageUrl"] as? String
        val category = this["category"] as? String ?: "general"
        val publishedAt = (this["publishedAt"] as? Number)?.toLong() ?: 0L
        NewsArticle(
            id = ArticleId("synced_${url.hashCode()}"),
            title = ArticleTitle(title),
            description = ArticleDescription(summary),
            content = ArticleContent(summary),
            author = ArticleAuthor(sourceName),
            source = NewsSource(
                id = SourceId(sourceName.lowercase().replace(" ", "-")),
                name = SourceName(sourceName),
            ),
            imageUrl = imageUrl?.takeIf { it.isNotBlank() }?.let { ImageUrl(it) },
            articleUrl = ArticleUrl(url),
            publishedAt = PublishedTimestamp(publishedAt),
            category = NewsCategory.fromApiValue(category),
        )
    }.getOrNull()

    private companion object {
        const val USERS = "users"
        const val FIELD_SAVED_ARTICLES = "savedArticles"
        const val FIELD_SETTINGS = "settings"
    }
}

fun createRemoteSyncClient(context: Context): RemoteSyncClient {
    if (FirebaseApp.getApps(context).isEmpty()) return NoOpRemoteSyncClient
    return FirestoreSyncClient(FirebaseFirestore.getInstance())
}
