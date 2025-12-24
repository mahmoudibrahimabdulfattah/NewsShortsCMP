package org.example.newsshorts.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.example.newsshorts.presentation.viewmodel.NewsViewModel
import org.koin.mp.KoinPlatform

@Composable
actual fun provideNewsViewModel(): NewsViewModel {
    return remember { KoinPlatform.getKoin().get<NewsViewModel>() }
}

