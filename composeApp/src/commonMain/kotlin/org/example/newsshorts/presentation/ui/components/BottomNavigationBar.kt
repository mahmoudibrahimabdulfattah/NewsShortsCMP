package org.example.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.newsshorts.presentation.localization.AppStrings
import org.example.newsshorts.presentation.localization.appStrings
import org.example.newsshorts.presentation.mvi.NavigationTab

private const val ANIMATION_DURATION_MILLIS: Int = 200

@Composable
fun BottomNavigationBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.95f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.entries.forEach { tab ->
                BottomNavItem(
                    tab = tab,
                    title = getTabTitle(tab, strings),
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: NavigationTab,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "NavIconColor"
    )
    val textColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "NavTextColor"
    )
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "NavBackgroundColor"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = getNavIcon(tab, isSelected),
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

private fun getNavIcon(tab: NavigationTab, isSelected: Boolean): ImageVector {
    return when (tab) {
        NavigationTab.FOR_YOU -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
        NavigationTab.COUNTRIES -> if (isSelected) Icons.Filled.Public else Icons.Outlined.Public
        NavigationTab.PROFILE -> if (isSelected) Icons.Filled.Person else Icons.Outlined.Person
    }
}

private fun getTabTitle(tab: NavigationTab, strings: AppStrings): String {
    return when (tab) {
        NavigationTab.FOR_YOU -> strings.forYou
        NavigationTab.COUNTRIES -> strings.countries
        NavigationTab.PROFILE -> strings.profile
    }
}
