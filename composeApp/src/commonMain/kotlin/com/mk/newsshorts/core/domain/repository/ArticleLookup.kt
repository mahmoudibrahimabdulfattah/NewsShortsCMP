package com.mk.newsshorts.core.domain.repository

import com.mk.newsshorts.core.model.NewsArticle

interface ArticleLookup {
    suspend fun find(url: String): NewsArticle?
}
