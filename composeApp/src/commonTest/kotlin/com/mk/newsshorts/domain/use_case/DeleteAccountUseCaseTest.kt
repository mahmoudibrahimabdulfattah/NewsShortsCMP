package com.mk.newsshorts.domain.use_case

import com.mk.newsshorts.auth.AuthFailure
import com.mk.newsshorts.auth.AuthResult
import com.mk.newsshorts.sync.SyncDelete
import com.mk.newsshorts.testing.AuthCall
import com.mk.newsshorts.testing.FakeAuthClient
import com.mk.newsshorts.testing.FakeRemoteSyncClient
import com.mk.newsshorts.testing.RemoteSyncCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class DeleteAccountUseCaseTest {

    private val uid = "reader-1"

    @Test
    fun `remote delete failure keeps auth account and surfaces auth error`() = runTest {
        val auth = FakeAuthClient()
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataResult = SyncDelete.Failed
        }

        val result = DeleteAccountUseCase(auth, sync)(uid)

        assertEquals(AuthResult.Error(AuthFailure.ACCOUNT_DATA_DELETE_FAILED), result)
        assertEquals(0, auth.calls.count { it == AuthCall.DeleteAccount })
    }

    @Test
    fun `remote delete success deletes auth account once`() = runTest {
        val auth = FakeAuthClient()
        val sync = FakeRemoteSyncClient()

        val result = DeleteAccountUseCase(auth, sync)(uid)

        assertEquals(AuthResult.Success, result)
        assertEquals(1, auth.calls.count { it == AuthCall.DeleteAccount })
    }

    @Test
    fun `a successful remote delete passes the signed-in uid through`() = runTest {
        val auth = FakeAuthClient()
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataResult = SyncDelete.Success
        }

        val result = DeleteAccountUseCase(auth, sync)(uid)

        assertEquals(AuthResult.Success, result)
        assertEquals(1, auth.calls.count { it == AuthCall.DeleteAccount })
        assertEquals(listOf(uid), sync.deletedUids)
    }

    @Test
    fun `retry after failed remote delete can complete deletion`() = runTest {
        val auth = FakeAuthClient()
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataResult = SyncDelete.Failed
        }
        val useCase = DeleteAccountUseCase(auth, sync)

        val failed = useCase(uid)
        sync.deleteUserDataResult = SyncDelete.Success
        val succeeded = useCase(uid)

        assertEquals(AuthResult.Error(AuthFailure.ACCOUNT_DATA_DELETE_FAILED), failed)
        assertEquals(AuthResult.Success, succeeded)
        assertEquals(1, auth.calls.count { it == AuthCall.DeleteAccount })
        assertEquals(listOf(uid), sync.deletedUids)
    }

    @Test
    fun `remote implementation throw still keeps auth account`() = runTest {
        val auth = FakeAuthClient()
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataError = IllegalStateException("boom")
        }

        val result = DeleteAccountUseCase(auth, sync)(uid)

        assertEquals(AuthResult.Error(AuthFailure.ACCOUNT_DATA_DELETE_FAILED), result)
        assertEquals(0, auth.calls.count { it == AuthCall.DeleteAccount })
    }

    @Test
    fun `remote deletion completes before auth account deletion starts`() = runTest {
        val auth = FakeAuthClient()
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataDelayMs = 5_000
        }

        val result = DeleteAccountUseCase(auth, sync)(uid)

        assertEquals(AuthResult.Success, result)
        assertEquals<List<RemoteSyncCall>>(listOf(RemoteSyncCall.DeleteUserData(uid)), sync.completedCalls)
        assertEquals<List<AuthCall>>(listOf(AuthCall.DeleteAccount), auth.calls)
    }
}
