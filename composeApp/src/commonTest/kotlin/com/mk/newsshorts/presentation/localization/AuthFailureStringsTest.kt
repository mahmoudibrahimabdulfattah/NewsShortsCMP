package com.mk.newsshorts.presentation.localization

import com.mk.newsshorts.auth.AuthFailure
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sign-in errors arrive from Firebase and Credential Manager as English
 * sentences ("No credentials available"), and passing one straight to the
 * screen is what put English text in the middle of the Arabic UI. These cases
 * are what keeps that from coming back.
 */
class AuthFailureStringsTest {

    @Test
    fun `every failure has text in both languages`() {
        AuthFailure.entries.forEach { failure ->
            assertTrue(
                EnglishStrings.authFailure(failure).isNotBlank(),
                "$failure has no English text",
            )
            assertTrue(
                ArabicStrings.authFailure(failure).isNotBlank(),
                "$failure has no Arabic text",
            )
        }
    }

    @Test
    fun `the Arabic text is actually Arabic rather than the English string reused`() {
        AuthFailure.entries.forEach { failure ->
            val arabic = ArabicStrings.authFailure(failure)
            assertTrue(
                arabic.any { it in ARABIC_RANGE },
                "$failure has no Arabic characters: \"$arabic\"",
            )
        }
    }

    @Test
    fun `no failure text leaks an SDK phrase`() {
        // The exact strings the SDKs produce. If one of these ever appears in
        // the table, a raw exception message was pasted in rather than
        // translated.
        val sdkPhrases = listOf(
            "No credentials available",
            "A network error",
            "FirebaseAuth",
            "com.google",
        )
        AuthFailure.entries.forEach { failure ->
            listOf(EnglishStrings.authFailure(failure), ArabicStrings.authFailure(failure)).forEach { text ->
                sdkPhrases.forEach { phrase ->
                    assertFalse(
                        text.contains(phrase, ignoreCase = true),
                        "$failure carries the raw SDK phrase \"$phrase\"",
                    )
                }
            }
        }
    }

    @Test
    fun `a dead link tells the reader how to get a working one`() {
        // Both cases are recoverable in exactly one way — ask for another link
        // — and a message that does not say so leaves the reader stuck on a
        // screen whose only other option is giving up.
        listOf(AuthFailure.INVALID_LINK, AuthFailure.EXPIRED_LINK).forEach { failure ->
            assertTrue(
                EnglishStrings.authFailure(failure).contains("new one", ignoreCase = true),
                "$failure does not tell the reader to ask for a new link",
            )
            assertTrue(
                ArabicStrings.authFailure(failure).contains("جديد"),
                "$failure does not tell the reader to ask for a new link, in Arabic",
            )
        }
    }

    @Test
    fun `no failure mentions a password`() {
        // There are none. A message that says otherwise sends the reader
        // looking for a field that does not exist.
        AuthFailure.entries.forEach { failure ->
            assertFalse(
                EnglishStrings.authFailure(failure).contains("password", ignoreCase = true),
                "$failure mentions a password",
            )
            assertFalse(
                ArabicStrings.authFailure(failure).contains("كلمة المرور"),
                "$failure mentions a password, in Arabic",
            )
        }
    }

    private companion object {
        val ARABIC_RANGE = '؀'..'ۿ'
    }
}
