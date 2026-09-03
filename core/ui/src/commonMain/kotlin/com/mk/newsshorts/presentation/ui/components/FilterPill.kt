package com.mk.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.presentation.ui.theme.LocalIsDarkTheme
import com.mk.newsshorts.presentation.ui.theme.OnImagery
import com.mk.newsshorts.presentation.ui.theme.PillShape

private const val ANIMATION_DURATION_MILLIS: Int = 200
private const val EDGE_FADE_FRACTION: Float = 0.06f

/**
 * The one chip in the app.
 *
 * There used to be four: categories were a 24dp box, countries a 16dp column
 * with a 1.5dp border, languages a 16dp box with a 1dp border, theme modes a
 * 14dp box with none — four answers to a question that only has one. They also
 * disagreed on animation duration and on how a selected item should look.
 *
 * @param leading an emoji shown before the label — a category icon or a flag.
 * @param onImagery true when the pill sits over a photograph rather than a
 *   themed surface, which changes where its colours come from.
 */
@Composable
fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    onImagery: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    // The selected chip takes whichever brand colour has the most presence on
    // the surface it is sitting on, because its whole job is to be the most
    // salient thing in the row.
    //
    // Over photographs and on dark, that is crimson: the dark azure is a pale
    // tint that sinks into blue-grey chrome, so selection stopped announcing
    // itself. On light, azure is already the darkest, most saturated thing on a
    // near-white screen and wins outright — and crimson there costs real money,
    // because a saturated red block on white reads as a warning, and the same
    // screen carries a red delete affordance a few rows down.
    val isDark: Boolean = LocalIsDarkTheme.current
    val targetBackground: Color = when {
        isSelected && onImagery -> OnImagery.selectedFill
        isSelected && isDark -> colorScheme.tertiary
        isSelected -> colorScheme.primary
        onImagery -> OnImagery.fill
        else -> colorScheme.surfaceVariant
    }
    val targetContent: Color = when {
        isSelected && onImagery -> OnImagery.onSelectedFill
        isSelected && isDark -> colorScheme.onTertiary
        isSelected -> colorScheme.onPrimary
        onImagery -> OnImagery.content
        else -> colorScheme.onSurfaceVariant
    }
    val targetBorder: Color = when {
        isSelected -> Color.Transparent
        onImagery -> OnImagery.border
        else -> colorScheme.outlineVariant
    }

    val backgroundColor: Color by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "PillBackground"
    )
    val contentColor: Color by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "PillContent"
    )
    val borderColor: Color by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "PillBorder"
    )

    Box(
        modifier = modifier
            .clip(PillShape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = PillShape)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (leading != null) "$leading $label" else label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * A horizontal row of [FilterPill]s.
 *
 * Carries three things every hand-rolled row in the app was missing. It scrolls
 * the selection into view — with seven categories and thirteen countries the
 * current choice was regularly off screen, and re-entering a tab always started
 * back at the first item. It fades its own content at whichever edge still has
 * more to show, so a half-visible chip reads as "keep scrolling" rather than as
 * something clipped by mistake. And it fixes the spacing, which was 8dp for
 * categories, 12dp for countries and 10dp for languages.
 *
 * The fade is a mask over the content rather than a gradient painted on top,
 * because over the feed there is no single colour to fade into — the background
 * there is a photograph.
 */
@Composable
fun <T> SelectorRow(
    items: List<T>,
    selected: T,
    key: (T) -> Any,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    onImagery: Boolean = false,
    leading: (T) -> String? = { null },
    label: @Composable (T) -> String,
) {
    val listState = rememberLazyListState()
    val selectedIndex: Int = items.indexOf(selected)

    // Instant the first time so the row simply opens on the right item, animated
    // afterwards so a tap reads as movement the reader caused.
    var hasSettled: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        if (hasSettled) {
            listState.animateScrollToItem(selectedIndex)
        } else {
            listState.scrollToItem(selectedIndex)
            hasSettled = true
        }
    }

    val startFade: Float by animateFloatAsState(
        targetValue = if (listState.canScrollBackward) 1f else 0f,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "StartFade"
    )
    val endFade: Float by animateFloatAsState(
        targetValue = if (listState.canScrollForward) 1f else 0f,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "EndFade"
    )

    LazyRow(
        state = listState,
        modifier = modifier
            .selectableGroup()
            // Offscreen compositing is what makes DstIn mask the row's own
            // pixels instead of everything already painted beneath it.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 1f - startFade),
                        EDGE_FADE_FRACTION to Color.White,
                        1f - EDGE_FADE_FRACTION to Color.White,
                        1f to Color.White.copy(alpha = 1f - endFade),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items = items, key = { item -> key(item) }) { item ->
            FilterPill(
                label = label(item),
                isSelected = item == selected,
                onClick = { onSelect(item) },
                leading = leading(item),
                onImagery = onImagery,
            )
        }
    }
}
