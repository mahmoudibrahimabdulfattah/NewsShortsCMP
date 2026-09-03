package com.mk.newsshorts.core.contract.deeplink

/**
 * The path segment an article's landing page is published under.
 *
 * Derived from the article's URL and not from its feed id: the id comes from an
 * autoincrement column in a database CI restores from a cache, and if that cache
 * is lost the ids restart at 1 — a link already sitting in someone's chat would
 * then open a different story. A hash of the URL cannot do that.
 *
 * The URL is hashed exactly as stored, with no canonicalising beyond trimming.
 * The server's `Articles.url` is unique and reaches the app verbatim in the
 * feed JSON, so the two sides are already hashing identical bytes; normalising
 * would only add a second thing to keep in step.
 *
 * FNV-1a, hand-rolled over unsigned arithmetic, because the server and every
 * app target have to produce the same digits with nothing platform-specific
 * underneath them.
 */
object ShareSlug {

    private const val OFFSET_BASIS: ULong = 14695981039346656037uL
    private const val PRIME: ULong = 1099511628211uL
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** 36^13 is the first power of 36 above ULong.MAX_VALUE. */
    private const val MAX_DIGITS = 13

    fun of(articleUrl: String): String {
        var hash = OFFSET_BASIS
        for (byte in articleUrl.trim().encodeToByteArray()) {
            // Bytes above 127 are negative and would sign-extend to a very
            // different value, which is the classic way two implementations of
            // this hash quietly disagree — and every Arabic URL has them.
            hash = hash xor (byte.toULong() and 0xFFuL)
            hash *= PRIME
        }
        return base36(hash)
    }

    private fun base36(value: ULong): String {
        if (value == 0uL) return "0"
        val digits = CharArray(MAX_DIGITS)
        var index = MAX_DIGITS
        var rest = value
        while (rest > 0uL) {
            digits[--index] = ALPHABET[(rest % 36uL).toInt()]
            rest /= 36uL
        }
        return digits.concatToString(index, MAX_DIGITS)
    }
}
