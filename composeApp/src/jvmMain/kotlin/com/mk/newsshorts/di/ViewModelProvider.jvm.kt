package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun provideNewsViewModel(): NewsViewModel {
    return koinViewModel()
}

