package org.example.newsshorts.di

import androidx.compose.runtime.Composable
import org.example.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun provideNewsViewModel(): NewsViewModel {
    return koinViewModel()
}

