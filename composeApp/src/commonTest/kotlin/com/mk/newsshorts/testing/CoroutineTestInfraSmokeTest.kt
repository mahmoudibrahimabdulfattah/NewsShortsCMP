package com.mk.newsshorts.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineTestInfraSmokeTest {
    @Test
    fun virtualTimeSchedulerAdvancesThroughFakeDelay() = runTest {
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataDelayMs = 5_000
        }

        sync.deleteUserData("reader-1")

        assertEquals(5_000, currentTime)
        assertEquals<List<RemoteSyncCall>>(listOf(RemoteSyncCall.DeleteUserData("reader-1")), sync.completedCalls)
        assertEquals(listOf("reader-1"), sync.deletedUids)
    }

    @Test
    fun cancellingFakeOperationLeavesCallStartedButNotCompleted() = runTest {
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataDelayMs = 5_000
        }

        val job = launch {
            sync.deleteUserData("reader-1")
        }
        runCurrent()
        advanceTimeBy(1_000)
        job.cancelAndJoin()
        runCurrent()

        val call = RemoteSyncCall.DeleteUserData("reader-1")
        assertEquals<List<RemoteSyncCall>>(listOf(call), sync.calls)
        assertFalse(call in sync.completedCalls)
        assertFalse("reader-1" in sync.deletedUids)
    }

    @Test
    fun fakesExposeFailureAndAuthStateControl() = runTest {
        val sync = FakeRemoteSyncClient().apply {
            deleteUserDataError = IllegalStateException("boom")
        }
        val auth = FakeAuthClient()

        assertFailsWith<IllegalStateException> {
            sync.deleteUserData("reader-1")
        }

        assertNull(auth.currentUser.value)
        auth.signInWithGoogle()
        assertEquals(auth.userAfterSignIn, auth.currentUser.value)
    }
}
