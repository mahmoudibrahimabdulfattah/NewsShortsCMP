package com.mk.newsshorts.feature.feed

import com.mk.newsshorts.presentation.ui.components.AppButton
import com.mk.newsshorts.presentation.ui.components.feedSummaryMaxLines
import com.mk.newsshorts.presentation.ui.components.feedTitleMaxLines
import com.mk.newsshorts.presentation.ui.components.formatPublishedTime
import com.mk.newsshorts.presentation.ui.components.isolateBidi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.PublishedTimestamp
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.categoryName
import com.mk.newsshorts.presentation.ui.theme.LocalTextScale
import com.mk.newsshorts.presentation.ui.theme.OnImagery
import com.mk.newsshorts.presentation.ui.theme.PillShape

private const val ANIMATION_DURATION_MILLIS: Int = 600

@Composable
fun NewsCard(
    article: NewsArticle,
    isSaved: Boolean = false,
    onOpenArticle: () -> Unit,
    onShareArticle: () -> Unit,
    onSaveArticle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isVisible: Boolean by remember { mutableStateOf(false) }
    val animatedAlpha: Float by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CardAlpha"
    )
    val animatedTranslation: Float by animateFloatAsState(
        targetValue = if (isVisible) 0f else 50f,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CardTranslation"
    )
    LaunchedEffect(article.id) {
        isVisible = true
    }
    Card(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = animatedAlpha
                translationY = animatedTranslation
            },
        // Square on purpose: the card is the whole screen, so there are no
        // corners to round against anything.
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NewsCardBackground(imageUrl = article.imageUrl?.value)
            NewsCardGradientOverlay()
            NewsCardContent(
                article = article,
                isSaved = isSaved,
                onOpenArticle = onOpenArticle,
                onShareArticle = onShareArticle,
                onSaveArticle = onSaveArticle,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun NewsCardBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Article Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun NewsCardGradientOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = OnImagery.bottomScrim)
    )
}

@Composable
private fun NewsCardContent(
    article: NewsArticle,
    isSaved: Boolean,
    onOpenArticle: () -> Unit,
    onShareArticle: () -> Unit,
    onSaveArticle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 120.dp, top = 20.dp)
    ) {
        NewsCardCategoryBadge(
            categoryEmoji = article.category.emoji,
            categoryName = categoryName(article.category.apiValue, article.category.displayName)
        )
        Spacer(modifier = Modifier.height(16.dp))
        NewsCardTitle(title = article.title.value)
        Spacer(modifier = Modifier.height(12.dp))
        NewsCardDescription(description = article.description.value)
        Spacer(modifier = Modifier.height(16.dp))
        NewsCardMetadata(
            sourceName = article.source.name.value,
            publishedAt = article.publishedAt
        )
        Spacer(modifier = Modifier.height(20.dp))
        NewsCardActions(
            isSaved = isSaved,
            onOpenArticle = onOpenArticle,
            onShareArticle = onShareArticle,
            onSaveArticle = onSaveArticle
        )
    }
}

@Composable
private fun NewsCardCategoryBadge(
    categoryEmoji: String,
    categoryName: String,
    modifier: Modifier = Modifier
) {
    // Neutral on purpose. This badge names the article's category, and the chip
    // row at the top of the screen names the selected filter — which is almost
    // always the same word. Two identical labels a screen apart in two
    // different accent colours invited the reader to work out a difference that
    // is not there. Colour now means one thing in these rows: crimson is the
    // filter you picked, azure is something you can press.
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(OnImagery.fillStrong)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$categoryEmoji $categoryName",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = OnImagery.content
        )
    }
}

@Composable
private fun NewsCardTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = OnImagery.content,
        maxLines = feedTitleMaxLines(LocalTextScale.current),
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun NewsCardDescription(
    description: String,
    modifier: Modifier = Modifier
) {
    if (description.isNotBlank()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = OnImagery.contentMuted,
            maxLines = feedSummaryMaxLines(LocalTextScale.current),
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
private fun NewsCardMetadata(
    sourceName: String,
    publishedAt: PublishedTimestamp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            // Isolated so a Latin source name keeps its own punctuation order
            // inside an RTL layout ("NYT U.S." rendering as ".NYT U.S").
            text = isolateBidi(sourceName),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = OnImagery.content
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "•",
            style = MaterialTheme.typography.labelLarge,
            color = OnImagery.contentFaint
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatPublishedTime(publishedAt, appStrings().monthNames, appStrings().recently),
            style = MaterialTheme.typography.labelMedium,
            color = OnImagery.contentMuted
        )
    }
}

@Composable
private fun NewsCardActions(
    isSaved: Boolean,
    onOpenArticle: () -> Unit,
    onShareArticle: () -> Unit,
    onSaveArticle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val strings = appStrings()
        AppButton(
            text = strings.readFullArticle,
            onClick = onOpenArticle,
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        )
        // Only the icon changes colour, never the button under it — the same
        // way the details screen's bookmark behaves. Swapping the whole
        // container made the row jump every time the reader saved something,
        // and turned a small confirmation into the loudest thing on the card.
        IconAction(
            icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            contentDescription = if (isSaved) strings.unsave else strings.save,
            onClick = onSaveArticle,
            container = OnImagery.fillStrong,
            content = if (isSaved) OnImagery.savedTint else OnImagery.content,
        )
        IconAction(
            icon = Icons.Default.Share,
            contentDescription = strings.share,
            onClick = onShareArticle,
            container = OnImagery.fillStrong,
            content = OnImagery.content,
        )
    }
}

/** Square-ish icon-only companion to [AppButton], same height and corner. */
@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
        contentPadding = PaddingValues(0.dp),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .height(52.dp)
            .width(64.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}
