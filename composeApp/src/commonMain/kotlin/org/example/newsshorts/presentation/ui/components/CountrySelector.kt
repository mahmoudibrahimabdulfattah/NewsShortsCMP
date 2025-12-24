package org.example.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.newsshorts.presentation.mvi.CountryOption

private const val ANIMATION_DURATION_MILLIS: Int = 200

@Composable
fun CountrySelector(
    selectedCountry: CountryOption,
    onCountrySelected: (CountryOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = CountryOption.entries.toList(),
            key = { country -> country.code }
        ) { country ->
            CountryChip(
                country = country,
                isSelected = country == selectedCountry,
                onClick = { onCountrySelected(country) }
            )
        }
    }
}

@Composable
private fun CountryChip(
    country: CountryOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.1f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CountryBackgroundColor"
    )
    val borderColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CountryBorderColor"
    )
    val textColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            Color.White.copy(alpha = 0.8f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "CountryTextColor"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = country.flag,
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = country.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

