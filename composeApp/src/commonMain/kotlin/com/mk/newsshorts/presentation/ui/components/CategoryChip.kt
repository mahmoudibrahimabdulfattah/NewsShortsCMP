package com.mk.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.presentation.localization.categoryName

private const val ANIMATION_DURATION_MILLIS: Int = 300
private val UNSELECTED_BACKGROUND_COLOR: Color = Color.White.copy(alpha = 0.15f)
private val UNSELECTED_BORDER_COLOR: Color = Color.White.copy(alpha = 0.3f)

@Composable
fun CategoryChip(
    category: NewsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            UNSELECTED_BACKGROUND_COLOR
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CategoryBackgroundColor"
    )
    val contentColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            Color.White
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CategoryContentColor"
    )
    val borderColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            Color.Transparent
        } else {
            UNSELECTED_BORDER_COLOR
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CategoryBorderColor"
    )
    val chipShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(chipShape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${category.emoji} ${categoryName(category.apiValue, category.displayName)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}
