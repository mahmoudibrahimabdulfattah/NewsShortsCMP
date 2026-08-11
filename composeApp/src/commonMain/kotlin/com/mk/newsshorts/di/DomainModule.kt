package com.mk.newsshorts.di

import com.mk.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.domain.use_case.SearchNewsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetTopHeadlinesUseCase(newsRepository = get()) }
    factory { SearchNewsUseCase(newsRepository = get()) }
}
