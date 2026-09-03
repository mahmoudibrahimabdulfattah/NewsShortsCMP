package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.core.model.article.ArticleOpenOrigin
import com.mk.newsshorts.core.model.NewsArticle

sealed interface Overlay {
    data class Details(val article: NewsArticle, val origin: ArticleOpenOrigin) : Overlay
    data object Settings : Overlay
    data object SavedArticles : Overlay
    data object SignIn : Overlay
    data object Search : Overlay
    data object NotificationInbox : Overlay
    /** Third-party notices. The bundled fonts are under the OFL, which
     *  requires its notice to travel with them. */
    data object Licenses : Overlay
}

enum class NavigationTab(
    val title: String,
    val icon: String
) {
    FOR_YOU(title = "For You", icon = "🔥"),
    COUNTRIES(title = "Countries", icon = "🌍"),
    PROFILE(title = "Profile", icon = "⚙️")
}
