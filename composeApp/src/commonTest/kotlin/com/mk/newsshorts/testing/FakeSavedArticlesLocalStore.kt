package com.mk.newsshorts.testing

import com.mk.newsshorts.core.data.local.SavedArticlesLocalStore
import com.mk.newsshorts.core.data.local.cappedForStorage
import com.mk.newsshorts.core.model.NewsArticle

/**
 * In-memory stand-in for `SavedArticlesStore`, which needs a `SettingsStorage`
 * that commonTest has no actual for. Caps through the same function the real
 * store uses, so a test that fills it past the limit sees real behaviour.
 */
class FakeSavedArticlesLocalStore(
    initial: List<NewsArticle> = emptyList(),
) : SavedArticlesLocalStore {

    var contents: List<NewsArticle> = cappedForStorage(initial)
        private set

    var saveCount: Int = 0
        private set

    override fun load(): List<NewsArticle> = contents

    override fun save(articles: List<NewsArticle>) {
        contents = cappedForStorage(articles)
        saveCount++
    }
}
