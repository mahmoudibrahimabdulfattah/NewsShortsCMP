package com.mk.newsshorts.auth

import com.mk.newsshorts.testing.FakeAuthClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthSessionTest {
    @Test
    fun `auth session exposes the auth client's current user`() {
        val authClient = FakeAuthClient()
        val session = DefaultAuthSession(authClient)
        val user = AuthUser(
            uid = "reader-1",
            displayName = "Reader",
            email = "reader@example.com",
            photoUrl = null,
        )

        assertNull(session.user.value)

        authClient.setUser(user)

        assertEquals(user, session.user.value)
    }
}
