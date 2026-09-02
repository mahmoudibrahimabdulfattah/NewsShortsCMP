package com.mk.newsshorts.feature.appgate

import com.mk.newsshorts.analytics.AnalyticsEvent
import com.mk.newsshorts.analytics.AnalyticsReporter
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.data.local.SecurityFlagPersistence
import com.mk.newsshorts.data.remote.RemoteConfigClient
import com.mk.newsshorts.data.remote.RequiredUpdate
import com.mk.newsshorts.data.remote.isDebugBuild
import com.mk.newsshorts.data.remote.requiredUpdateFor
import com.mk.newsshorts.presentation.viewmodel.BaseViewModel
import com.mk.newsshorts.security.DeviceIntegrityInspector
import com.mk.newsshorts.security.IntegrityPolicy
import com.mk.newsshorts.security.SecurityNotice
import com.mk.newsshorts.security.SecurityReason
import com.mk.newsshorts.security.securityNoticeFor
import com.mk.newsshorts.security.securityReasonFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Whether the app may be used at all, and on what terms.
 *
 * Two unrelated reasons to stop share this state because they answer the same
 * question for the shell and are mutually exclusive on screen: a build the
 * backend has retired, and a device the app should not trust.
 */
data class AppGateUiState(
    /** Non-null when this build is too old to run. Carries where to update. */
    val requiredUpdate: RequiredUpdate? = null,
    val securityNotice: SecurityNotice = SecurityNotice.NONE,
    /** Which signals produced [securityNotice]; decides the wording shown. */
    val securityReason: SecurityReason = SecurityReason.INTEGRITY,
) {
    /** True while nothing below the gate should be reachable. */
    val isBlocking: Boolean
        get() = requiredUpdate != null || securityNotice == SecurityNotice.BLOCKED
}

sealed interface AppGateUiEvent {
    /** The reader acknowledged the one-time integrity warning. */
    data object DismissSecurityWarning : AppGateUiEvent
}

class AppGateViewModel(
    private val remoteConfigClient: RemoteConfigClient,
    private val deviceIntegrityInspector: DeviceIntegrityInspector,
    private val securityFlags: SecurityFlagPersistence,
    private val analytics: AnalyticsReporter,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow(AppGateUiState())
    val uiState: StateFlow<AppGateUiState> = mutableState.asStateFlow()

    private val gateScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    init {
        check()
    }

    fun processEvent(event: AppGateUiEvent) {
        when (event) {
            AppGateUiEvent.DismissSecurityWarning -> dismissSecurityWarning()
        }
    }

    /**
     * Runs alongside the feed load rather than before it: these checks are
     * remote and slow, and a reader on a healthy device should never wait for
     * them to find out they were fine.
     *
     * The device is inspected regardless of whether the config arrives — a
     * blocked network is exactly the state an attacker would arrange if the
     * response decided whether the check ran. What the config decides is only
     * the response to it, and the default is the mildest one.
     */
    private fun check() {
        gateScope.launch {
            val config = remoteConfigClient.fetch()

            val update = config?.let { requiredUpdateFor(it, BuildConfig.VERSION_CODE) }
            if (update != null) {
                analytics.logEvent(AnalyticsEvent.UpdateRequired(BuildConfig.VERSION_CODE))
                mutableState.update { state -> state.copy(requiredUpdate = update) }
                // An unsupported build is the more urgent of the two screens,
                // and it is the one the reader can act on.
                return@launch
            }

            // A debug build never enforces any of this, so it does not run the
            // checks either — the whole feature is invisible while developing.
            if (isDebugBuild()) return@launch

            val integrity = deviceIntegrityInspector.inspect()
            if (!integrity.isCompromised && !integrity.isDeveloperEnvironment) return@launch

            analytics.logEvent(
                AnalyticsEvent.DeviceIntegrityFailed(
                    rooted = integrity.isRooted,
                    debugger = integrity.isDebuggerAttached,
                    tampered = integrity.isTampered,
                    emulator = integrity.isEmulator,
                    developerOptions = integrity.isDeveloperOptionsEnabled,
                )
            )
            val notice = securityNoticeFor(
                integrity = integrity,
                policy = IntegrityPolicy.fromWire(config?.rootPolicy),
                environmentPolicy = IntegrityPolicy.fromWire(
                    config?.emulatorPolicy,
                    default = IntegrityPolicy.BLOCK,
                ),
                warningAlreadySeen = securityFlags.securityWarningSeen(),
                enforce = true,
            )
            if (notice != SecurityNotice.NONE) {
                mutableState.update { state ->
                    state.copy(
                        securityNotice = notice,
                        securityReason = securityReasonFor(integrity),
                    )
                }
            }
        }
    }

    /** The warning is shown once; dismissing it records that it was seen. */
    private fun dismissSecurityWarning() {
        gateScope.launch {
            securityFlags.markSecurityWarningSeen()
            mutableState.update { state -> state.copy(securityNotice = SecurityNotice.NONE) }
        }
    }
}
