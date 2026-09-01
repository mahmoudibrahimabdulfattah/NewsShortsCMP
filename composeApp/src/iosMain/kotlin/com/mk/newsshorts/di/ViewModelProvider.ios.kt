package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
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

@Composable
actual fun provideSavedArticlesViewModel(): SavedArticlesViewModel =
    remember { KoinPlatform.getKoin().get<SavedArticlesViewModel>() }

@Composable
actual fun provideSettingsViewModel(): SettingsViewModel =
    remember { KoinPlatform.getKoin().get<SettingsViewModel>() }
