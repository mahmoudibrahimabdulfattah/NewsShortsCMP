package com.mk.newsshorts.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.dsl.module

interface Navigator {
    /**
     * Where the reader is.
     *
     * This is state: selecting the current tab again leaves this value unchanged.
     * Consumers that need to react to the gesture itself must collect
     * [tabSelections].
     */
    val tab: StateFlow<NavigationTab>

    /**
     * What the reader asked for.
     *
     * This emits for every tab tap, including tapping the tab already selected.
     * The feed uses that same-tab selection as "take me back to the top"; forty
     * cards deep, the alternative is forty swipes.
     *
     * Deliberately without replay. A replayed selection would arrive at a newly
     * built collector already equal to [tab], which is the same-tab gesture —
     * so every ViewModel rebuild would fire a network refresh nobody asked for.
     * Nothing is lost by dropping it: the only collector is the feed, and the
     * tabs it would miss a tap from are drawn by the screen it backs.
     */
    val tabSelections: SharedFlow<NavigationTab>

    /**
     * Screens pushed above the tabs: details, Settings, Saved, Search, and the
     * smaller flows they can open. Last element is what is on screen; empty
     * means none. A list rather than one nullable field, because Settings needs
     * SignIn above it and the tabs themselves are not part of this stack: they
     * switch, they do not push.
     */
    val overlays: StateFlow<List<Overlay>>

    fun selectTab(tab: NavigationTab)
    fun open(overlay: Overlay)
    fun close()
    fun close(overlay: Overlay)
    fun handleBack(): Boolean
}

class OverlayNavigator : Navigator {
    private val mutableTab = MutableStateFlow(NavigationTab.FOR_YOU)
    override val tab: StateFlow<NavigationTab> = mutableTab.asStateFlow()

    private val mutableTabSelections = MutableSharedFlow<NavigationTab>(
        replay = 0,
        extraBufferCapacity = 16,
        // A tab tap is a gesture, not a record. If a slow collector ever let
        // sixteen pile up, the newest is the one the reader is waiting on —
        // and dropping is better than the alternative, which is a tap that
        // either suspends the caller or throws on the UI thread.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val tabSelections: SharedFlow<NavigationTab> = mutableTabSelections.asSharedFlow()

    private val mutableOverlays = MutableStateFlow<List<Overlay>>(emptyList())
    override val overlays: StateFlow<List<Overlay>> = mutableOverlays.asStateFlow()

    override fun selectTab(tab: NavigationTab) {
        mutableTab.value = tab
        // Cannot fail: the flow drops the oldest rather than refusing.
        mutableTabSelections.tryEmit(tab)
    }

    override fun open(overlay: Overlay) {
        mutableOverlays.update { overlays ->
            // Never stacked twice, or one back press would leave a duplicate
            // behind. Sign-in is the one that can arrive from two directions —
            // a tap and a followed link.
            if (overlay == Overlay.SignIn && Overlay.SignIn in overlays) {
                overlays
            } else {
                overlays + overlay
            }
        }
    }

    /** Pops exactly one overlay, which is the single back-press rule. */
    override fun close() {
        mutableOverlays.update { it.dropLast(1) }
    }

    /** Removes the newest matching entry, even when another overlay is above it. */
    override fun close(overlay: Overlay) {
        mutableOverlays.update { overlays ->
            val index = overlays.lastIndexOf(overlay)
            if (index < 0) overlays else overlays.take(index) + overlays.drop(index + 1)
        }
    }

    override fun handleBack(): Boolean {
        if (overlays.value.isNotEmpty()) {
            close()
            return true
        }
        if (tab.value != NavigationTab.FOR_YOU) {
            selectTab(NavigationTab.FOR_YOU)
            return true
        }
        return false
    }
}

val navigationModule = module {
    single<Navigator>(createdAtStart = true) { OverlayNavigator() }
}
