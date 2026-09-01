package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun provideNewsViewModel(): NewsViewModel {
    return koinViewModel()
}

@Composable
actual fun provideSearchViewModel(): SearchViewModel {
    return koinViewModel()
}

@Composable
actual fun provideSavedArticlesViewModel(): SavedArticlesViewModel = koinViewModel()

@Composable
actual fun provideSettingsViewModel(): SettingsViewModel = koinViewModel()
