package com.mk.newsshorts.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mk.newsshorts.feature.appgate.AppGateViewModel
import com.mk.newsshorts.feature.auth.AuthViewModel
import com.mk.newsshorts.feature.inbox.InboxViewModel
import com.mk.newsshorts.feature.onboarding.OnboardingViewModel
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.feature.feed.FeedViewModel
import com.mk.newsshorts.presentation.viewmodel.AppShellViewModel
import org.koin.mp.KoinPlatform

@Composable
actual fun provideFeedViewModel(): FeedViewModel {
    return remember { KoinPlatform.getKoin().get<FeedViewModel>() }
}

@Composable
actual fun provideAuthViewModel(): AuthViewModel {
    return remember { KoinPlatform.getKoin().get<AuthViewModel>() }
}

@Composable
actual fun provideInboxViewModel(): InboxViewModel =
    remember { KoinPlatform.getKoin().get<InboxViewModel>() }

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

@Composable
actual fun provideAppGateViewModel(): AppGateViewModel =
    remember { KoinPlatform.getKoin().get<AppGateViewModel>() }

@Composable
actual fun provideOnboardingViewModel(): OnboardingViewModel =
    remember { KoinPlatform.getKoin().get<OnboardingViewModel>() }

@Composable
actual fun provideAppShellViewModel(): AppShellViewModel =
    remember { KoinPlatform.getKoin().get<AppShellViewModel>() }
