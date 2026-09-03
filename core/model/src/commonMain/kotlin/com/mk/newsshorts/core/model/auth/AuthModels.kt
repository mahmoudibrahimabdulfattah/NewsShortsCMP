package com.mk.newsshorts.core.model.auth

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
