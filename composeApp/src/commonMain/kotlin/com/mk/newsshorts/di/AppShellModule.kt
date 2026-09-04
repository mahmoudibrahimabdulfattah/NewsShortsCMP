package com.mk.newsshorts.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.feature.appgate.AppGateViewModel
import com.mk.newsshorts.feature.onboarding.OnboardingViewModel
import com.mk.newsshorts.presentation.viewmodel.AppShellViewModel
import org.koin.dsl.module

/**
 * The ViewModels the shell owns rather than any one feature.
 *
 * App-gate and onboarding are first-run routes that `App.kt` selects between,
 * and the shell ViewModel is what every feature reaches the outside world
 * through. None of the three belongs to a feature, so none of them has a
 * feature module to live in.
 */
val appShellModule = module {
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
            navigator = get(),
        )
    }
}
