package com.mk.newsshorts.server.ingest

import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.model.FeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssFetcherTest {

    private val voa = FeedSource(
        name = "VOA",
        url = "https://www.voanews.com/api/feed-id",
        language = "en",
        category = "general",
        excludeThirdPartyCredits = true,
    )

    @Test
    fun `every VOA feed opts into third-party credit exclusion`() {
        val sources = FeedCatalog.sources.filter { it.name == "VOA" || it.name.startsWith("VOA ") }

        assertEquals(6, sources.size)
        assertTrue(sources.all { it.excludeThirdPartyCredits })
    }

    @Test
    fun `plain VOA reporting is kept`() {
        assertFalse(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Congress debates the annual budget",
                description = "Lawmakers met Tuesday to discuss spending priorities.",
            )
        )
    }

    @Test
    fun `standard VOA Associated Press credit is dropped`() {
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Storm reaches the coast",
                description = "Some information for this report came from The Associated Press.",
            )
        )
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Flooding closes roads",
                description = "Associated Press contributed to this report.",
            )
        )
    }

    @Test
    fun `ordinary words containing AP are kept`() {
        assertFalse(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Apple opens a new campus",
                description = "The capital project includes an AP Photo exhibit.",
            )
        )
    }

    @Test
    fun `Reuters credit in a title is dropped`() {
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Reuters: Markets close higher",
                description = "Stocks advanced after the latest earnings reports.",
            )
        )
    }

    @Test
    fun `full and context-qualified AFP credits are dropped`() {
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Talks resume",
                description = "Reporting by Agence France-Presse.",
            )
        )
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Talks conclude",
                description = "Some information for this report came from AFP.",
            )
        )
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Election results announced",
                description = "AFP contributed to this report.",
            )
        )
        assertTrue(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "Storm reaches the coast",
                description = "Some information for this report came from AP.",
            )
        )
    }

    @Test
    fun `bare AFP without credit context is kept`() {
        assertFalse(
            shouldExcludeForThirdPartyCredits(
                voa,
                title = "AFP gene study enters its second phase",
                description = "Researchers published their latest findings.",
            )
        )
    }

    @Test
    fun `sources without opt-in keep agency credited items`() {
        assertFalse(
            shouldExcludeForThirdPartyCredits(
                voa.copy(excludeThirdPartyCredits = false),
                title = "Markets close higher",
                description = "Reuters contributed to this report.",
            )
        )
    }
}
