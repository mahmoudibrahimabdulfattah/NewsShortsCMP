package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import com.mk.newsshorts.feature.appgate.AppGateViewModel
import com.mk.newsshorts.feature.auth.AuthViewModel
import com.mk.newsshorts.feature.inbox.InboxViewModel
import com.mk.newsshorts.feature.onboarding.OnboardingViewModel
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.feature.feed.FeedViewModel
import com.mk.newsshorts.presentation.viewmodel.AppShellViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun provideFeedViewModel(): FeedViewModel {
    return koinViewModel()
}

@Composable
actual fun provideAuthViewModel(): AuthViewModel {
    return koinViewModel()
}

@Composable
actual fun provideInboxViewModel(): InboxViewModel = koinViewModel()

@Composable
actual fun provideSearchViewModel(): SearchViewModel {
    return koinViewModel()
}

@Composable
actual fun provideSavedArticlesViewModel(): SavedArticlesViewModel = koinViewModel()

@Composable
actual fun provideSettingsViewModel(): SettingsViewModel = koinViewModel()

@Composable
actual fun provideAppGateViewModel(): AppGateViewModel = koinViewModel()

@Composable
actual fun provideOnboardingViewModel(): OnboardingViewModel = koinViewModel()

@Composable
actual fun provideAppShellViewModel(): AppShellViewModel =
    koinViewModel()
