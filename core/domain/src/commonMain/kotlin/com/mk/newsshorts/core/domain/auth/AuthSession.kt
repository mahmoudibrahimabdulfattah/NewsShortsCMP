package com.mk.newsshorts.core.domain.auth

import com.mk.newsshorts.core.model.auth.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthSession {
    val user: StateFlow<AuthUser?>
}

class DefaultAuthSession(
    private val authClient: AuthClient,
) : AuthSession {
    override val user: StateFlow<AuthUser?> = authClient.currentUser
}
