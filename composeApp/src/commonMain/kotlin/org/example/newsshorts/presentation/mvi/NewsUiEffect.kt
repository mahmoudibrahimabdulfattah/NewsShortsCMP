package org.example.newsshorts.presentation.mvi

sealed interface NewsUiEffect {
    data class OpenUrl(val url: String) : NewsUiEffect

    /**
     * [chooserTitle] is passed down rather than read from a platform resource:
     * the app's language is chosen in its own settings and can differ from the
     * system locale, so Android string resources would pick the wrong one.
     */
    data class ShareContent(
        val title: String,
        val url: String,
        val chooserTitle: String
    ) : NewsUiEffect

    data class ShowToast(val message: String) : NewsUiEffect
}
