package com.mk.newsshorts.presentation.ui.screen

import com.mk.newsshorts.feature.saved.SavedArticlesUiEvent
import com.mk.newsshorts.presentation.viewmodel.AppShellUiEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.categoryName
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.presentation.ui.components.formatPublishedTime
import com.mk.newsshorts.presentation.ui.components.isolateBidi
import com.mk.newsshorts.presentation.ui.components.AppButton
import com.mk.newsshorts.presentation.ui.theme.PillShape

/**
 * A single article, full screen.
 *
 * Deliberately not a reader view: the app only ever holds the AI summary, never
 * the publisher's text. So this shows the whole summary — the feed card cuts it
 * to three lines — and sends the reader to the source for the rest.
 *
 * Back is handled once, centrally, in `NewsScreenContent` — every overlay
 * screen (this one, Settings, Saved) pops the same stack, so there is exactly
 * one place that decides what a back-press does.
 */
@Composable
fun ArticleDetailsScreen(
    article: NewsArticle,
    isSaved: Boolean,
    onShellEvent: (AppShellUiEvent) -> Unit,
    onSavedEvent: (SavedArticlesUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        DetailsTopBar(
            isSaved = isSaved,
            onBack = { onShellEvent(AppShellUiEvent.CloseOverlay) },
            onShare = { onShellEvent(AppShellUiEvent.ShareArticle(article)) },
            onSave = { onSavedEvent(SavedArticlesUiEvent.Toggle(article)) },
        )
        DetailsHeroImage(imageUrl = article.imageUrl?.value)

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            CategoryBadge(
                label = "${article.category.emoji} " +
                    categoryName(article.category.apiValue, article.category.displayName)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = article.title.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SourceRow(
                sourceName = article.source.name.value,
                publishedLabel = formatPublishedTime(
                    article.publishedAt,
                    strings.monthNames,
                    strings.recently,
                ),
            )
            Spacer(modifier = Modifier.height(20.dp))
            // The disclaimer belongs to the summary, so it goes when the summary
            // does — otherwise the screen explains a summary that isn't there.
            if (article.description.value.isNotBlank()) {
                Text(
                    text = article.description.value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.summaryDisclaimer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            AppButton(
                text = strings.readAtSource,
                onClick = { onShellEvent(AppShellUiEvent.OpenArticleSource) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(24.dp)
            )
        }
    }
}

@Composable
private fun DetailsTopBar(
    isSaved: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                // Auto-mirrored so the arrow points the right way in Arabic.
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = appStrings().back,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = appStrings().articleDetails,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = appStrings().share,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = onSave) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isSaved) appStrings().unsave else appStrings().save,
                // Crimson, matching the feed card's bookmark. It was azure,
                // which is the colour of everything tappable, so a saved
                // article looked no different from a link.
                tint = if (isSaved) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }
    }
}

@Composable
private fun DetailsHeroImage(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().height(260.dp)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Same placeholder the feed card uses, so a push-built article
            // without an image still looks deliberate.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun CategoryBadge(label: String, modifier: Modifier = Modifier) {
    // Neutral, and a pill, matching the badge on the feed card. It names the
    // article's category — it is a label, not something to press and not a
    // selection, so it takes neither accent.
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceRow(
    sourceName: String,
    publishedLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The dot that used to sit here was decoration wearing the urgency
        // colour, and the bullet two elements along already separates these.
        // The feed card's metadata row lost the same dot.
        Text(
            text = isolateBidi(sourceName),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "•",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = publishedLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}
