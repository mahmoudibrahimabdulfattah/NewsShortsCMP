package com.mk.newsshorts.feature.onboarding

import com.mk.newsshorts.core.model.settings.AppPreferences
import com.mk.newsshorts.core.data.local.OnboardingPersistence
import com.mk.newsshorts.core.domain.settings.SettingsPersistence
import com.mk.newsshorts.core.domain.feed.FeedInvalidator
import com.mk.newsshorts.core.domain.feed.InvalidationReason
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.onboarding.OnboardingStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The behaviour here is a rule about *when the app is allowed to ask for the
 * notification permission*, and getting it wrong spends the one request Android
 * ever grants. Each test is that rule, not the code that implements it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @Test
    fun `a reader who has been through onboarding never sees it again`() = runTest {
        val f = fixture(alreadyComplete = true)

        assertNull(f.viewModel.uiState.value.step)
        assertFalse(f.viewModel.uiState.value.isShowing)
    }

    @Test
    fun `a new reader starts on the language step`() = runTest {
        val f = fixture()

        assertEquals(OnboardingStep.LANGUAGE, f.viewModel.uiState.value.step)
    }

    @Test
    fun `categories are ticked and unticked but saved only at the end`() = runTest {
        val f = fixture()

        f.viewModel.processEvent(OnboardingUiEvent.ToggleCategory(NewsCategory.SPORTS))
        f.viewModel.processEvent(OnboardingUiEvent.ToggleCategory(NewsCategory.HEALTH))
        f.viewModel.processEvent(OnboardingUiEvent.ToggleCategory(NewsCategory.SPORTS))
        runCurrent()

        assertEquals(listOf(NewsCategory.HEALTH), f.viewModel.uiState.value.chosenCategories)
        assertNull(f.store.savedCategories, "categories were written before onboarding finished")
    }

    @Test
    fun `pressing through every step saves the choice and finishes`() = runTest {
        val f = fixture()

        f.viewModel.processEvent(OnboardingUiEvent.ToggleCategory(NewsCategory.SCIENCE))
        repeat(OnboardingStep.entries.size) {
            f.viewModel.processEvent(OnboardingUiEvent.Next)
        }
        runCurrent()

        assertNull(f.viewModel.uiState.value.step)
        assertEquals(listOf(NewsCategory.SCIENCE.apiValue), f.store.savedCategories)
        assertTrue(f.store.complete)
    }

    @Test
    fun `the feed is told the categories changed only after they are written`() = runTest {
        val f = fixture()
        f.viewModel.processEvent(OnboardingUiEvent.ToggleCategory(NewsCategory.BUSINESS))
        repeat(OnboardingStep.entries.size) {
            f.viewModel.processEvent(OnboardingUiEvent.Next)
        }
        runCurrent()

        assertEquals(listOf(InvalidationReason.OnboardingFinished), f.feedInvalidator.reasons)
        assertEquals(
            listOf(NewsCategory.BUSINESS.apiValue),
            f.feedInvalidator.categoriesWhenInvalidated,
            "the feed was told to reload before the choice was on disk",
        )
    }

    /**
     * Pressing through the last step is an answer either way, so the contextual
     * prompt must never fire afterwards — a reader who just said no would get
     * the system dialog anyway, and declining that locks the permission for
     * good.
     */
    @Test
    fun `answering the last step closes the question for the contextual prompt`() = runTest {
        val f = fixture(notificationsEnabled = false)

        repeat(OnboardingStep.entries.size) {
            f.viewModel.processEvent(OnboardingUiEvent.Next)
        }
        runCurrent()

        assertTrue(f.store.promptSeen, "a reader who answered would be asked again later")
    }

    @Test
    fun `skipping leaves the contextual prompt armed`() = runTest {
        val f = fixture()

        f.viewModel.processEvent(OnboardingUiEvent.Skip)
        runCurrent()

        assertFalse(f.store.promptSeen, "skipping is not an answer, so the prompt must survive")
        assertTrue(f.store.complete)
    }

    @Test
    fun `the system dialog is requested only for a yes`() = runTest {
        val f = fixture(notificationsEnabled = true)
        val effects = collectEffects(f.viewModel)

        repeat(OnboardingStep.entries.size) {
            f.viewModel.processEvent(OnboardingUiEvent.Next)
        }
        runCurrent()

        assertEquals(listOf<OnboardingUiEffect>(OnboardingUiEffect.RequestNotificationPermission), effects)
    }

    @Test
    fun `a no on the last step never reaches the system dialog`() = runTest {
        val f = fixture(notificationsEnabled = false)
        val effects = collectEffects(f.viewModel)

        repeat(OnboardingStep.entries.size) {
            f.viewModel.processEvent(OnboardingUiEvent.Next)
        }
        runCurrent()

        assertTrue(effects.isEmpty(), "asking after a no spends the one request Android grants")
    }

    @Test
    fun `skipping never reaches the system dialog either`() = runTest {
        val f = fixture(notificationsEnabled = true)
        val effects = collectEffects(f.viewModel)

        f.viewModel.processEvent(OnboardingUiEvent.Skip)
        runCurrent()

        assertTrue(effects.isEmpty())
    }

    private class Fixture(
        val viewModel: OnboardingViewModel,
        val store: RecordingOnboardingStore,
        val feedInvalidator: RecordingFeedInvalidator,
    )

    private fun TestScope.fixture(
        alreadyComplete: Boolean = false,
        notificationsEnabled: Boolean = true,
    ): Fixture {
        val store = RecordingOnboardingStore(alreadyComplete)
        val feedInvalidator = RecordingFeedInvalidator { store.savedCategories }
        return Fixture(
            viewModel = OnboardingViewModel(
                onboardingStore = store,
                settings = FixedSettings(notificationsEnabled),
                feedInvalidator = feedInvalidator,
                scopeOverride = backgroundScope,
            ),
            store = store,
            feedInvalidator = feedInvalidator,
        )
    }

    private class RecordingOnboardingStore(
        var complete: Boolean,
    ) : OnboardingPersistence {
        var savedCategories: List<String>? = null
        var promptSeen: Boolean = false

        override fun onboardingComplete(): Boolean = complete
        override suspend fun markOnboardingComplete() { complete = true }
        override fun notificationPromptSeen(): Boolean = promptSeen
        override suspend fun markNotificationPromptSeen() { promptSeen = true }
        override fun preferredCategories(): List<String> = savedCategories.orEmpty()
        override suspend fun savePreferredCategories(apiValues: List<String>) {
            savedCategories = apiValues
        }
    }

    /**
     * Records what the categories looked like at the instant the feed was told
     * they had changed. The feed re-reads them on that signal, so a signal sent
     * before the write would make it reload the choice the reader replaced.
     */
    private class RecordingFeedInvalidator(
        private val currentCategories: () -> List<String>?,
    ) : FeedInvalidator {
        private val mutableSignals = MutableSharedFlow<InvalidationReason>(extraBufferCapacity = 16)
        override val signals: SharedFlow<InvalidationReason> = mutableSignals.asSharedFlow()

        val reasons = mutableListOf<InvalidationReason>()
        var categoriesWhenInvalidated: List<String>? = null

        override fun invalidate(reason: InvalidationReason) {
            reasons += reason
            categoriesWhenInvalidated = currentCategories()
            mutableSignals.tryEmit(reason)
        }
    }

    private fun TestScope.collectEffects(
        viewModel: OnboardingViewModel,
    ): MutableList<OnboardingUiEffect> {
        val effects = mutableListOf<OnboardingUiEffect>()
        backgroundScope.launch { viewModel.uiEffect.collect { effects += it } }
        runCurrent()
        return effects
    }

    private class FixedSettings(notificationsEnabled: Boolean) : SettingsPersistence {
        private val state = MutableStateFlow(
            AppPreferences(notificationsEnabled = notificationsEnabled)
        )
        override val preferences: StateFlow<AppPreferences> = state.asStateFlow()
        override suspend fun saveAppLocale(localeCode: String) = Unit
        override suspend fun saveThemeMode(mode: String) = Unit
        override suspend fun saveTextScale(scale: String) = Unit
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setNotifyBreaking(enabled: Boolean) = Unit
        override suspend fun setNotifyTopStory(enabled: Boolean) = Unit
        override suspend fun setNotifyReminder(enabled: Boolean) = Unit
    }
}
