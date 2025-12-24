package org.example.newsshorts.di

import androidx.compose.runtime.Composable
import org.example.newsshorts.presentation.viewmodel.NewsViewModel

@Composable
expect fun provideNewsViewModel(): NewsViewModel

