package com.mk.newsshorts.presentation.ui.components

/**
 * How many lines the feed card gives its headline and summary at a given text
 * size.
 *
 * The card is one screen tall and cannot grow, so something has to give when
 * the type does. Trading lines for size is the right way round: a reader who
 * turned text up did it to read the words in front of them, not to see more
 * words too small to read. Holding the line counts fixed instead would push the
 * summary under the buttons at the largest step, which is the one place a
 * reading setting must not break.
 *
 * The headline keeps a line longer than the summary does, because a truncated
 * headline is a story the reader cannot identify, while a truncated summary is
 * a story they can still open.
 */
fun feedTitleMaxLines(scale: Float): Int = if (scale > 1.05f) 3 else 4

fun feedSummaryMaxLines(scale: Float): Int = if (scale > 1.05f) 2 else 3
