package com.mk.newsshorts.feature.feed.di

import com.mk.newsshorts.feature.feed.FeedViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val feedModule = module {
    viewModel {
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
