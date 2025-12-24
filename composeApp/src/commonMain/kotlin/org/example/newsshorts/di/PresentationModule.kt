package org.example.newsshorts.di

import org.example.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.dsl.module

val presentationModule = module {
    single {
        NewsViewModel(
            getTopHeadlinesUseCase = get(),
            settingsManager = get()
        )
    }
}

