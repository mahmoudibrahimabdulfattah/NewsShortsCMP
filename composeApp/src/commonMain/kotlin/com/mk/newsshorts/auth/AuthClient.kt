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
    INVALID_EMAIL,
    /** The link is malformed, or was already used — they are single-use. */
    INVALID_LINK,
    EXPIRED_LINK,
    /**
     * Firebase caps how many sign-in emails a project may send per day, and
     * the cap is per project rather than per reader — so one person can find
     * themselves locked out by everyone else's attempts. Worth its own case:
     * the only useful advice is to use Google instead, or wait.
     */
    EMAIL_QUOTA_EXCEEDED,
    /** Asked to act on the account while nobody is signed in. */
    NOT_SIGNED_IN,
    /** Firebase requires a fresh sign-in before deleting an account. */
    REAUTHENTICATION_REQUIRED,
    /** The synced copy could not be removed, so the account was deliberately kept. */
    ACCOUNT_DATA_DELETE_FAILED,
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

    /**
     * Emails a one-time sign-in link, which is the whole of the email flow —
     * this app has no passwords.
     *
     * Not having them is the point: a password account proves nothing about
     * who owns the address, so anyone could have registered under someone
     * else's email and squatted it. A link cannot be followed by someone who
     * cannot read the inbox, so ownership is proven by construction rather
     * than by a verification step that has to be remembered and enforced.
     *
     * [AuthResult.Success] here means *the link was sent* — the reader is not
     * signed in until they follow it and [completeSignInWithLink] runs.
     *
     * [languageCode] picks which of the provider's email templates is used, so
     * a reader on the Arabic app is not mailed English. It is the app's own
     * language rather than the device's: the two disagree whenever the reader
     * has overridden it in Settings, and the app's choice is the deliberate one.
     */
    suspend fun sendSignInLink(email: String, languageCode: String): AuthResult

    /** Whether an incoming link is one of ours, before trying to act on it. */
    fun isSignInLink(link: String): Boolean

    /**
     * Finishes what [sendSignInLink] started. Firebase requires the address the
     * link was sent to, which the caller has to supply: the link may well be
     * opened on a different device than asked for it, where nothing was stored.
     */
    suspend fun completeSignInWithLink(email: String, link: String): AuthResult

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
    override suspend fun sendSignInLink(email: String, languageCode: String): AuthResult =
        unsupported
    override fun isSignInLink(link: String): Boolean = false
    override suspend fun completeSignInWithLink(email: String, link: String): AuthResult = unsupported
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): AuthResult = unsupported
}
