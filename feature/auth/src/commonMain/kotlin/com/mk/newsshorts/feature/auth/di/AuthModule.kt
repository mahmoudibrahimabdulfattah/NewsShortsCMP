package com.mk.newsshorts.feature.auth.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.feature.auth.AuthViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    viewModel {
        val settingsManager = get<SettingsManager>()
        AuthViewModel(
            authClient = get(),
            authSession = get(),
            pendingSignInEmailStore = get(),
            signInLinkBus = get(),
            navigator = get(),
            deleteAccountUseCase = get(),
            appLocaleCode = { settingsManager.preferences.value.appLocale },
        )
    }
}
