package com.mk.newsshorts.presentation.mvi

import com.mk.newsshorts.auth.AuthFailure
import com.mk.newsshorts.auth.AuthResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AfterUnsuccessfulAuthTest {

    @Test
    fun `auth error clears progress and surfaces failure`() {
        val state = NewsUiState(authInProgress = true)
            .afterUnsuccessfulAuth(AuthResult.Error(AuthFailure.ACCOUNT_DATA_DELETE_FAILED))

        assertFalse(state.authInProgress)
        assertEquals(AuthFailure.ACCOUNT_DATA_DELETE_FAILED, state.authError)
    }

    @Test
    fun `cancel clears progress without creating an error`() {
        val state = NewsUiState(authInProgress = true)
            .afterUnsuccessfulAuth(AuthResult.Cancelled)

        assertFalse(state.authInProgress)
        assertNull(state.authError)
    }
}
