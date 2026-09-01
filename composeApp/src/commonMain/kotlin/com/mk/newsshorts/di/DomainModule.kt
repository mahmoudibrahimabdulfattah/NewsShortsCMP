package com.mk.newsshorts.di

import com.mk.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import com.mk.newsshorts.feature.search.SearchNewsUseCase
import com.mk.newsshorts.feature.search.SearchNews
import org.koin.dsl.module

val domainModule = module {
    factory { GetTopHeadlinesUseCase(newsRepository = get()) }
    factory<SearchNews> { SearchNewsUseCase(newsRepository = get()) }
}
