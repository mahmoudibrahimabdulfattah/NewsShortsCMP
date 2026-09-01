package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.mp.KoinPlatform

@Composable
actual fun provideNewsViewModel(): NewsViewModel {
    return remember { KoinPlatform.getKoin().get<NewsViewModel>() }
}

@Composable
actual fun provideSearchViewModel(): SearchViewModel {
    return remember { KoinPlatform.getKoin().get<SearchViewModel>() }
}
