package com.mk.newsshorts.domain.repository

import com.mk.newsshorts.domain.model.NewsArticle

interface ArticleLookup {
    suspend fun find(url: String): NewsArticle?
}
