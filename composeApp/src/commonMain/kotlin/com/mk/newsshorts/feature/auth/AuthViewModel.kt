package com.mk.newsshorts.feature.auth

import com.mk.newsshorts.core.domain.auth.AuthClient
import com.mk.newsshorts.core.model.auth.AuthFailure
import com.mk.newsshorts.core.model.auth.AuthResult
import com.mk.newsshorts.core.domain.auth.AuthSession
import com.mk.newsshorts.core.model.auth.AuthUser
import com.mk.newsshorts.core.data.local.PendingSignInEmailPersistence
import com.mk.newsshorts.core.data.local.isPlausibleEmail
import com.mk.newsshorts.core.domain.use_case.DeleteAccountUseCase
import com.mk.newsshorts.navigation.SignInLinkBus
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

data class AuthUiState(
    /** Null for a guest. Every screen already works without one. */
    val authUser: AuthUser? = null,
    /** True while a sign-in/up/delete call is in flight - drives a spinner. */
    val authInProgress: Boolean = false,
    /**
     * The last auth failure, shown once then dismissed. A value rather than a
     * message so the screen can render it in the reader's language.
     */
    val authError: AuthFailure? = null,
    /**
     * The address a sign-in link was just sent to. Non-null means the reader is
     * waiting on their inbox.
     */
    val pendingSignInEmail: String? = null,
    /**
     * A followed link that arrived with no stored address to redeem it.
     * Held so the screen can ask who it belongs to instead of discarding it.
     */
    val unclaimedSignInLink: String? = null,
) {
    val hasUnclaimedLink: Boolean get() = unclaimedSignInLink != null
}

sealed interface AuthUiEvent {
    /** Leaves the sign-in screen without changing auth state. */
    data object Closed : AuthUiEvent
    data object SignInWithGoogle : AuthUiEvent
    /** Emails a sign-in link. Signing in only happens once it is followed. */
    data class SendSignInLink(val email: String) : AuthUiEvent
    /** A followed link arrived from the OS, carrying no address of its own. */
    data class SignInLinkOpened(val link: String) : AuthUiEvent
    /**
     * The address for a link opened on a device that never requested one, so
     * nothing was stored to match it against.
     */
    data class SupplyLinkEmail(val email: String) : AuthUiEvent
    /** Back out of the "check your inbox" state to send to a different address. */
    data object CancelPendingSignInLink : AuthUiEvent
    data object SignOut : AuthUiEvent
    data object DeleteAccount : AuthUiEvent
    data object DismissAuthError : AuthUiEvent
}

sealed interface AuthUiEffect {
    data object CloseOverlay : AuthUiEffect
    data object OpenSignInOverlay : AuthUiEffect
}

class AuthViewModel(
    private val authClient: AuthClient,
    private val authSession: AuthSession,
    private val pendingSignInEmailStore: PendingSignInEmailPersistence,
    private val signInLinkBus: SignInLinkBus,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val appLocaleCode: () -> String,
    private val scopeOverride: CoroutineScope? = null,
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(
        AuthUiState(authUser = authSession.user.value)
    )
    val uiState: StateFlow<AuthUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<AuthUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<AuthUiEffect> = effectChannel.receiveAsFlow()

    private val authScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    init {
        authScope.launch {
            authSession.user.collect { user ->
                mutableState.update { it.copy(authUser = user, authInProgress = false) }
            }
        }
        authScope.launch {
            signInLinkBus.pending.collect { link ->
                if (link == null) return@collect
                consumePostedSignInLink(link)
            }
        }
    }

    fun processEvent(event: AuthUiEvent) {
        when (event) {
            AuthUiEvent.Closed -> Unit
            AuthUiEvent.SignInWithGoogle -> handleSignInWithGoogle()
            is AuthUiEvent.SendSignInLink -> handleSendSignInLink(event.email)
            is AuthUiEvent.SignInLinkOpened -> handleSignInLinkOpened(event.link)
            is AuthUiEvent.SupplyLinkEmail -> handleSupplyLinkEmail(event.email)
            AuthUiEvent.CancelPendingSignInLink -> handleCancelPendingSignInLink()
            AuthUiEvent.SignOut -> handleSignOut()
            AuthUiEvent.DeleteAccount -> handleDeleteAccount()
            AuthUiEvent.DismissAuthError -> handleDismissAuthError()
        }
    }

    fun consumePendingSignInLink() {
        val link = signInLinkBus.pending.value ?: return
        consumePostedSignInLink(link)
    }

    private fun consumePostedSignInLink(link: String) {
        handleSignInLinkOpened(link)
        // Consumed whether or not it worked: these are single-use, so retrying
        // the same one only ever produces a worse error.
        signInLinkBus.consume()
    }

    private fun handleSignInWithGoogle() {
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        authScope.launch {
            when (val result = authClient.signInWithGoogle()) {
                AuthResult.Success -> effectChannel.send(AuthUiEffect.CloseOverlay)
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    /**
     * Success here means the mail is on its way, not that anyone is signed in -
     * the reader now has to leave the app entirely, so the address is written
     * to disk before the state changes.
     */
    private fun handleSendSignInLink(email: String) {
        val address = email.trim()
        if (!isPlausibleEmail(address)) {
            mutableState.update { it.copy(authError = AuthFailure.INVALID_EMAIL) }
            return
        }
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        authScope.launch {
            when (val result = authClient.sendSignInLink(address, appLocaleCode())) {
                AuthResult.Success -> {
                    pendingSignInEmailStore.save(address)
                    mutableState.update {
                        it.copy(authInProgress = false, pendingSignInEmail = address)
                    }
                }
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    /**
     * A followed link, handed over by the OS. The address it belongs to is not
     * in the link, so a link opened on a device that never asked for one is
     * held for the screen to ask about rather than dropped.
     */
    private fun handleSignInLinkOpened(link: String) {
        if (!authClient.isSignInLink(link)) return
        val storedEmail = pendingSignInEmailStore.load()
        if (storedEmail == null) {
            mutableState.update { it.copy(unclaimedSignInLink = link) }
            authScope.launch {
                effectChannel.send(AuthUiEffect.OpenSignInOverlay)
            }
            return
        }
        completeLinkSignIn(email = storedEmail, link = link)
    }

    private fun handleSupplyLinkEmail(email: String) {
        val link = mutableState.value.unclaimedSignInLink ?: return
        val address = email.trim()
        if (!isPlausibleEmail(address)) {
            mutableState.update { it.copy(authError = AuthFailure.INVALID_EMAIL) }
            return
        }
        completeLinkSignIn(email = address, link = link)
    }

    private fun completeLinkSignIn(email: String, link: String) {
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        authScope.launch {
            when (val result = authClient.completeSignInWithLink(email, link)) {
                AuthResult.Success -> {
                    pendingSignInEmailStore.clear()
                    mutableState.update {
                        it.copy(pendingSignInEmail = null, unclaimedSignInLink = null)
                    }
                    effectChannel.send(AuthUiEffect.CloseOverlay)
                }
                AuthResult.Cancelled -> mutableState.update { it.copy(authInProgress = false) }
                // The link stays held on failure: an expired one is worth saying
                // so about, and the reader may simply have mistyped the address
                // on a second device.
                is AuthResult.Error -> mutableState.update {
                    it.copy(authInProgress = false, authError = result.failure)
                }
            }
        }
    }

    private fun handleCancelPendingSignInLink() {
        pendingSignInEmailStore.clear()
        mutableState.update {
            it.copy(pendingSignInEmail = null, unclaimedSignInLink = null, authError = null)
        }
    }

    /**
     * Local bookmarks and settings are left exactly as they are: a guest is not
     * a second-class reader here, and the data on this device belongs to this
     * device regardless of whose account was just attached to it.
     */
    private fun handleSignOut() {
        authScope.launch { authClient.signOut() }
    }

    /**
     * Deletes the server side first, while the reader is still authenticated.
     * See [DeleteAccountUseCase], which enforces that order.
     */
    private fun handleDeleteAccount() {
        val uid = mutableState.value.authUser?.uid ?: return
        mutableState.update { it.copy(authInProgress = true, authError = null) }
        authScope.launch {
            when (val result = deleteAccountUseCase(uid)) {
                AuthResult.Success -> effectChannel.send(AuthUiEffect.CloseOverlay)
                else -> mutableState.update { it.afterUnsuccessfulAuth(result) }
            }
        }
    }

    private fun handleDismissAuthError() {
        mutableState.update { it.copy(authError = null) }
    }
}

/**
 * Success closes the overlay elsewhere; this helper is only the state left by
 * an auth attempt that did not finish successfully.
 */
fun AuthUiState.afterUnsuccessfulAuth(result: AuthResult): AuthUiState = when (result) {
    AuthResult.Success -> this
    AuthResult.Cancelled -> copy(authInProgress = false)
    is AuthResult.Error -> copy(authInProgress = false, authError = result.failure)
}
