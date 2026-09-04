package com.mk.newsshorts.feature.feed.di

import com.mk.newsshorts.feature.feed.FeedViewModel
import org.koin.dsl.module

val feedModule = module {
    single(createdAtStart = false) {
        FeedViewModel(
            getTopHeadlinesUseCase = get(),
            settingsManager = get(),
            analytics = get(),
            savedArticles = get(),
            syncPublisher = get(),
            feedInvalidator = get(),
            seenArticlesStore = get(),
            navigator = get(),
        )
    }
}
