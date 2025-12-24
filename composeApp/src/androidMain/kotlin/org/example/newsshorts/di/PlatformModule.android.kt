package org.example.newsshorts.di

import org.example.newsshorts.data.local.SettingsStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single { SettingsStorage(context = androidContext()) }
}

