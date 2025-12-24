package org.example.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val ANIMATION_DURATION_MILLIS: Int = 300
private val INDICATOR_WIDTH_ACTIVE: Dp = 24.dp
private val INDICATOR_WIDTH_INACTIVE: Dp = 8.dp
private val INDICATOR_HEIGHT: Dp = 4.dp

@Composable
fun ArticleIndicator(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    if (totalCount <= 1) return
    val maxVisibleIndicators: Int = minOf(totalCount, 7)
    val startIndex: Int = calculateStartIndex(currentIndex, totalCount, maxVisibleIndicators)
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(maxVisibleIndicators) { index ->
            val actualIndex: Int = startIndex + index
            val isActive: Boolean = actualIndex == currentIndex
            IndicatorDot(isActive = isActive)
        }
    }
}

@Composable
private fun IndicatorDot(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val width: Dp by animateDpAsState(
        targetValue = if (isActive) INDICATOR_WIDTH_ACTIVE else INDICATOR_WIDTH_INACTIVE,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "IndicatorWidth"
    )
    val color: Color by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "IndicatorColor"
    )
    Box(
        modifier = modifier
            .width(width)
            .height(INDICATOR_HEIGHT)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

private fun calculateStartIndex(
    currentIndex: Int,
    totalCount: Int,
    maxVisible: Int
): Int {
    if (totalCount <= maxVisible) return 0
    val halfVisible: Int = maxVisible / 2
    return when {
        currentIndex < halfVisible -> 0
        currentIndex >= totalCount - halfVisible -> totalCount - maxVisible
        else -> currentIndex - halfVisible
    }
}

