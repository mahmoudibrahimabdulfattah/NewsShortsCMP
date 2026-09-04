package com.mk.newsshorts.feature.saved.di

import com.mk.newsshorts.core.data.local.SettingsManager
import com.mk.newsshorts.core.domain.saved.SavedArticles
import com.mk.newsshorts.feature.saved.SavedArticlesViewModel
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.getStrings
import org.koin.dsl.module

val savedModule = module {
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
}
