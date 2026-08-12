package com.mk.newsshorts.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.domain.model.NewsArticle
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.languageName
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NewsUiState

private const val ANIMATION_DURATION_MILLIS: Int = 200

@Composable
fun ProfileScreen(
    uiState: NewsUiState,
    onLanguageSelected: (LanguageOption) -> Unit,
    onAppLocaleSelected: (AppLocale) -> Unit,
    onSavedArticleClick: (NewsArticle) -> Unit,
    onRemoveSavedArticle: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F0F23)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = 100.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                ProfileHeader(strings = strings)
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AppLanguageSection(
                    selectedLocale = uiState.appLocale,
                    onLocaleSelected = onAppLocaleSelected,
                    strings = strings
                )
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                NewsLanguageSettingsSection(
                    selectedLanguage = uiState.selectedLanguage,
                    onLanguageSelected = onLanguageSelected,
                    strings = strings
                )
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SavedArticlesSection(
                    savedArticles = uiState.savedArticles,
                    onArticleClick = onSavedArticleClick,
                    onRemoveArticle = onRemoveSavedArticle,
                    strings = strings
                )
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AppInfoSection(strings = strings)
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    strings: com.mk.newsshorts.presentation.localization.AppStrings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE94560),
                            Color(0xFF0F3460)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = strings.newsReader,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.personalizeExperience,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun AppLanguageSection(
    selectedLocale: AppLocale,
    onLocaleSelected: (AppLocale) -> Unit,
    strings: com.mk.newsshorts.presentation.localization.AppStrings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Translate,
            title = strings.appLanguage,
            subtitle = strings.appLanguageDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppLocaleChip(
                locale = AppLocale.ENGLISH,
                isSelected = selectedLocale == AppLocale.ENGLISH,
                onClick = { onLocaleSelected(AppLocale.ENGLISH) },
                modifier = Modifier.weight(1f)
            )
            AppLocaleChip(
                locale = AppLocale.ARABIC,
                isSelected = selectedLocale == AppLocale.ARABIC,
                onClick = { onLocaleSelected(AppLocale.ARABIC) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AppLocaleChip(
    locale: AppLocale,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0F3460) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "AppLocaleChipBackground"
    )
    val borderColor: Color by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0F3460) else Color.White.copy(alpha = 0.2f),
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "AppLocaleChipBorder"
    )
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (locale == AppLocale.ENGLISH) "🇺🇸" else "🇸🇦",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = languageName(locale.code, locale.displayName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = locale.nativeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFFE94560),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NewsLanguageSettingsSection(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit,
    strings: com.mk.newsshorts.presentation.localization.AppStrings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Language,
            title = strings.newsLanguage,
            subtitle = strings.newsLanguageDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(LanguageOption.entries) { language ->
                LanguageChip(
                    language = language,
                    isSelected = language == selectedLanguage,
                    onClick = { onLanguageSelected(language) }
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(
    language: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFE94560) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "LanguageChipBackground"
    )
    val borderColor: Color by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFE94560) else Color.White.copy(alpha = 0.2f),
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "LanguageChipBorder"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = language.flag,
                style = MaterialTheme.typography.titleMedium
            )
            Column {
                Text(
                    text = languageName(language.code, language.displayName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedArticlesSection(
    savedArticles: List<NewsArticle>,
    onArticleClick: (NewsArticle) -> Unit,
    onRemoveArticle: (NewsArticle) -> Unit,
    strings: com.mk.newsshorts.presentation.localization.AppStrings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Bookmark,
            title = strings.savedArticles,
            subtitle = if (savedArticles.isEmpty()) {
                strings.noSavedArticles
            } else {
                "${savedArticles.size} article${if (savedArticles.size > 1) "s" else ""}"
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (savedArticles.isEmpty()) {
            EmptySavedArticlesCard(strings = strings)
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                savedArticles.forEach { article ->
                    SavedArticleCard(
                        article = article,
                        onClick = { onArticleClick(article) },
                        onRemove = { onRemoveArticle(article) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySavedArticlesCard(
    strings: com.mk.newsshorts.presentation.localization.AppStrings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.noSavedArticles,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.savedArticlesDescription,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SavedArticleCard(
    article: NewsArticle,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = article.title.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.source.name.value,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE94560)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE94560).copy(alpha = 0.2f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = Color(0xFFE94560),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AppInfoSection(
    strings: com.mk.newsshorts.presentation.localization.AppStrings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Info,
            title = strings.about,
            subtitle = strings.aboutDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.05f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                InfoRow(label = strings.appName, value = "News Shorts")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = strings.appVersion, value = BuildConfig.VERSION_NAME)
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = strings.platform, value = "Compose Multiplatform")
                Spacer(modifier = Modifier.height(12.dp))
                // NewsAPI was replaced by the RSS + Gemini backend; the credit
                // had been telling readers the wrong thing since then.
                InfoRow(label = strings.poweredBy, value = "RSS + Gemini")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE94560).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFE94560),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
