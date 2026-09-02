package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import com.mk.newsshorts.feature.auth.AuthViewModel
import com.mk.newsshorts.feature.inbox.InboxViewModel
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.presentation.viewmodel.NewsViewModel

@Composable
expect fun provideNewsViewModel(): NewsViewModel

@Composable
expect fun provideAuthViewModel(): AuthViewModel

@Composable
expect fun provideInboxViewModel(): InboxViewModel

@Composable
expect fun provideSearchViewModel(): SearchViewModel

@Composable
expect fun provideSavedArticlesViewModel(): SavedArticlesViewModel

@Composable
expect fun provideSettingsViewModel(): SettingsViewModel
