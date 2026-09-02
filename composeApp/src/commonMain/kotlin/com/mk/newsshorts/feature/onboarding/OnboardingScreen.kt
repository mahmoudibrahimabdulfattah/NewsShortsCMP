package com.mk.newsshorts.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.domain.model.NewsCategory
import com.mk.newsshorts.presentation.localization.AppLocale
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.feature.settings.SettingsUiEvent
import com.mk.newsshorts.feature.settings.SettingsUiState
import com.mk.newsshorts.presentation.localization.categoryName
import com.mk.newsshorts.presentation.mvi.OnboardingStep
import com.mk.newsshorts.presentation.ui.components.AppButton
import com.mk.newsshorts.presentation.ui.components.AppButtonTone
import com.mk.newsshorts.presentation.ui.components.FilterPill

/**
 * The three questions worth asking before the first headline.
 *
 * Every step applies its answer the moment it is tapped rather than on the way
 * out — the language switch rewrites this screen under the reader's hand, which
 * is the clearest possible confirmation that it worked, and a ticked category
 * is a tick, not a form field.
 *
 * Skippable throughout. What a reader who skips gets is not a broken app but
 * the defaults: their device language, every category in declared order, and
 * the notification permission question left for later — see
 * `FeedViewModel.handleOnboardingSkip`.
 */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    settingsUiState: SettingsUiState,
    onEvent: (OnboardingUiEvent) -> Unit,
    onSettingsEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val step: OnboardingStep = uiState.step ?: return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            StepDots(current = step, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "OnboardingStep",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (current) {
                        OnboardingStep.LANGUAGE -> LanguageStep(settingsUiState, onSettingsEvent)
                        OnboardingStep.CATEGORIES -> CategoriesStep(uiState, onEvent)
                        OnboardingStep.NOTIFICATIONS -> {
                            NotificationsStep(settingsUiState, onSettingsEvent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AppButton(
                text = if (step.next == null) strings.onboardingStart else strings.onboardingContinue,
                onClick = { onEvent(OnboardingUiEvent.Next) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            AppButton(
                text = strings.onboardingSkip,
                onClick = { onEvent(OnboardingUiEvent.Skip) },
                tone = AppButtonTone.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepHeading(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun LanguageStep(settingsUiState: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    val strings = appStrings()
    StepHeading(strings.onboardingLanguageTitle, strings.onboardingLanguageSubtitle)
    // Applied on tap, not on continue: the whole screen flips to the chosen
    // language and direction, which says more than any confirmation could.
    AppLocale.entries.forEach { locale ->
        FilterPill(
            label = locale.nativeName,
            isSelected = locale == settingsUiState.appLocale,
            onClick = { onEvent(SettingsUiEvent.SelectAppLocale(locale)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun CategoriesStep(uiState: OnboardingUiState, onEvent: (OnboardingUiEvent) -> Unit) {
    val strings = appStrings()
    StepHeading(strings.onboardingCategoriesTitle, strings.onboardingCategoriesSubtitle)
    // Two per row rather than a wrapping flow: the labels are one word in both
    // languages, and a fixed grid keeps the Arabic and English versions of this
    // screen the same shape.
    NewsCategory.entries.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { category ->
                FilterPill(
                    label = categoryName(category.apiValue, category.displayName),
                    leading = category.emoji,
                    isSelected = category in uiState.chosenCategories,
                    onClick = { onEvent(OnboardingUiEvent.ToggleCategory(category)) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Keeps the last odd pill the same width as the rest.
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NotificationsStep(settingsUiState: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    val strings = appStrings()
    StepHeading(strings.onboardingNotificationsTitle, strings.onboardingNotificationsSubtitle)
    // The in-app preference, not the OS permission. The permission itself is
    // requested once this flow finishes, and only if this is on — asking the
    // system before the reader has said they want it is what this replaced.
    FilterPill(
        label = strings.onboardingNotificationsOn,
        isSelected = settingsUiState.notificationsEnabled,
        onClick = {
            if (!settingsUiState.notificationsEnabled) onEvent(SettingsUiEvent.ToggleNotifications)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
    FilterPill(
        label = strings.onboardingNotificationsOff,
        isSelected = !settingsUiState.notificationsEnabled,
        onClick = {
            if (settingsUiState.notificationsEnabled) onEvent(SettingsUiEvent.ToggleNotifications)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun StepDots(current: OnboardingStep, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingStep.entries.forEach { step ->
            val isCurrent: Boolean = step == current
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}
