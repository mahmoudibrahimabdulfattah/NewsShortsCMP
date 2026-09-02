package com.mk.newsshorts.core.domain.preferences

import com.mk.newsshorts.core.model.NewsCategory

/**
 * The category row, ordered by what the reader said they wanted.
 *
 * Picked categories come first in the order they were picked; everything else
 * follows in its declared order. Nothing is removed — a reader who chose Sports
 * and Business has said what interests them most, not that the rest of the news
 * should become unreachable, and hiding tabs behind a choice made once at
 * install is how an app ends up feeling narrower than it is.
 *
 * An empty [preferred] returns the declared order unchanged, which is what a
 * reader who skipped onboarding should get.
 */
fun orderedCategories(preferred: List<String>): List<NewsCategory> {
    if (preferred.isEmpty()) return NewsCategory.entries
    val picked = preferred.mapNotNull { value ->
        NewsCategory.entries.firstOrNull { it.apiValue == value }
    }.distinct()
    return picked + NewsCategory.entries.filterNot { it in picked }
}

/**
 * Which category the feed opens on: the reader's first pick, or the default.
 *
 * Their first pick rather than a blend of all of them, because the feed shows
 * one category at a time and the first thing chosen on a list is the strongest
 * signal on it.
 */
fun openingCategory(preferred: List<String>): NewsCategory =
    orderedCategories(preferred).firstOrNull() ?: NewsCategory.GENERAL
