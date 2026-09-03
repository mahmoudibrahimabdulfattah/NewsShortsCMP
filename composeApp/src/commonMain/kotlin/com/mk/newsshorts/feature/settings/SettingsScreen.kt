package com.mk.newsshorts.feature.settings

import com.mk.newsshorts.presentation.viewmodel.AppShellUiEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.core.model.auth.AuthFailure
import com.mk.newsshorts.core.model.auth.AuthUser
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.localization.languageName
import com.mk.newsshorts.core.model.feed.LanguageOption
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.feature.feed.FeedUiState
import com.mk.newsshorts.core.model.settings.TextScale
import com.mk.newsshorts.core.model.settings.ThemeMode
import com.mk.newsshorts.presentation.ui.components.FilterPill
import com.mk.newsshorts.presentation.ui.components.SelectorRow
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar
import com.mk.newsshorts.presentation.ui.components.SectionHeader

private const val ANIMATION_DURATION_MILLIS: Int = 200

/**
 * Everything that used to be spread across Profile's one long list, now its
 * own screen: app language, news language, appearance, notifications.
 */
@Composable
fun SettingsScreen(
    newsUiState: FeedUiState,
    settingsUiState: SettingsUiState,
    authUser: AuthUser?,
    authInProgress: Boolean,
    authError: AuthFailure?,
    onFeedEvent: (FeedUiEvent) -> Unit,
    onShellEvent: (AppShellUiEvent) -> Unit,
    onSettingsEvent: (SettingsUiEvent) -> Unit,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismissAuthError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(title = strings.settings, onBack = { onShellEvent(AppShellUiEvent.CloseOverlay) })
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // The app draws behind the system navigation bar, so the last
                // row needs the inset added or it sits under it on
                // three-button navigation.
                contentPadding = WindowInsets.navigationBars
                    .add(WindowInsets(top = 16.dp, bottom = 16.dp))
                    .asPaddingValues()
            ) {
                item {
                    AppLanguageSection(
                        selectedLocale = settingsUiState.appLocale,
                        onLocaleSelected = { onSettingsEvent(SettingsUiEvent.SelectAppLocale(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    NewsLanguageSection(
                        selectedLanguage = newsUiState.selectedLanguage,
                        onLanguageSelected = { onFeedEvent(FeedUiEvent.SelectLanguage(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    ThemeSection(
                        selectedMode = settingsUiState.themeMode,
                        onModeSelected = { onSettingsEvent(SettingsUiEvent.SelectThemeMode(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    TextSizeSection(
                        selectedScale = settingsUiState.textScale,
                        onScaleSelected = { onSettingsEvent(SettingsUiEvent.SelectTextScale(it)) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    NotificationsSection(
                        uiState = settingsUiState,
                        onEvent = onSettingsEvent,
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    AccountSection(
                        authUser = authUser,
                        isLoading = authInProgress,
                        authError = authError,
                        onOpenSignIn = onOpenSignIn,
                        onSignOut = onSignOut,
                        onDeleteAccount = onDeleteAccount,
                        onDismissAuthError = onDismissAuthError,
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
        // The same pill as every other selector on this screen. This was the
        // last hand-rolled variant left — a pair of cards with their own fill,
        // their own check mark and their own idea of what selected looks like,
        // sitting directly above a row of pills that answer the same question.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLocale.entries.forEach { locale ->
                FilterPill(
                    label = languageName(locale.code, locale.displayName),
                    isSelected = selectedLocale == locale,
                    onClick = { onLocaleSelected(locale) },
                    leading = if (locale == AppLocale.ENGLISH) "🇺🇸" else "🇸🇦",
                    modifier = Modifier.weight(1f),
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
        SelectorRow(
            items = LanguageOption.entries,
            selected = selectedLanguage,
            key = { language -> language.code },
            onSelect = onLanguageSelected,
            leading = { language -> language.flag },
            label = { language -> languageName(language.code, language.nativeName) },
        )
    }
}


/**
 * Text size, previewed in the row that sets it.
 *
 * The sample line is rendered at the size each pill would give, so the choice
 * is visible before it is made — a reading app's most consequential setting
 * should not be four words the reader has to try one at a time.
 */
@Composable
private fun TextSizeSection(
    selectedScale: TextScale,
    onScaleSelected: (TextScale) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.FormatSize,
            title = strings.textSize,
            subtitle = strings.textSizeSubtitle
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextScale.entries.forEach { scale ->
                FilterPill(
                    label = textScaleLabel(scale, strings),
                    isSelected = selectedScale == scale,
                    onClick = { onScaleSelected(scale) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun textScaleLabel(scale: TextScale, strings: AppStrings): String = when (scale) {
    TextScale.SMALL -> strings.textSizeSmall
    TextScale.DEFAULT -> strings.textSizeDefault
    TextScale.LARGE -> strings.textSizeLarge
    TextScale.EXTRA_LARGE -> strings.textSizeExtraLarge
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                label = strings.themeSystem,
                isSelected = selectedMode == ThemeMode.SYSTEM,
                onClick = { onModeSelected(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            FilterPill(
                label = strings.themeLight,
                isSelected = selectedMode == ThemeMode.LIGHT,
                onClick = { onModeSelected(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            FilterPill(
                label = strings.themeDark,
                isSelected = selectedMode == ThemeMode.DARK,
                onClick = { onModeSelected(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}


@Composable
private fun NotificationsSection(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
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
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SettingsSwitchRow(
                    label = strings.notificationsMasterLabel,
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.ToggleNotifications) },
                )
                // Disabled rather than hidden: the per-tier choice a reader made
                // is still visible, and re-enabling the master switch restores
                // exactly what they had before instead of resetting it.
                SettingsSwitchRow(
                    label = strings.notifyBreakingLabel,
                    checked = uiState.notifyBreaking,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = {
                        onEvent(SettingsUiEvent.ToggleNotificationTier(NotificationTier.BREAKING))
                    },
                )
                SettingsSwitchRow(
                    label = strings.notifyTopStoryLabel,
                    checked = uiState.notifyTopStory,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = {
                        onEvent(SettingsUiEvent.ToggleNotificationTier(NotificationTier.TOP_STORY))
                    },
                )
                SettingsSwitchRow(
                    label = strings.notifyReminderLabel,
                    checked = uiState.notifyReminder,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = {
                        onEvent(SettingsUiEvent.ToggleNotificationTier(NotificationTier.REMINDER))
                    },
                )
            }
        }
    }
}

/**
 * Signed out: one row into sign-in, same shape as every other entry on this
 * screen. Signed in: sign out and delete, with delete needing an explicit
 * confirmation — it is the one action here that cannot be undone from inside
 * the app.
 */
@Composable
private fun AccountSection(
    authUser: AuthUser?,
    isLoading: Boolean,
    authError: AuthFailure?,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismissAuthError: () -> Unit,
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
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (authUser == null) {
                    AccountActionRow(
                        icon = Icons.Filled.Person,
                        label = strings.signIn,
                        onClick = onOpenSignIn,
                    )
                } else {
                    AccountActionRow(
                        icon = Icons.Filled.Logout,
                        label = strings.signOutLabel,
                        onClick = onSignOut,
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
        authError?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.authFailure(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissAuthError) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = strings.cancelLabel)
                }
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
                    onDeleteAccount()
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
