package org.example.newsshorts.presentation.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.newsshorts.data.local.SettingsManager
import org.example.newsshorts.domain.model.NewsCategory
import org.example.newsshorts.domain.model.NewsResult
import org.example.newsshorts.domain.use_case.GetTopHeadlinesRequest
import org.example.newsshorts.domain.use_case.GetTopHeadlinesUseCase
import org.example.newsshorts.presentation.localization.AppLocale
import org.example.newsshorts.presentation.mvi.CountryOption
import org.example.newsshorts.presentation.mvi.LanguageOption
import org.example.newsshorts.presentation.mvi.NavigationTab
import org.example.newsshorts.presentation.mvi.NewsUiEffect
import org.example.newsshorts.presentation.mvi.NewsUiEvent
import org.example.newsshorts.presentation.mvi.NewsUiState

class NewsViewModel(
    private val getTopHeadlinesUseCase: GetTopHeadlinesUseCase,
    private val settingsManager: SettingsManager
) : BaseViewModel() {

    private val mutableState: MutableStateFlow<NewsUiState> = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = mutableState.asStateFlow()

    private val mutableEffect: MutableSharedFlow<NewsUiEffect> = MutableSharedFlow()
    val uiEffect: SharedFlow<NewsUiEffect> = mutableEffect.asSharedFlow()

    init {
        loadSavedSettings()
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val savedNewsLanguage = settingsManager.newsLanguageFlow.first()
            val savedAppLocale = settingsManager.appLocaleFlow.first()
            val savedCountry = settingsManager.selectedCountryFlow.first()
            val newsLanguage = LanguageOption.entries.find { it.code == savedNewsLanguage }
                ?: LanguageOption.ENGLISH
            val appLocale = AppLocale.fromCode(savedAppLocale)
            val country = CountryOption.entries.find { it.code == savedCountry }
                ?: CountryOption.UNITED_STATES
            mutableState.update { state ->
                state.copy(
                    selectedLanguage = newsLanguage,
                    appLocale = appLocale,
                    selectedCountry = country,
                    isFirstLaunch = false
                )
            }
            loadNews()
        }
    }

    fun processEvent(event: NewsUiEvent) {
        when (event) {
            is NewsUiEvent.SelectCategory -> handleSelectCategory(event.category)
            is NewsUiEvent.SelectCountry -> handleSelectCountry(event.country)
            is NewsUiEvent.SelectLanguage -> handleSelectLanguage(event.language)
            is NewsUiEvent.SelectAppLocale -> handleSelectAppLocale(event.locale)
            is NewsUiEvent.SelectTab -> handleSelectTab(event.tab)
            is NewsUiEvent.ScrollToArticle -> handleScrollToArticle(event.index)
            is NewsUiEvent.OpenArticle -> handleOpenArticle(event.articleIndex)
            is NewsUiEvent.ShareArticle -> handleShareArticle(event.articleIndex)
            is NewsUiEvent.SaveArticle -> handleSaveArticle(event.articleIndex)
            is NewsUiEvent.RemoveSavedArticle -> handleRemoveSavedArticle(event.articleIndex)
            NewsUiEvent.RefreshNews -> handleRefreshNews()
            NewsUiEvent.RetryLoading -> handleRetryLoading()
            NewsUiEvent.DismissError -> handleDismissError()
            NewsUiEvent.NavigateToSavedArticles -> handleNavigateToSavedArticles()
            NewsUiEvent.NavigateToLanguageSettings -> handleNavigateToLanguageSettings()
        }
    }

    private fun handleSelectCategory(category: NewsCategory) {
        if (category == mutableState.value.selectedCategory) return
        mutableState.update { state ->
            state.copy(
                selectedCategory = category,
                isLoading = true,
                articles = emptyList(),
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        loadNews()
    }

    private fun handleSelectCountry(country: CountryOption) {
        if (country == mutableState.value.selectedCountry) return
        mutableState.update { state ->
            state.copy(
                selectedCountry = country,
                isLoading = true,
                articles = emptyList(),
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            settingsManager.saveSelectedCountry(country.code)
        }
        loadNewsForCountry(country)
    }

    private fun loadNewsForCountry(country: CountryOption) {
        viewModelScope.launch {
            val currentState = mutableState.value
            val request = GetTopHeadlinesRequest(
                category = currentState.selectedCategory,
                country = country.code,
                countryName = country.displayName,
                language = currentState.selectedLanguage.code,
                useCountry = true
            )
            when (val result = getTopHeadlinesUseCase.execute(request)) {
                is NewsResult.Success -> {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            articles = result.data,
                            errorMessage = null,
                            currentArticleIndex = 0,
                            isOfflineMode = false
                        )
                    }
                }
                is NewsResult.Error -> {
                    handleNewsError(result.error.message)
                }
            }
        }
    }

    private fun handleSelectLanguage(language: LanguageOption) {
        if (language == mutableState.value.selectedLanguage) return
        mutableState.update { state ->
            state.copy(
                selectedLanguage = language,
                isLoading = true,
                articles = emptyList(),
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            settingsManager.saveNewsLanguage(language.code)
            mutableEffect.emit(NewsUiEffect.ShowToast("${language.displayName}"))
        }
        loadNews()
    }

    private fun handleSelectAppLocale(locale: AppLocale) {
        if (locale == mutableState.value.appLocale) return
        mutableState.update { state ->
            state.copy(appLocale = locale)
        }
        viewModelScope.launch {
            settingsManager.saveAppLocale(locale.code)
            val message = if (locale == AppLocale.ARABIC) {
                "تم تغيير اللغة إلى العربية"
            } else {
                "Language changed to English"
            }
            mutableEffect.emit(NewsUiEffect.ShowToast(message))
        }
    }

    private fun handleSelectTab(tab: NavigationTab) {
        if (tab == mutableState.value.currentTab) return
        val needsLoading: Boolean = tab != NavigationTab.PROFILE
        mutableState.update { state ->
            state.copy(
                currentTab = tab,
                isLoading = needsLoading,
                articles = if (needsLoading) emptyList() else state.articles,
                currentArticleIndex = 0,
                errorMessage = null
            )
        }
        if (needsLoading) {
            when (tab) {
                NavigationTab.COUNTRIES -> {
                    loadNewsForCountry(mutableState.value.selectedCountry)
                }
                NavigationTab.FOR_YOU -> {
                    loadNews()
                }
                NavigationTab.PROFILE -> {
                    // No loading needed
                }
            }
        }
    }

    private fun handleScrollToArticle(index: Int) {
        mutableState.update { state ->
            state.copy(currentArticleIndex = index.coerceIn(0, state.articles.lastIndex.coerceAtLeast(0)))
        }
    }

    private fun handleOpenArticle(articleIndex: Int) {
        val article = mutableState.value.articles.getOrNull(articleIndex) ?: return
        viewModelScope.launch {
            mutableEffect.emit(NewsUiEffect.OpenUrl(article.articleUrl.value))
        }
    }

    private fun handleShareArticle(articleIndex: Int) {
        val article = mutableState.value.articles.getOrNull(articleIndex) ?: return
        viewModelScope.launch {
            mutableEffect.emit(
                NewsUiEffect.ShareContent(
                    title = article.title.value,
                    url = article.articleUrl.value
                )
            )
        }
    }

    private fun handleSaveArticle(articleIndex: Int) {
        val article = mutableState.value.articles.getOrNull(articleIndex) ?: return
        val currentSaved = mutableState.value.savedArticles.toMutableList()
        val savedIndex = currentSaved.indexOfFirst { it.articleUrl == article.articleUrl }
        val isAlreadySaved = savedIndex != -1
        if (isAlreadySaved) {
            currentSaved.removeAt(savedIndex)
            mutableState.update { state ->
                state.copy(savedArticles = currentSaved)
            }
            viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.ShowToast("Article removed"))
            }
        } else {
            currentSaved.add(0, article)
            mutableState.update { state ->
                state.copy(savedArticles = currentSaved)
            }
            viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.ShowToast("Article saved!"))
            }
        }
    }

    private fun handleRemoveSavedArticle(articleIndex: Int) {
        val currentSaved = mutableState.value.savedArticles.toMutableList()
        if (articleIndex in currentSaved.indices) {
            currentSaved.removeAt(articleIndex)
            mutableState.update { state ->
                state.copy(savedArticles = currentSaved)
            }
            viewModelScope.launch {
                mutableEffect.emit(NewsUiEffect.ShowToast("Article removed"))
            }
        }
    }

    private fun handleNavigateToSavedArticles() {
        mutableState.update { state ->
            state.copy(currentTab = NavigationTab.PROFILE)
        }
    }

    private fun handleNavigateToLanguageSettings() {
        mutableState.update { state ->
            state.copy(currentTab = NavigationTab.PROFILE)
        }
    }

    private fun handleRefreshNews() {
        mutableState.update { state -> state.copy(isRefreshing = true) }
        loadNews()
    }

    private fun handleRetryLoading() {
        mutableState.update { state ->
            state.copy(isLoading = true, errorMessage = null)
        }
        loadNews()
    }

    private fun handleDismissError() {
        mutableState.update { state -> state.copy(errorMessage = null) }
    }

    private fun handleNewsError(errorMessage: String) {
        mutableState.update { state ->
            state.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = errorMessage,
                isOfflineMode = true
            )
        }
    }

    private fun loadNews() {
        viewModelScope.launch {
            val currentState = mutableState.value
            val isCountriesTab: Boolean = currentState.currentTab == NavigationTab.COUNTRIES
            val request = GetTopHeadlinesRequest(
                category = currentState.selectedCategory,
                country = currentState.selectedCountry.code,
                countryName = currentState.selectedCountry.displayName,
                language = currentState.selectedLanguage.code,
                useCountry = isCountriesTab
            )
            when (val result = getTopHeadlinesUseCase.execute(request)) {
                is NewsResult.Success -> {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            articles = result.data,
                            errorMessage = null,
                            currentArticleIndex = 0,
                            isOfflineMode = false
                        )
                    }
                }
                is NewsResult.Error -> {
                    handleNewsError(result.error.message)
                }
            }
        }
    }
}
