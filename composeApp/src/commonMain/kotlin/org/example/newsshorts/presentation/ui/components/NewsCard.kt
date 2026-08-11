package org.example.newsshorts.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.example.newsshorts.domain.model.NewsArticle
import org.example.newsshorts.domain.model.PublishedTimestamp
import org.example.newsshorts.presentation.localization.appStrings
import org.example.newsshorts.presentation.localization.categoryName

private const val GRADIENT_START_ALPHA: Float = 0f
private const val GRADIENT_END_ALPHA: Float = 0.95f
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
        shape = RoundedCornerShape(0.dp),
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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = GRADIENT_START_ALPHA),
                        Color.Black.copy(alpha = 0.3f),
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = GRADIENT_END_ALPHA)
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$categoryEmoji $categoryName",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
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
        color = Color.White,
        maxLines = 4,
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
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 3,
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
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            // Isolated so a Latin source name keeps its own punctuation order
            // inside an RTL layout ("NYT U.S." rendering as ".NYT U.S").
            text = isolateBidi(sourceName),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "•",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatPublishedTime(publishedAt, appStrings().monthNames, appStrings().recently),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f)
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
        Button(
            onClick = onOpenArticle,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = appStrings().readFullArticle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Button(
            onClick = onSaveArticle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSaved) Color(0xFF4CAF50) else Color(0xFFE94560),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(50.dp)
                .width(70.dp)
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isSaved) "Unsave" else "Save",
                modifier = Modifier.size(22.dp)
            )
        }
        Button(
            onClick = onShareArticle,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.25f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(50.dp)
                .width(70.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
