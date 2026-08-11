package org.example.newsshorts.presentation.ui.components

import org.example.newsshorts.domain.model.PublishedTimestamp

/**
 * Formats a publish date as "10 August 2026" using localized month names.
 *
 * Shared by the feed card and the details screen so a story never appears to
 * have two different dates.
 */
internal fun formatPublishedTime(
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
internal fun isolateBidi(text: String): String = "⁨$text⁩"
