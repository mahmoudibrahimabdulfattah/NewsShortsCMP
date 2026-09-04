package com.mk.newsshorts.di

import com.mk.newsshorts.feature.auth.di.authModule
import com.mk.newsshorts.feature.feed.di.feedModule
import com.mk.newsshorts.feature.inbox.di.inboxModule
import com.mk.newsshorts.feature.saved.di.savedModule
import com.mk.newsshorts.feature.search.di.searchModule
import com.mk.newsshorts.feature.settings.di.settingsModule
import com.mk.newsshorts.navigation.navigationModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * The composition root: the one place that knows every module exists.
 *
 * Each feature ships its own Koin module, so adding a feature is adding a line
 * here rather than editing a shared file that lists every ViewModel in the app.
 */
private val appModules: List<Module> = listOf(
    dataModule,
    domainModule,
    navigationModule,
    feedModule,
    savedModule,
    searchModule,
    settingsModule,
    authModule,
    inboxModule,
    appShellModule,
)

fun initializeKoin(
    platformModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {}
): KoinApplication {
    return startKoin {
        appDeclaration()
        // Common modules bind the no-op defaults; platform modules come last
        // so Android's real clients and Context-backed storage override them.
        modules(appModules + platformModules)
    }
}
