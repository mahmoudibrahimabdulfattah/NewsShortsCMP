package com.mk.newsshorts.core.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The writer has its own suite because it is internal, so its test has to live
 * in the module that owns it. It pins the drain race: a write submitted in the
 * instant the writer drains must still be sent, which account-level tests
 * cannot reach through the public API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConflatedRemoteWriterTest {
    @Test
    fun `a write submitted exactly as the writer drains is still sent`() = runTest {
        val written = mutableListOf<Int>()
        lateinit var writer: ConflatedRemoteWriter<Int>
        writer = ConflatedRemoteWriter(
            scope = this,
            write = { _, value ->
                written += value
                if (value == 1) {
                    writer.submit(
                        uid = "reader-1",
                        stillCurrent = { true },
                        value = 2,
                    )
                }
            },
        )

        writer.submit(
            uid = "reader-1",
            stillCurrent = { true },
            value = 1,
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 2), written)
    }
}
