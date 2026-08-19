package com.mk.newsshorts.server.share

/**
 * The path segment an article's landing page is published under.
 *
 * Derived from the article's URL and not from its database id. The id comes
 * from an autoincrement column in a database CI restores from a cache, which is
 * best-effort by definition — if that cache is ever lost, ids restart at 1 and a
 * link already sitting in someone's chat would open a different story. That is
 * worse than a dead link, and a hash of the URL cannot do it: the same article
 * is the same slug on a rebuilt database, a fresh one, or another machine.
 *
 * FNV-1a rather than a platform digest because the app has to arrive at the same
 * value in common code on five targets with no crypto dependency — the app
 * builds the link, so it has to name a page the server has already published.
 * The two modules cannot share code, so the algorithm is pinned by a literal
 * asserted in both test suites, the same way the deep link format is.
 *
 * The URL is hashed exactly as stored, with no canonicalising beyond trimming.
 * `Articles.url` is unique and reaches the app verbatim in the feed JSON, so the
 * two sides are already hashing identical bytes; normalising would only add a
 * second thing to keep in step.
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
