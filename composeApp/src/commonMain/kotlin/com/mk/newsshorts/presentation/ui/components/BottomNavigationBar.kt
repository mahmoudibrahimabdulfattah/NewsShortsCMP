package com.mk.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.mvi.NavigationTab
import com.mk.newsshorts.presentation.ui.theme.NewsShortsTheme
import com.mk.newsshorts.presentation.ui.theme.OnImagery

private const val ANIMATION_DURATION_MILLIS: Int = 200

/**
 * How much room the bar takes above the system inset. Screens that scroll
 * underneath it use this to keep their last row reachable.
 */
val BottomNavBarHeight: Dp = 84.dp

/**
 * The bar sits over two very different things. On the feed it floats over
 * full-bleed photographs, where only a scrim and white icons survive; on
 * Profile it sits on a themed surface, where that same scrim reads as a black
 * smear and its white labels vanish into a light background.
 *
 * @param isOverImagery true when the surface behind the bar is a photograph
 *   rather than a themed background.
 */
@Composable
fun BottomNavigationBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    isOverImagery: Boolean,
    modifier: Modifier = Modifier
) {
    if (isOverImagery) {
        // Over photographs the bar is a dark surface whatever the setting says
        // — the same reason the feed forces its own theme. Without this the
        // accent comes from the light scheme, a muted slate that sinks into
        // the scrim, and the selected tab stops looking selected.
        NewsShortsTheme(isDarkTheme = true) {
            BottomNavigationBarContent(selectedTab, onTabSelected, true, modifier)
        }
    } else {
        BottomNavigationBarContent(selectedTab, onTabSelected, false, modifier)
    }
}

@Composable
private fun BottomNavigationBarContent(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    isOverImagery: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val palette: NavBarPalette = rememberNavBarPalette(isOverImagery)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(brush = palette.background)
            // After the background on purpose: the surface paints through the
            // inset area, and only the icons move up out of the system bar.
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        if (palette.edge != null) {
            // A themed surface has no gradient to separate it from whatever
            // scrolls behind it, so it gets a hairline instead.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.edge)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(top = 16.dp, bottom = 8.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.entries.forEach { tab ->
                BottomNavItem(
                    tab = tab,
                    title = getTabTitle(tab, strings),
                    isSelected = tab == selectedTab,
                    palette = palette,
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
    palette: NavBarPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor: Color by animateColorAsState(
        targetValue = if (isSelected) palette.selectedContent else palette.unselectedContent,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "NavContentColor"
    )
    val indicatorColor: Color by animateColorAsState(
        targetValue = if (isSelected) palette.indicator else Color.Transparent,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "NavIndicatorColor"
    )
    Column(
        modifier = modifier
            // Role.Tab, not a button: this announces the tab as one of three
            // and says which is selected, which a plain clickable does not.
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(INDICATOR_WIDTH)
                .height(INDICATOR_HEIGHT)
                .clip(RoundedCornerShape(percent = 50))
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getNavIcon(tab, isSelected),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

/** The M3 pill wraps the icon alone; the label sits below it. */
private val INDICATOR_WIDTH: Dp = 64.dp
private val INDICATOR_HEIGHT: Dp = 32.dp

private class NavBarPalette(
    val background: Brush,
    val edge: Color?,
    val selectedContent: Color,
    val unselectedContent: Color,
    val indicator: Color,
)

@Composable
private fun rememberNavBarPalette(isOverImagery: Boolean): NavBarPalette {
    val colorScheme = MaterialTheme.colorScheme
    return if (isOverImagery) {
        NavBarPalette(
            background = OnImagery.navScrim,
            edge = null,
            selectedContent = colorScheme.primary,
            unselectedContent = OnImagery.contentFaint,
            indicator = colorScheme.primary.copy(alpha = 0.15f),
        )
    } else {
        NavBarPalette(
            background = SolidColor(colorScheme.surface),
            edge = colorScheme.outlineVariant,
            selectedContent = colorScheme.onSecondaryContainer,
            unselectedContent = colorScheme.onSurfaceVariant,
            indicator = colorScheme.secondaryContainer,
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
