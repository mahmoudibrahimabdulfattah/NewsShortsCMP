package com.mk.newsshorts.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthSession {
    val user: StateFlow<AuthUser?>
}

class DefaultAuthSession(
    private val authClient: AuthClient,
) : AuthSession {
    override val user: StateFlow<AuthUser?> = authClient.currentUser
}
