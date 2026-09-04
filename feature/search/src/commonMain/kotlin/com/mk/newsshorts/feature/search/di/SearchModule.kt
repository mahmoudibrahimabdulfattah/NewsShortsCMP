package com.mk.newsshorts.feature.search.di

import com.mk.newsshorts.feature.search.SearchViewModel
import org.koin.dsl.module

val searchModule = module {
    single(createdAtStart = false) {
        SearchViewModel(
            searchNews = get(),
            recentSearches = get(),
            analytics = get(),
        )
    }
}
