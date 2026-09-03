package com.mk.newsshorts.core.domain.auth

import com.mk.newsshorts.core.model.auth.AuthResult
import com.mk.newsshorts.core.model.auth.AuthUser
import kotlinx.coroutines.flow.StateFlow

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
