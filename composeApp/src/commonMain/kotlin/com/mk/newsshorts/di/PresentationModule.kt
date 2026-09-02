package com.mk.newsshorts.di

import com.mk.newsshorts.data.local.SettingsManager
import com.mk.newsshorts.data.repository.SavedArticles
import com.mk.newsshorts.feature.appgate.AppGateViewModel
import com.mk.newsshorts.feature.auth.AuthViewModel
import com.mk.newsshorts.feature.inbox.InboxViewModel
import com.mk.newsshorts.feature.onboarding.OnboardingViewModel
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.feature.search.SearchViewModel
import com.mk.newsshorts.feature.settings.SettingsViewModel
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.getStrings
import com.mk.newsshorts.feature.feed.FeedViewModel
import com.mk.newsshorts.presentation.viewmodel.AppShellViewModel
import org.koin.dsl.module

val presentationModule = module {
    single(createdAtStart = false) {
        val settingsManager = get<SettingsManager>()
        AuthViewModel(
            authClient = get(),
            authSession = get(),
            pendingSignInEmailStore = get(),
            signInLinkBus = get(),
            deleteAccountUseCase = get(),
            appLocaleCode = { settingsManager.preferences.value.appLocale },
        )
    }
    single(createdAtStart = false) {
        val settingsManager = get<SettingsManager>()
        SavedArticlesViewModel(
            repository = get<SavedArticles>(),
            analytics = get(),
            syncPublisher = get(),
            strings = {
                getStrings(AppLocale.fromCode(settingsManager.preferences.value.appLocale))
            },
        )
    }
    single(createdAtStart = false) {
        SettingsViewModel(
            settingsManager = get<SettingsManager>(),
            analytics = get(),
            syncPublisher = get(),
        )
    }
    single(createdAtStart = false) {
        SearchViewModel(
            searchNews = get(),
            recentSearches = get(),
            analytics = get(),
        )
    }
    single(createdAtStart = false) {
        AppGateViewModel(
            remoteConfigClient = get(),
            deviceIntegrityInspector = get(),
            securityFlags = get<SettingsManager>(),
            analytics = get(),
        )
    }
    single(createdAtStart = false) {
        OnboardingViewModel(
            onboardingStore = get<SettingsManager>(),
            settings = get<SettingsManager>(),
            feedInvalidator = get(),
        )
    }
    single(createdAtStart = false) {
        InboxViewModel(
            notificationInboxClient = get(),
            notificationInboxStore = get(),
            notificationBus = get(),
            settingsManager = get<SettingsManager>(),
        )
    }
    single(createdAtStart = false) {
        FeedViewModel(
            getTopHeadlinesUseCase = get(),
            settingsManager = get(),
            analytics = get(),
            savedArticles = get(),
            syncPublisher = get(),
            feedInvalidator = get(),
            seenArticlesStore = get(),
        )
    }
    single(createdAtStart = false) {
        AppShellViewModel(
            settingsManager = get(),
            analytics = get(),
            deepLinkBus = get(),
            savedArticles = get(),
            accountSync = get(),
            authSession = get(),
            syncPublisher = get(),
            feedInvalidator = get(),
            articleLookup = get(),
            sharePageResolver = get(),
            inboxReadMarker = get(),
        )
    }
}
