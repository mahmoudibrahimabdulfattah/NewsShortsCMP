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

sealed interface AuthResult {
    data object Success : AuthResult
    /** The reader closed the picker or backed out — not a failure worth a toast. */
    data object Cancelled : AuthResult
    data class Error(val message: String) : AuthResult
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

    private val unsupported = AuthResult.Error("Sign-in isn't available on this platform")

    override suspend fun signInWithGoogle(): AuthResult = unsupported
    override suspend fun signInWithEmail(email: String, password: String): AuthResult = unsupported
    override suspend fun signUpWithEmail(email: String, password: String): AuthResult = unsupported
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): AuthResult = unsupported
}
