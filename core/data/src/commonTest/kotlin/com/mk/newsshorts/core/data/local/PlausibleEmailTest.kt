package com.mk.newsshorts.core.data.local

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * This check only guards the send button. It exists to stop a link being
 * mailed into the void, not to adjudicate what a valid address is — the real
 * verdict is whether the mail arrives, and being stricter here would reject
 * real addresses with no way for the reader to argue.
 */
class PlausibleEmailTest {

    @Test
    fun `ordinary addresses pass`() {
        listOf(
            "a@b.co",
            "mahmoud@gmail.com",
            "first.last+tag@sub.domain.example",
            "  padded@example.com  ",
        ).forEach { assertTrue(isPlausibleEmail(it), "rejected \"$it\"") }
    }

    @Test
    fun `an unfinished field does not send`() {
        listOf(
            "",
            "   ",
            "mahmoud",
            "mahmoud@",
            "@gmail.com",
            "mahmoud@gmail",
            "mahmoud gmail.com",
            "two@at@example.com",
        ).forEach { assertFalse(isPlausibleEmail(it), "accepted \"$it\"") }
    }

    @Test
    fun `a domain cannot be all dot`() {
        // ".com" and "example." both parse as "has a dot" under a naive check.
        assertFalse(isPlausibleEmail("someone@.com"))
        assertFalse(isPlausibleEmail("someone@example."))
    }
}
