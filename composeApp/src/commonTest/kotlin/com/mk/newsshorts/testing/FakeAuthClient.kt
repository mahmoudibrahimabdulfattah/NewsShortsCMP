package com.mk.newsshorts.testing

import com.mk.newsshorts.core.domain.auth.AuthClient
import com.mk.newsshorts.core.model.auth.AuthResult
import com.mk.newsshorts.core.model.auth.AuthUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthClient : AuthClient {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    var userAfterSignIn: AuthUser = AuthUser(
        uid = "test-user",
        displayName = "Test User",
        email = "test@example.com",
        photoUrl = null,
    )

    var signInWithGoogleResult: AuthResult = AuthResult.Success
    var sendSignInLinkResult: AuthResult = AuthResult.Success
    var completeSignInWithLinkResult: AuthResult = AuthResult.Success
    var deleteAccountResult: AuthResult = AuthResult.Success
    var isSignInLinkResult: Boolean = false

    var signInWithGoogleError: Throwable? = null
    var sendSignInLinkError: Throwable? = null
    var completeSignInWithLinkError: Throwable? = null
    var signOutError: Throwable? = null
    var deleteAccountError: Throwable? = null

    var signInWithGoogleDelayMs: Long = 0
    var sendSignInLinkDelayMs: Long = 0
    var completeSignInWithLinkDelayMs: Long = 0
    var signOutDelayMs: Long = 0
    var deleteAccountDelayMs: Long = 0

    val calls = mutableListOf<AuthCall>()
    val completedCalls = mutableListOf<AuthCall>()

    fun setUser(user: AuthUser?) {
        _currentUser.value = user
    }

    override suspend fun signInWithGoogle(): AuthResult {
        val call = AuthCall.SignInWithGoogle
        calls += call
        delay(signInWithGoogleDelayMs)
        signInWithGoogleError?.let { throw it }
        val result = signInWithGoogleResult
        if (result == AuthResult.Success) {
            _currentUser.value = userAfterSignIn
        }
        completedCalls += call
        return result
    }

    override suspend fun sendSignInLink(email: String, languageCode: String): AuthResult {
        val call = AuthCall.SendSignInLink(email, languageCode)
        calls += call
        delay(sendSignInLinkDelayMs)
        sendSignInLinkError?.let { throw it }
        val result = sendSignInLinkResult
        completedCalls += call
        return result
    }

    override fun isSignInLink(link: String): Boolean {
        val call = AuthCall.IsSignInLink(link)
        calls += call
        completedCalls += call
        return isSignInLinkResult
    }

    override suspend fun completeSignInWithLink(email: String, link: String): AuthResult {
        val call = AuthCall.CompleteSignInWithLink(email, link)
        calls += call
        delay(completeSignInWithLinkDelayMs)
        completeSignInWithLinkError?.let { throw it }
        val result = completeSignInWithLinkResult
        if (result == AuthResult.Success) {
            _currentUser.value = userAfterSignIn
        }
        completedCalls += call
        return result
    }

    override suspend fun signOut() {
        val call = AuthCall.SignOut
        calls += call
        delay(signOutDelayMs)
        signOutError?.let { throw it }
        _currentUser.value = null
        completedCalls += call
    }

    override suspend fun deleteAccount(): AuthResult {
        val call = AuthCall.DeleteAccount
        calls += call
        delay(deleteAccountDelayMs)
        deleteAccountError?.let { throw it }
        val result = deleteAccountResult
        if (result == AuthResult.Success) {
            _currentUser.value = null
        }
        completedCalls += call
        return result
    }

    fun reset() {
        _currentUser.value = null
        userAfterSignIn = AuthUser(
            uid = "test-user",
            displayName = "Test User",
            email = "test@example.com",
            photoUrl = null,
        )
        signInWithGoogleResult = AuthResult.Success
        sendSignInLinkResult = AuthResult.Success
        completeSignInWithLinkResult = AuthResult.Success
        deleteAccountResult = AuthResult.Success
        isSignInLinkResult = false
        signInWithGoogleError = null
        sendSignInLinkError = null
        completeSignInWithLinkError = null
        signOutError = null
        deleteAccountError = null
        signInWithGoogleDelayMs = 0
        sendSignInLinkDelayMs = 0
        completeSignInWithLinkDelayMs = 0
        signOutDelayMs = 0
        deleteAccountDelayMs = 0
        calls.clear()
        completedCalls.clear()
    }
}

sealed interface AuthCall {
    data object SignInWithGoogle : AuthCall
    data class SendSignInLink(val email: String, val languageCode: String) : AuthCall
    data class IsSignInLink(val link: String) : AuthCall
    data class CompleteSignInWithLink(val email: String, val link: String) : AuthCall
    data object SignOut : AuthCall
    data object DeleteAccount : AuthCall
}
