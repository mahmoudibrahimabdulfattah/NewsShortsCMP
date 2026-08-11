package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel

@Composable
expect fun provideNewsViewModel(): NewsViewModel

