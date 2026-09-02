package com.mk.newsshorts.core.domain.saved

import com.mk.newsshorts.core.model.ToggleResult
import com.mk.newsshorts.core.model.NewsArticle
import kotlinx.coroutines.flow.StateFlow

interface SavedArticles {
    val saved: StateFlow<List<NewsArticle>>
    val isLoaded: StateFlow<Boolean>

    suspend fun awaitLoaded()
    suspend fun load()
    fun toggle(article: NewsArticle): ToggleResult
    fun remove(article: NewsArticle): Boolean
    fun mergeWithRemote(remote: List<NewsArticle>): List<NewsArticle>
    fun replaceAll(articles: List<NewsArticle>)
}
