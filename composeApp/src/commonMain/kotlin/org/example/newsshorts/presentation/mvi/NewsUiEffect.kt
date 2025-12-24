package org.example.newsshorts.presentation.mvi

sealed interface NewsUiEffect {
    data class OpenUrl(val url: String) : NewsUiEffect
    data class ShareContent(val title: String, val url: String) : NewsUiEffect
    data class ShowToast(val message: String) : NewsUiEffect
}

