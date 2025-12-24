package org.example.newsshorts.di

import org.example.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import org.example.newsshorts.domain.use_case.SearchNewsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetTopHeadlinesUseCase(newsRepository = get()) }
    factory { SearchNewsUseCase(newsRepository = get()) }
}

