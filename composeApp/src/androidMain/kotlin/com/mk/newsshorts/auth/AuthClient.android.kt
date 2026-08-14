package com.mk.newsshorts.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
            // sooner, and says why.
            return AuthResult.Error(AuthFailure.NOT_CONFIGURED)
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
                AuthResult.Error(failure.toAuthFailure())
            }
        }
    }

    override suspend fun sendSignInLink(email: String, languageCode: String): AuthResult {
        // The project's own Auth domain: authorized by default, and the same
        // host the manifest filter claims. Read from the app's own options so
        // it cannot drift from whichever project google-services.json names.
        //
        // This URL is also the visible fallback whenever the browser handles
        // the link instead of the app, so it must actually serve a page — and
        // a Firebase Hosting site has to be deployed for that, which is the
        // same deploy that publishes /.well-known/assetlinks.json (via
        // firebase.json's appAssociation) and so lets App Links verify at all.
        // Without it the domain 404s and verification can never succeed.
        val projectId = auth.app.options.projectId
            ?: return AuthResult.Error(AuthFailure.NOT_CONFIGURED)
        // Chooses the email template Firebase renders. Set per send rather than
        // once at construction, because the reader can change the app's
        // language between one link and the next. An unknown code falls back to
        // the project's default template, so a new locale cannot break sending.
        auth.setLanguageCode(languageCode)
        return runCatching {
            val settings = ActionCodeSettings.newBuilder()
                .setUrl("https://$projectId.firebaseapp.com")
                .setHandleCodeInApp(true)
                .setAndroidPackageName(context.packageName, true, null)
                .build()
            auth.sendSignInLinkToEmail(email, settings).await()
            AuthResult.Success
        }.getOrElse { AuthResult.Error(it.toAuthFailure()) }
    }

    override fun isSignInLink(link: String): Boolean = auth.isSignInWithEmailLink(link)

    override suspend fun completeSignInWithLink(email: String, link: String): AuthResult =
        runCatching {
            auth.signInWithEmailLink(email, link).await()
            AuthResult.Success
        }.getOrElse { AuthResult.Error(it.toAuthFailure()) }

    override suspend fun signOut() {
        auth.signOut()
        // Otherwise Credential Manager keeps offering the same Google account
        // as the only option, and "sign in as someone else" has no way to work.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    override suspend fun deleteAccount(): AuthResult {
        val user = auth.currentUser ?: return AuthResult.Error(AuthFailure.NOT_SIGNED_IN)
        return runCatching {
            user.delete().await()
            AuthResult.Success
        }.getOrElse {
            // Firebase requires a *recent* sign-in for this specific operation
            // and throws FirebaseAuthRecentLoginRequiredException otherwise —
            // mapped to its own case so the screen can say "sign in again"
            // rather than showing a generic failure.
            AuthResult.Error(it.toAuthFailure())
        }
    }

    /**
     * Maps the SDKs' exceptions onto [AuthFailure]. Every branch here exists
     * because the untranslated message underneath would otherwise reach the
     * screen: Firebase and Credential Manager only ever speak English.
     */
    private fun Throwable.toAuthFailure(): AuthFailure = when (this) {
        is NoCredentialException -> AuthFailure.NO_GOOGLE_ACCOUNT
        is FirebaseNetworkException -> AuthFailure.NETWORK
        is FirebaseTooManyRequestsException -> AuthFailure.EMAIL_QUOTA_EXCEEDED
        is FirebaseAuthRecentLoginRequiredException -> AuthFailure.REAUTHENTICATION_REQUIRED
        // A used, tampered or timed-out link. Expiry gets its own case because
        // it is the one a reader can fix by asking for another link, and the
        // message should say so rather than implying the link was never valid.
        is FirebaseAuthActionCodeException ->
            if (errorCode == EXPIRED_ACTION_CODE) AuthFailure.EXPIRED_LINK else AuthFailure.INVALID_LINK
        is FirebaseAuthInvalidCredentialsException -> when (errorCode) {
            "ERROR_INVALID_EMAIL" -> AuthFailure.INVALID_EMAIL
            EXPIRED_ACTION_CODE -> AuthFailure.EXPIRED_LINK
            // The same exception type carries a bad action code on some paths.
            "ERROR_INVALID_ACTION_CODE" -> AuthFailure.INVALID_LINK
            else -> AuthFailure.UNKNOWN
        }
        else -> AuthFailure.UNKNOWN
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl?.toString(),
    )

    private companion object {
        const val EXPIRED_ACTION_CODE = "ERROR_EXPIRED_ACTION_CODE"
    }
}

/** Null Firebase config means no Auth either — the app stays guest-only. */
fun createAuthClient(context: Context): AuthClient {
    if (FirebaseApp.getApps(context).isEmpty()) return NoOpAuthClient
    return FirebaseAuthClient(context, FirebaseAuth.getInstance())
}
