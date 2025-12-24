package org.example.newsshorts.presentation.mvi

import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.presentation.localization.AppLocale

sealed interface NewsUiEvent {
    data class SelectCategory(val category: NewsCategory) : NewsUiEvent
    data class SelectCountry(val country: CountryOption) : NewsUiEvent
    data class SelectLanguage(val language: LanguageOption) : NewsUiEvent
    data class SelectAppLocale(val locale: AppLocale) : NewsUiEvent
    data class SelectTab(val tab: NavigationTab) : NewsUiEvent
    data class ScrollToArticle(val index: Int) : NewsUiEvent
    data class OpenArticle(val articleIndex: Int) : NewsUiEvent
    data class ShareArticle(val articleIndex: Int) : NewsUiEvent
    data class SaveArticle(val articleIndex: Int) : NewsUiEvent
    data class RemoveSavedArticle(val articleIndex: Int) : NewsUiEvent
    data object RefreshNews : NewsUiEvent
    data object RetryLoading : NewsUiEvent
    data object DismissError : NewsUiEvent
    data object NavigateToSavedArticles : NewsUiEvent
    data object NavigateToLanguageSettings : NewsUiEvent
}
