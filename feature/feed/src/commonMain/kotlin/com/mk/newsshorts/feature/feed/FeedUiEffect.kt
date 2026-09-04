package com.mk.newsshorts.feature.feed

sealed interface FeedUiEffect {
    data class OpenUrl(val url: String) : FeedUiEffect

    /**
     * [chooserTitle] is passed down rather than read from a platform resource:
     * the app's language is chosen in its own settings and can differ from the
     * system locale, so Android string resources would pick the wrong one.
     */
    data class ShareContent(
        val title: String,
        val url: String,
        val chooserTitle: String
    ) : FeedUiEffect

    data class ShowToast(val message: String) : FeedUiEffect

    /**
     * Asks the platform layer to show the OS permission dialog. Kept as an
     * effect rather than a direct call so the ViewModel decides *when* to ask
     * without knowing *how* — only Android has this permission at all.
     */
    data object RequestNotificationPermission : FeedUiEffect
}
