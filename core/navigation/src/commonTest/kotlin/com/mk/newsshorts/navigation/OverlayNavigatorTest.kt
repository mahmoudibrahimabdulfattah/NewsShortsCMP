package com.mk.newsshorts.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayNavigatorTest {
    @Test
    fun `same-tab selections are emitted as reader gestures`() = runTest {
        val navigator = OverlayNavigator()
        val selections = mutableListOf<NavigationTab>()
        backgroundScope.launch {
            navigator.tabSelections.take(2).toList(selections)
        }
        runCurrent()

        navigator.selectTab(NavigationTab.FOR_YOU)
        navigator.selectTab(NavigationTab.FOR_YOU)
        runCurrent()

        assertEquals(
            listOf(NavigationTab.FOR_YOU, NavigationTab.FOR_YOU),
            selections,
        )
    }

    /**
     * A selection made before a collector attaches must not reach it.
     *
     * The feed treats a selection equal to the tab it is already on as "take me
     * back to the top" and refreshes. A replayed selection is exactly that shape
     * — it arrives at a freshly built collector that has already read [tab] — so
     * replaying one would refresh the feed over the network every time the
     * ViewModel is rebuilt, which is routine once the app moves to `viewModel {}`.
     */
    @Test
    fun `a selection made before a collector attaches is not replayed to it`() = runTest {
        val navigator = OverlayNavigator()

        navigator.selectTab(NavigationTab.COUNTRIES)
        val selections = mutableListOf<NavigationTab>()
        backgroundScope.launch {
            navigator.tabSelections.toList(selections)
        }
        runCurrent()

        assertEquals(emptyList(), selections)
        assertEquals(NavigationTab.COUNTRIES, navigator.tab.value)
    }

    @Test
    fun `sign-in is not stacked twice`() {
        val navigator = OverlayNavigator()

        navigator.open(Overlay.SignIn)
        navigator.open(Overlay.SignIn)

        assertEquals(listOf(Overlay.SignIn), navigator.overlays.value)
    }

    @Test
    fun `back pops overlay then returns to home tab before falling through`() {
        val navigator = OverlayNavigator()

        navigator.selectTab(NavigationTab.COUNTRIES)
        navigator.open(Overlay.Search)

        assertTrue(navigator.handleBack())
        assertEquals(emptyList(), navigator.overlays.value)
        assertEquals(NavigationTab.COUNTRIES, navigator.tab.value)

        assertTrue(navigator.handleBack())
        assertEquals(NavigationTab.FOR_YOU, navigator.tab.value)

        assertFalse(navigator.handleBack())
    }
}
