package com.mk.newsshorts.presentation.ui.components

import com.mk.newsshorts.core.model.PublishedTimestamp

/** Shared publish-time formatter for feed, details, and notification UI. */
fun formatPublishedTime(
    timestamp: PublishedTimestamp,
    monthNames: List<String>,
    recentlyLabel: String,
): String {
    // An article rebuilt from a push may carry no timestamp; without this the
    // epoch default would render as 1 January 1970.
    if (timestamp.epochMillis <= 0L) return recentlyLabel
    return try {
        val totalDays: Long = timestamp.epochMillis / (1000 * 60 * 60 * 24)
        var remainingDays: Int = totalDays.toInt()
        var year = 1970
        while (true) {
            val daysInYear: Int = if (isLeapYear(year)) 366 else 365
            if (remainingDays < daysInYear) break
            remainingDays -= daysInYear
            year++
        }
        val daysInMonths: IntArray = if (isLeapYear(year)) {
            intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        } else {
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        }
        var month = 0
        while (month < 12 && remainingDays >= daysInMonths[month]) {
            remainingDays -= daysInMonths[month]
            month++
        }
        val day: Int = remainingDays + 1
        "$day ${monthNames[month]} $year"
    } catch (exception: Exception) {
        recentlyLabel
    }
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

/**
 * Isolates a source name so a Latin name keeps its own punctuation order inside
 * an RTL layout — without it "NYT U.S." renders as ".NYT U.S".
 */
fun isolateBidi(text: String): String = "⁨$text⁩"
