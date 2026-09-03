package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.core.contract.feed.NewsCategories
import java.net.URI

data class RejectedSource(val sourceName: String, val reason: String)

data class SourceAudit(
    val accepted: List<SourceSnapshot>,
    val rejected: List<RejectedSource>,
)

/**
 * Rejects section feeds that no longer identify the section they were
 * configured for. General feeds may move because they do not grant a
 * specialised membership; a section feed may change host or protocol, but not
 * its path or query.
 *
 * Distinct section paths from one publisher that return the exact same useful
 * URL set are rejected together. That is what a publisher silently serving its
 * home-page feed at every old section URL looks like, and choosing one category
 * would merely make the contamination deterministic.
 */
fun auditSources(snapshots: List<SourceSnapshot>): SourceAudit {
    val rejected = linkedMapOf<SourceSnapshot, String>()

    snapshots.filter { snapshot ->
        snapshot.source.categories.any { it != NewsCategories.GENERAL }
    }.forEach { snapshot ->
        if (!samePathAndQuery(snapshot.source.url, snapshot.effectiveUrl)) {
            rejected[snapshot] = "redirected outside its configured section"
        }
    }

    snapshots
        .asSequence()
        .filter { snapshot ->
            snapshot.source.categories.any { it != NewsCategories.GENERAL } &&
                snapshot.articles.size >= MIN_DUPLICATE_ITEMS
        }
        .filterNot { it in rejected }
        .groupBy { publisherHost(it.effectiveUrl) }
        .values
        .forEach { publisherSources ->
            publisherSources.forEachIndexed { index, first ->
                val firstUrls = first.articles.mapTo(linkedSetOf()) { it.url }
                publisherSources.drop(index + 1).forEach { second ->
                    val secondUrls = second.articles.mapTo(linkedSetOf()) { it.url }
                    if (overlaps(firstUrls, secondUrls)) {
                        rejected.putIfAbsent(first, "duplicates ${second.source.name} across categories")
                        rejected.putIfAbsent(second, "duplicates ${first.source.name} across categories")
                    }
                }
            }
        }

    return SourceAudit(
        accepted = snapshots.filterNot { it in rejected },
        rejected = rejected.map { (snapshot, reason) -> RejectedSource(snapshot.source.name, reason) },
    )
}

/**
 * Whether two section feeds are serving the same articles.
 *
 * Measured as a share of the smaller feed rather than as equality: a publisher
 * answering every section path with one home-page feed rarely returns byte-identical
 * lists twice in a row — items arrive between the two fetches, and the feeds are
 * often cut to different lengths. Demanding an exact match let precisely the
 * publisher this check exists for slip through on a one-article difference.
 */
private fun overlaps(first: Set<String>, second: Set<String>): Boolean {
    val smaller = minOf(first.size, second.size)
    if (smaller < MIN_DUPLICATE_ITEMS) return false
    val shared = first.count { it in second }
    return shared.toDouble() / smaller >= DUPLICATE_OVERLAP
}

private fun samePathAndQuery(configured: String, effective: String): Boolean =
    try {
        val expected = URI(configured)
        val actual = URI(effective)
        expected.path.trimEnd('/') == actual.path.trimEnd('/') &&
            expected.rawQuery.orEmpty() == actual.rawQuery.orEmpty()
    } catch (_: Exception) {
        false
    }

private fun publisherHost(url: String): String =
    try {
        URI(url).host.orEmpty().removePrefix("www.").lowercase()
    } catch (_: Exception) {
        ""
    }

private const val MIN_DUPLICATE_ITEMS = 5

/**
 * How much of the smaller feed has to be shared before two sections count as
 * one. Below this, publishers that genuinely cross-post a story or two — a
 * technology piece that is also business news — would reject each other.
 */
private const val DUPLICATE_OVERLAP = 0.8
