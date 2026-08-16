package com.mk.newsshorts.server.feed

/**
 * One published page of a feed.
 *
 * [number] is identity, not position: a page keeps its number for as long as it
 * exists, and reading order is *descending* number, because a new page is
 * always sealed off the front of the feed and therefore holds newer articles
 * than every page sealed before it.
 */
data class FeedPage(val number: Int, val articleIds: List<Long>)

/**
 * How one feed's articles are split across files, and which article ids have
 * already been placed.
 *
 * [pages] is in reading order — the head page first, then the sealed pages,
 * newest first.
 *
 * [placedIds] is every article id this feed has ever published, whether it is
 * still on a page or not. Without it, an article that lost its slot (pruned, or
 * aged out of the published depth) could reappear at the head as if it had just
 * been published.
 *
 * A membership set rather than a high-water mark, because article ids are
 * assigned at *ingestion* while an article joins a feed only once its text
 * exists in that feed's language — and rendering is budgeted, newest first. An
 * article ingested early and translated late therefore has a low id and is
 * still new to the feed, which a "highest id seen" test reads as already
 * published and drops for good.
 */
data class FeedLayout(
    val pages: List<FeedPage>,
    val placedIds: Set<Long>,
) {
    val head: FeedPage? get() = pages.firstOrNull()

    companion object {
        val EMPTY = FeedLayout(pages = emptyList(), placedIds = emptySet())
    }
}

/**
 * Places [order] into pages, keeping every already-sealed page exactly as it
 * was.
 *
 * [order] is the feed as it should read right now: newest first, already
 * interleaved by source. Interleaving happens over the whole depth *before*
 * this runs, so a publisher cannot dominate the region around a page boundary —
 * mixing each page separately would do exactly that.
 *
 * The stability rule this exists for: articles arrive at the front of the feed
 * every half hour, so a page defined as "ranks 41-80 of whatever is published
 * now" holds different articles on every run, and a reader who loaded page 1
 * half an hour ago would see some of it again on page 2 (or skip past a story
 * entirely). So only the head page is allowed to move. Everything below it is
 * frozen the moment it is sealed:
 *
 *  - new articles enter the head page and nowhere else;
 *  - when the head grows past [pageSize], its *oldest* [pageSize] articles are
 *    sealed off as a new page, which takes the next number up;
 *  - a sealed page's contents and its link to the page below it never change.
 *
 * A reader follows the chain frozen into the file they downloaded, so pages
 * sealed after that download are simply not on their path — and everything on
 * those pages is either already in their hands or newer than anything they have
 * seen, which is what a refresh is for.
 *
 * Articles missing from [order] have been pruned or have fallen past the
 * published depth; their slots go with them, and a page that empties out
 * disappears.
 */
fun repaginate(
    previous: FeedLayout,
    order: List<Long>,
    pageSize: Int,
    firstNumber: Int = 1,
): FeedLayout {
    require(pageSize > 0) { "pageSize must be positive" }

    val live = order.toSet()
    val sealed = previous.pages.drop(1)
        .map { page -> page.copy(articleIds = page.articleIds.filter { it in live }) }
        .filter { it.articleIds.isNotEmpty() }
    val alreadySealed = sealed.flatMapTo(HashSet()) { it.articleIds }
    val previousHead = previous.head?.articleIds?.toHashSet() ?: HashSet()

    // Order decides the head's contents, not the previous layout: the head is
    // the volatile part of the feed and is re-mixed on every publish. New to
    // this feed, or already in its head — anything else was published once and
    // has since left, and must not come back.
    val headCandidates = order.filter {
        it !in alreadySealed && (it in previousHead || it !in previous.placedIds)
    }

    // [firstNumber] only ever applies to a feed with no history at all. That is
    // normally the first publish, but it is also what a lost layout looks like,
    // and there the number matters: restarting at 1 would republish `-p1.json`
    // with a different set of articles under a name readers already hold. Given
    // a number no publish has used before, the worst a lost layout can do is end
    // a reader's feed early, which the client already treats as the end.
    var nextNumber = when {
        sealed.isNotEmpty() -> sealed.maxOf { it.number } + 1
        else -> previous.head?.number ?: firstNumber
    }
    val newlySealed = ArrayList<FeedPage>()
    var head = headCandidates
    // Sealing from the tail: the chunk that comes off is the oldest part of the
    // head, so successive seals produce successively newer pages and descending
    // page number stays the reading order.
    while (head.size > pageSize) {
        newlySealed += FeedPage(number = nextNumber++, articleIds = head.takeLast(pageSize))
        head = head.dropLast(pageSize)
    }

    val pages = buildList {
        add(FeedPage(number = nextNumber, articleIds = head))
        addAll((newlySealed + sealed).sortedByDescending { it.number })
    }
    return FeedLayout(
        pages = pages,
        // Accumulated, never recomputed from the pages alone: an article that
        // gets pruned off a sealed page has to stay retired, and a set rebuilt
        // from what is currently published would let it back in at the top of
        // the feed as though it had just arrived.
        placedIds = previous.placedIds + pages.flatMapTo(HashSet()) { it.articleIds },
    )
}

/**
 * File names for a feed, given the name its head page is published under.
 *
 * The head keeps the plain name the app has always requested, so a build
 * released before pagination existed keeps working: it reads one file, ignores
 * the field it does not know about, and stops — which is what it did before.
 */
object FeedPageNames {

    fun head(feedKey: String): String = "$feedKey.json"

    fun sealed(feedKey: String, number: Int): String = "$feedKey-p$number.json"

    /** The name a page in [layout] at [index] is published under. */
    fun fileFor(feedKey: String, layout: FeedLayout, index: Int): String =
        if (index == 0) head(feedKey) else sealed(feedKey, layout.pages[index].number)
}
