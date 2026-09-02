package com.mk.newsshorts.feature.onboarding

import com.mk.newsshorts.core.data.local.OnboardingPersistence
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import com.mk.newsshorts.core.domain.feed.FeedInvalidator
import com.mk.newsshorts.core.domain.feed.InvalidationReason
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.presentation.mvi.OnboardingStep
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    /**
     * Which step is showing, or null once it is done or was never needed. Null
     * rather than a boolean plus an index, so "not onboarding" is one state
     * instead of two that can disagree.
     */
    val step: OnboardingStep? = null,
    /** Ticked as the reader goes; written to settings only when it finishes. */
    val chosenCategories: List<NewsCategory> = emptyList(),
) {
    val isShowing: Boolean get() = step != null
}

sealed interface OnboardingUiEvent {
    data object Next : OnboardingUiEvent
    data object Skip : OnboardingUiEvent
    data class ToggleCategory(val category: NewsCategory) : OnboardingUiEvent
}

sealed interface OnboardingUiEffect {
    data object RequestNotificationPermission : OnboardingUiEffect
}

class OnboardingViewModel(
    private val onboardingStore: OnboardingPersistence,
    private val settings: SettingsPersistence,
    private val feedInvalidator: FeedInvalidator,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow(
        OnboardingUiState(
            step = if (onboardingStore.onboardingComplete()) null else OnboardingStep.LANGUAGE,
        )
    )
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<OnboardingUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<OnboardingUiEffect> = effectChannel.receiveAsFlow()

    private val onboardingScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    fun processEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.Next -> next()
            OnboardingUiEvent.Skip -> finish(answeredNotifications = false)
            is OnboardingUiEvent.ToggleCategory -> toggleCategory(event.category)
        }
    }

    /**
     * Advances onboarding, or finishes it on the last step.
     *
     * Finishing writes what was chosen; there is no separate confirm, because
     * every step already applied its own answer the moment it was tapped —
     * language rewrites the screen under the reader's hand, and a category is
     * ticked, not submitted.
     */
    private fun next() {
        val current: OnboardingStep = mutableState.value.step ?: return
        val following: OnboardingStep? = current.next
        if (following != null) {
            mutableState.update { it.copy(step = following) }
            return
        }
        finish(answeredNotifications = true)
    }

    private fun toggleCategory(category: NewsCategory) {
        mutableState.update { state ->
            val chosen = state.chosenCategories
            state.copy(
                chosenCategories = if (category in chosen) chosen - category else chosen + category
            )
        }
    }

    /**
     * [answeredNotifications] is whether the reader reached the last step and
     * pressed through it — which is an answer either way, including "no".
     * Skipping does not, which is why [OnboardingUiEvent.Skip] passes false.
     *
     * Only an answer closes the question. A "no" here used to leave the
     * contextual prompt armed, so the reader who had just declined got the
     * system dialog anyway a few articles later; declining that is what
     * permanently blocks the app from ever asking again, so the reask was not
     * merely rude, it spent the one thing it was trying to win. Skipping leaves
     * it armed on purpose: a reader who skipped past the question has not
     * answered it.
     */
    private fun finish(answeredNotifications: Boolean) {
        val preferred: List<String> = mutableState.value.chosenCategories.map { it.apiValue }
        mutableState.update { it.copy(step = null) }
        onboardingScope.launch {
            onboardingStore.savePreferredCategories(preferred)
            onboardingStore.markOnboardingComplete()
            if (answeredNotifications) {
                // Marked seen on either answer, so the after-a-few-articles
                // prompt never asks a question the reader has already answered.
                onboardingStore.markNotificationPromptSeen()
                // The system dialog only for a yes. Showing it to someone who
                // just said no would collect the denial that locks the
                // permission for good.
                if (settings.preferences.value.notificationsEnabled) {
                    effectChannel.send(OnboardingUiEffect.RequestNotificationPermission)
                }
            }
            // The categories are written before the feed is told, so the feed
            // reads the reader's choice and not the one it opened on. It
            // derives its own order from the store rather than being handed
            // one, which is why nothing about the feed appears here.
            feedInvalidator.invalidate(InvalidationReason.OnboardingFinished)
        }
    }
}
