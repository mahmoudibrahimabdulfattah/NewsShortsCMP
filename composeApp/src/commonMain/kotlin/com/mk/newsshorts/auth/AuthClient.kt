package com.mk.newsshorts.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in reader, or null for a guest.
 *
 * Deliberately thin — a name, an email, a photo, and the id sync is keyed on.
 * Nothing here is used for authorization; Firestore's own security rules are
 * what actually restrict a reader's data to their own uid.
 */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

/**
 * Why a sign-in attempt failed, as a value the UI can localize — never the
 * exception's own text.
 *
 * Firebase and Credential Manager messages are always English ("No credentials
 * available", "A network error ... has occurred") and often name internals a
 * reader has no use for. Passing them straight to the screen put English
 * sentences in the middle of the Arabic UI, which is exactly what the app's
 * own string table exists to prevent.
 */
enum class AuthFailure {
    /** No Google account on the device at all, or the picker had nothing to offer. */
    NO_GOOGLE_ACCOUNT,
    NETWORK,
    /** Wrong password, unknown email, or a disabled account — deliberately one case. */
    INVALID_CREDENTIALS,
    INVALID_EMAIL,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    /** Firebase requires a fresh sign-in before deleting an account. */
    REAUTHENTICATION_REQUIRED,
    /** GOOGLE_WEB_CLIENT_ID is missing from the build. */
    NOT_CONFIGURED,
    UNSUPPORTED_PLATFORM,
    UNKNOWN,
}

sealed interface AuthResult {
    data object Success : AuthResult
    /** The reader closed the picker or backed out — not a failure worth a toast. */
    data object Cancelled : AuthResult
    data class Error(val failure: AuthFailure) : AuthResult
}

/**
 * Sign-in is optional everywhere it appears: every screen in this app already
 * works for a guest, and every method here is additive on top of that — never
 * a gate a reader has to pass through first.
 */
interface AuthClient {
    val currentUser: StateFlow<AuthUser?>

    suspend fun signInWithGoogle(): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signUpWithEmail(email: String, password: String): AuthResult
    suspend fun signOut()

    /**
     * Removes the account itself, not just this device's session. Google Play
     * requires this exact capability once an app has accounts at all — see
     * `AccountDeletionClient` for what else it triggers.
     */
    suspend fun deleteAccount(): AuthResult
}

/** Used on every target except Android, where no sign-in provider ships. */
object NoOpAuthClient : AuthClient {
    override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(null).asStateFlow()

    private val unsupported = AuthResult.Error(AuthFailure.UNSUPPORTED_PLATFORM)

    override suspend fun signInWithGoogle(): AuthResult = unsupported
    override suspend fun signInWithEmail(email: String, password: String): AuthResult = unsupported
    override suspend fun signUpWithEmail(email: String, password: String): AuthResult = unsupported
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): AuthResult = unsupported
}
