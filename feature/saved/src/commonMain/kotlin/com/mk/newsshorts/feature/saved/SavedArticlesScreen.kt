package com.mk.newsshorts.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.ui.components.EmptySavedArticlesCard
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar
import com.mk.newsshorts.presentation.ui.components.SavedArticleCard

/**
 * Every bookmark, not just the two-item preview Profile shows.
 *
 * A real `LazyColumn` rather than Profile's plain `Column` — up to
 * `MAX_SAVED` (200) articles composing at once inside one `item {}` was the
 * cost of that preview approach, and this screen is exactly where it stops
 * being acceptable.
 */
@Composable
fun SavedArticlesScreen(
    uiState: SavedArticlesUiState,
    onBack: () -> Unit,
    onOpenArticle: (NewsArticle) -> Unit,
    onSavedEvent: (SavedArticlesUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(title = strings.savedArticles, onBack = onBack)
            if (!uiState.hasArticles) {
                EmptySavedArticlesCard(
                    strings = strings,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // The app draws behind the system navigation bar, so the
                    // last card needs the inset added or it sits under it on
                    // three-button navigation.
                    contentPadding = WindowInsets.navigationBars
                        .add(WindowInsets(left = 16.dp, top = 12.dp, right = 16.dp, bottom = 12.dp))
                        .asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.articles, key = { it.articleUrl.value }) { article ->
                        SavedArticleCard(
                            article = article,
                            onClick = { onOpenArticle(article) },
                            onRemove = { onSavedEvent(SavedArticlesUiEvent.Remove(article)) }
                        )
                    }
                }
            }
        }
    }
}
