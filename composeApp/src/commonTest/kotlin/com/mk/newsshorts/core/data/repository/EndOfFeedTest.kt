package com.mk.newsshorts.core.data.repository

import com.mk.newsshorts.core.model.NewsError
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which failures end a feed and which stay failures.
 *
 * Getting this wrong is quiet in both directions: end the feed on a dropped
 * connection and a reader on a train believes they have run out of news, or
 * keep offering "try again" at the real end and the feed looks broken every
 * time it finishes.
 */
class EndOfFeedTest {

    @Test
    fun `a page that is not published is the end of the feed`() {
        val page = endOfFeedFor(NewsError.NotFound)

        assertNotNull(page, "a missing page should end the feed rather than fail")
        assertTrue(page.articles.isEmpty())
        assertNull(page.nextPage, "the end of a feed cannot point at another page")
    }

    @Test
    fun `a dropped connection is not the end of the feed`() {
        assertNull(endOfFeedFor(NewsError.NetworkError))
    }

    @Test
    fun `a server failure is not the end of the feed`() {
        assertNull(endOfFeedFor(NewsError.ServerError))
    }

    @Test
    fun `a page that came back empty is not the end of the feed`() {
        // A published page with nothing on it is a backend mistake, not a
        // boundary — the reader should be able to ask for it again.
        assertNull(endOfFeedFor(NewsError.NoDataError))
    }

    @Test
    fun `anything unrecognised stays a failure`() {
        assertNull(endOfFeedFor(NewsError.UnknownError("something else")))
    }
}
