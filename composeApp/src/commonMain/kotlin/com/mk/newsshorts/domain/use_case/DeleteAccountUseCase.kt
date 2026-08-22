package com.mk.newsshorts.domain.use_case

import com.mk.newsshorts.auth.AuthClient
import com.mk.newsshorts.auth.AuthFailure
import com.mk.newsshorts.auth.AuthResult
import com.mk.newsshorts.sync.RemoteSyncClient
import com.mk.newsshorts.sync.SyncDelete
import kotlinx.coroutines.CancellationException

class DeleteAccountUseCase(
    private val authClient: AuthClient,
    private val remoteSyncClient: RemoteSyncClient,
) {
    /**
     * The server side goes first, while the reader is still authenticated:
     * Firestore's rules require `request.auth.uid == uid`, which stops being
     * true the moment `deleteAccount()` succeeds. If the remote delete fails,
     * keeping the account is the only state that still has authority to retry.
     */
    suspend operator fun invoke(uid: String): AuthResult {
        val deleted = try {
            remoteSyncClient.deleteUserData(uid)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            SyncDelete.Failed
        }

        return when (deleted) {
            SyncDelete.Success -> authClient.deleteAccount()
            SyncDelete.Failed -> AuthResult.Error(AuthFailure.ACCOUNT_DATA_DELETE_FAILED)
        }
    }
}
