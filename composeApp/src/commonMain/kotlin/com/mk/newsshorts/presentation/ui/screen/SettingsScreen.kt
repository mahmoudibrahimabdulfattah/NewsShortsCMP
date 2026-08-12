package com.mk.newsshorts.presentation.ui.screen

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.auth.AuthUser
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.languageName
import com.mk.newsshorts.presentation.mvi.LanguageOption
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.mvi.NewsUiState
import com.mk.newsshorts.presentation.mvi.NotificationTier
import com.mk.newsshorts.presentation.mvi.Overlay
import com.mk.newsshorts.presentation.mvi.ThemeMode
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar
import com.mk.newsshorts.presentation.ui.components.SectionHeader

private const val ANIMATION_DURATION_MILLIS: Int = 200

/**
 * Everything that used to be spread across Profile's one long list, now its
 * own screen: app language, news language, appearance, notifications.
 */
@Composable
fun SettingsScreen(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(title = strings.settings, onBack = { onEvent(NewsUiEvent.CloseOverlay) })
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    AppLanguageSection(
                        selectedLocale = uiState.appLocale,
                        onLocaleSelected = { onEvent(NewsUiEvent.SelectAppLocale(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    NewsLanguageSection(
                        selectedLanguage = uiState.selectedLanguage,
                        onLanguageSelected = { onEvent(NewsUiEvent.SelectLanguage(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    ThemeSection(
                        selectedMode = uiState.themeMode,
                        onModeSelected = { onEvent(NewsUiEvent.SelectThemeMode(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    NotificationsSection(
                        uiState = uiState,
                        onEvent = onEvent,
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    AccountSection(
                        authUser = uiState.authUser,
                        isLoading = uiState.authInProgress,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLanguageSection(
    selectedLocale: AppLocale,
    onLocaleSelected: (AppLocale) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
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
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "AppLocaleChipBackground"
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
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = locale.nativeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(20.dp).width(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NewsLanguageSection(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
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
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "LanguageChipBackground"
    )
    val borderColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "LanguageChipBorder"
    )
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = language.flag, style = MaterialTheme.typography.titleMedium)
            Column {
                Text(
                    text = languageName(language.code, language.displayName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.height(16.dp).width(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeSection(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.DarkMode,
            title = strings.themeSectionTitle,
            subtitle = strings.themeSectionDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeModeChip(
                label = strings.themeSystem,
                isSelected = selectedMode == ThemeMode.SYSTEM,
                onClick = { onModeSelected(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeChip(
                label = strings.themeLight,
                isSelected = selectedMode == ThemeMode.LIGHT,
                onClick = { onModeSelected(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeChip(
                label = strings.themeDark,
                isSelected = selectedMode == ThemeMode.DARK,
                onClick = { onModeSelected(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MILLIS),
        label = "ThemeModeChipBackground"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun NotificationsSection(
    uiState: NewsUiState,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Notifications,
            title = strings.notificationsSectionTitle,
            subtitle = strings.notificationsSectionDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SettingsSwitchRow(
                    label = strings.notificationsMasterLabel,
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { onEvent(NewsUiEvent.ToggleNotificationsEnabled) },
                )
                // Disabled rather than hidden: the per-tier choice a reader made
                // is still visible, and re-enabling the master switch restores
                // exactly what they had before instead of resetting it.
                SettingsSwitchRow(
                    label = strings.notifyBreakingLabel,
                    checked = uiState.notifyBreaking,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = { onEvent(NewsUiEvent.ToggleNotificationTier(NotificationTier.BREAKING)) },
                )
                SettingsSwitchRow(
                    label = strings.notifyTopStoryLabel,
                    checked = uiState.notifyTopStory,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = { onEvent(NewsUiEvent.ToggleNotificationTier(NotificationTier.TOP_STORY)) },
                )
                SettingsSwitchRow(
                    label = strings.notifyReminderLabel,
                    checked = uiState.notifyReminder,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = { onEvent(NewsUiEvent.ToggleNotificationTier(NotificationTier.REMINDER)) },
                )
            }
        }
    }
}

/**
 * Signed out: one row into [Overlay.SignIn], same shape as every other entry
 * on this screen. Signed in: sign out and delete, with delete needing an
 * explicit confirmation — it is the one action here that cannot be undone
 * from inside the app.
 */
@Composable
private fun AccountSection(
    authUser: AuthUser?,
    isLoading: Boolean,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    var showDeleteConfirmation: Boolean by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Person,
            title = strings.accountSectionTitle,
            subtitle = authUser?.email ?: strings.accountSectionDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (authUser == null) {
                    AccountActionRow(
                        icon = Icons.Filled.Person,
                        label = strings.signIn,
                        onClick = { onEvent(NewsUiEvent.OpenOverlay(Overlay.SignIn)) },
                    )
                } else {
                    AccountActionRow(
                        icon = Icons.Filled.Logout,
                        label = strings.signOutLabel,
                        onClick = { onEvent(NewsUiEvent.SignOut) },
                    )
                    AccountActionRow(
                        icon = Icons.Filled.DeleteForever,
                        label = strings.deleteAccountLabel,
                        onClick = { showDeleteConfirmation = true },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = strings.deleteAccountConfirmTitle) },
            text = { Text(text = strings.deleteAccountConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onEvent(NewsUiEvent.DeleteAccount)
                }) {
                    Text(text = strings.deleteAccountConfirmButton, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = strings.cancelLabel)
                }
            },
        )
    }
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.height(20.dp).width(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.4f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
