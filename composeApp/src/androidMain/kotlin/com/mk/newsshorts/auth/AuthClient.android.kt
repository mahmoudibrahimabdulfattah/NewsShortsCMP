package com.mk.newsshorts.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.mk.newsshorts.config.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth, with Google via Credential Manager.
 *
 * Credential Manager rather than the older `GoogleSignInClient`: it is the
 * currently supported path, it works through the account picker the OS
 * already shows for passkeys and saved passwords, and it does not need the
 * Google Play Services Auth SDK's older sign-in UI.
 */
private class FirebaseAuthClient(
    private val context: Context,
    private val auth: FirebaseAuth,
) : AuthClient {

    private val mutableCurrentUser = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val currentUser: StateFlow<AuthUser?> = mutableCurrentUser.asStateFlow()

    init {
        // Covers every path that changes the signed-in user — sign-in,
        // sign-out, deletion — from one place, rather than updating the state
        // flow by hand after each.
        auth.addAuthStateListener { firebaseAuth ->
            mutableCurrentUser.value = firebaseAuth.currentUser?.toAuthUser()
        }
    }

    override suspend fun signInWithGoogle(): AuthResult {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            // Not configured yet — see the setup notes on GOOGLE_WEB_CLIENT_ID.
            // A blank audience would fail on Google's side anyway; this fails
            // sooner, with a message that says why.
            return AuthResult.Error("Google Sign-In is not configured")
        }
        return runCatching {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val googleIdCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
            auth.signInWithCredential(firebaseCredential).await()
            AuthResult.Success
        }.getOrElse { failure ->
            if (failure is GetCredentialCancellationException) {
                AuthResult.Cancelled
            } else {
                AuthResult.Error(failure.message ?: "Google Sign-In failed")
            }
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Success
        }.getOrElse { AuthResult.Error(it.message ?: "Sign-in failed") }

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult =
        runCatching {
            auth.createUserWithEmailAndPassword(email, password).await()
            AuthResult.Success
        }.getOrElse { AuthResult.Error(it.message ?: "Account creation failed") }

    override suspend fun signOut() {
        auth.signOut()
        // Otherwise Credential Manager keeps offering the same Google account
        // as the only option, and "sign in as someone else" has no way to work.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    override suspend fun deleteAccount(): AuthResult {
        val user = auth.currentUser ?: return AuthResult.Error("Not signed in")
        return runCatching {
            user.delete().await()
            AuthResult.Success
        }.getOrElse {
            // Firebase requires a *recent* sign-in for this specific operation
            // and throws FirebaseAuthRecentLoginRequiredException otherwise —
            // surfaced as-is so the caller can prompt a re-auth rather than
            // silently failing.
            AuthResult.Error(it.message ?: "Account deletion failed")
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl?.toString(),
    )
}

/** Null Firebase config means no Auth either — the app stays guest-only. */
fun createAuthClient(context: Context): AuthClient {
    if (FirebaseApp.getApps(context).isEmpty()) return NoOpAuthClient
    return FirebaseAuthClient(context, FirebaseAuth.getInstance())
}
