package com.mk.newsshorts.server.store

import com.mk.newsshorts.server.feed.FeedLayout
import com.mk.newsshorts.server.feed.repaginate
import com.mk.newsshorts.server.summarize.parseCategory
import java.io.File
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleCategoryMembershipTest {

    private fun newDb(prefix: String = "article-categories"): File =
        File.createTempFile(prefix, ".db").apply {
            delete()
            deleteOnExit()
        }

    private fun store(db: File = newDb()): Pair<ArticleStore, File> =
        ArticleStore(db.absolutePath) to db

    private fun ArticleStore.seedArticle(
        url: String,
        title: String = "Headline",
        description: String? = "Description",
        language: String = "en",
        category: String = "general",
        country: String? = null,
        publishedAt: Long = 1_000L,
        sourceName: String = "Source",
    ): Long = insertIfNew(
        title = title,
        url = url,
        description = description,
        imageUrl = null,
        sourceName = sourceName,
        language = language,
        category = category,
        country = country,
        publishedAt = publishedAt,
    )!!

    private fun ArticleStore.putAiText(
        id: Long,
        language: String = "en",
        category: String = "general",
    ) {
        putText(id, language, "Rendered $id", "Summary $id", TextSource.AI)
        recordClassificationAttempt(id, category)
    }

    private fun membershipCount(db: File, articleId: Long? = null): Int =
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            val sql = if (articleId == null) {
                "SELECT COUNT(*) FROM article_categories"
            } else {
                "SELECT COUNT(*) FROM article_categories WHERE article_id = $articleId"
            }
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun createLegacyArticlesDb(db: File) {
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE articles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        description TEXT,
                        summary TEXT,
                        image_url TEXT,
                        source_name TEXT NOT NULL,
                        language VARCHAR(8) NOT NULL,
                        category VARCHAR(32) NOT NULL,
                        country VARCHAR(8),
                        published_at BIGINT NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate("CREATE UNIQUE INDEX articles_url_unique ON articles(url)")
                statement.executeUpdate(
                    """
                    CREATE TABLE article_texts (
                        article_id BIGINT NOT NULL,
                        language VARCHAR(8) NOT NULL,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        PRIMARY KEY (article_id, language)
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO articles (
                        id, title, url, description, summary, image_url, source_name,
                        language, category, country, published_at, created_at
                    ) VALUES
                        (1, 'Sports source title', 'https://example.com/legacy-sports',
                         'Sports description', NULL, NULL, 'Source',
                         'en', 'sports', NULL, 2000, 1000),
                        (2, 'Tech source title', 'https://example.com/legacy-tech',
                         'Tech description', NULL, NULL, 'Source',
                         'en', 'tech', NULL, 1000, 1000)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO article_texts (article_id, language, title, summary)
                    VALUES
                        (1, 'en', 'Legacy sports', 'Legacy sports summary'),
                        (2, 'en', 'Legacy tech', 'Legacy tech summary')
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `a specialised category needs source and classifier agreement`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/shared", category = "general")

        assertEquals(
            null,
            store.insertIfNew(
                title = "Headline",
                url = "https://example.com/shared",
                description = "Description",
                imageUrl = null,
                sourceName = "Source",
                language = "en",
                category = "sports",
                country = null,
                publishedAt = 1_000L,
            ),
        )
        store.putAiText(id, category = "sports")

        val general = store.feed("en", "general", limit = 10, offset = 0)
        val sports = store.feed("en", "sports", limit = 10, offset = 0)

        assertTrue(general.first.isEmpty())
        assertEquals(0, general.second)
        assertEquals(listOf(id), sports.first.map { it.id })
        assertEquals(1, sports.second)
        assertEquals("sports", sports.first.single().category)
        db.delete()
    }

    @Test
    fun `same URL from same source does not duplicate membership`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/same-source", category = "sports")

        store.insertIfNew(
            title = "Headline",
            url = "https://example.com/same-source",
            description = "Description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "sports",
            country = null,
            publishedAt = 1_000L,
        )
        store.putAiText(id, category = "sports")

        val sports = store.feed("en", "sports", limit = 10, offset = 0)
        assertEquals(listOf(id), sports.first.map { it.id })
        assertEquals(1, sports.second)
        assertEquals(1, membershipCount(db, id))
        db.delete()
    }

    @Test
    fun `classifier disagreement keeps a bad sports candidate in general`() {
        val (store, db) = store()
        val id = store.seedArticle(
            "https://example.com/floods-in-sports-feed",
            category = "sports",
        )
        store.putText(id, "en", "Floods", "Flood summary", TextSource.AI)

        assertEquals("general", store.recordClassificationAttempt(id, "general"))

        assertEquals(listOf(id), store.feed("en", "general", 10, 0).first.map { it.id })
        assertTrue(store.feed("en", "sports", 10, 0).first.isEmpty())
        db.delete()
    }

    @Test
    fun `a general feed's football story reaches the sports tab`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/football-in-general-feed", category = "general")
        store.putText(id, "en", "Football", "Football summary", TextSource.AI)

        // No feed ever filed this under sport. The classifier read the article,
        // which is the only reason the specialised tabs are not starved of the
        // publishers that carry the most news.
        assertEquals("sports", store.recordClassificationAttempt(id, "sports"))

        assertEquals(listOf(id), store.feed("en", "sports", 10, 0).first.map { it.id })
        assertTrue(store.feed("en", "general", 10, 0).first.isEmpty())
        db.delete()
    }

    @Test
    fun `a two-category answer stays in general pending a retry`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/both-answers", category = "general")
        store.putText(id, "en", "Tech", "Tech summary", TextSource.AI)

        val answer = parseCategory(Json.parseToJsonElement("""["technology", "general"]"""))
        assertEquals(null, answer)
        assertEquals("general", store.recordClassificationAttempt(id, answer))

        assertEquals(listOf(id), store.feed("en", "general", 10, 0).first.map { it.id })
        assertTrue(store.feed("en", "technology", 10, 0).first.isEmpty())
        assertEquals(listOf(id), store.pendingClassifications(10).map { it.id })
        db.delete()
    }

    @Test
    fun `an unclassified article with text waits in the classification queue`() {
        val (store, db) = store()
        val firstId = store.seedArticle(
            "https://example.com/awaiting-classification",
            category = "general",
        )
        val secondId = store.seedArticle(
            "https://example.com/also-awaiting-classification",
            category = "general",
        )
        store.putText(firstId, "en", "Headline", "Summary", TextSource.AI)
        store.putText(secondId, "en", "Another headline", "Another summary", TextSource.AI)

        // Their text is finished, so they must never be re-summarized to be
        // filed.
        assertTrue(store.pendingTexts(10, setOf("ar", "en")).none { it.id == firstId })
        assertEquals(
            setOf(firstId, secondId),
            store.pendingClassifications(10).mapTo(mutableSetOf()) { it.id },
        )
        assertEquals(2, store.countPendingClassifications())

        store.recordClassificationAttempt(firstId, "health")
        assertEquals(1, store.countPendingClassifications())
        store.recordClassificationAttempt(secondId, "technology")
        assertTrue(store.pendingClassifications(10).isEmpty())
        assertEquals(0, store.countPendingClassifications())
        db.delete()
    }

    @Test
    fun `classification stops being retried after the attempt cap`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/never-classifies", category = "general")
        store.putText(id, "en", "Headline", "Summary", TextSource.AI)

        repeat(3) { store.recordClassificationAttempt(id, classified = null) }

        assertTrue(store.pendingClassifications(10).none { it.id == id })
        assertEquals(listOf(id), store.feed("en", "general", 10, 0).first.map { it.id })
        db.delete()
    }

    @Test
    fun `an article without text is left to the summarizer to classify`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/not-rendered-yet", category = "general")

        assertTrue(store.pendingClassifications(10).none { it.id == id })
        assertEquals(listOf(id), store.pendingTexts(10, emptySet()).map { it.id })
        db.delete()
    }

    @Test
    fun `invalid classifier output never grants a specialised category`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/invalid-classification", category = "sports")
        store.putText(id, "en", "Headline", "Summary", TextSource.AI)

        assertEquals(
            "general",
            store.recordClassificationAttempt(id, "not-a-category"),
        )

        assertTrue(store.feed("en", "sports", 10, 0).first.isEmpty())
        assertEquals(listOf(id), store.pendingClassifications(10).map { it.id })
        db.delete()
    }

    @Test
    fun `a verified specialised article is rendered in every feed language`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/global-sport", category = "sports")
        store.putText(id, "en", "Sport", "Sport summary", TextSource.AI)
        store.recordClassificationAttempt(id, "sports")

        val pending = store.pendingTexts(10, setOf("ar", "en"))
        assertTrue(pending.any { it.id == id && it.targetLanguage == "ar" })

        store.putText(id, "ar", "رياضة", "ملخص رياضي", TextSource.AI)
        assertEquals(listOf(id), store.feed("ar", "sports", 10, 0).first.map { it.id })
        db.delete()
    }

    @Test
    fun `legacy category candidates return to general until verified`() {
        val db = newDb("article-categories-legacy")
        createLegacyArticlesDb(db)

        val store = ArticleStore(db.absolutePath)

        assertTrue(store.feed("en", "sports", limit = 10, offset = 0).first.isEmpty())
        assertTrue(store.feed("en", "tech", limit = 10, offset = 0).first.isEmpty())
        assertEquals(listOf(1L, 2L), store.feed("en", "general", limit = 10, offset = 0).first.map { it.id })
        assertEquals(listOf(1L, 2L), store.pendingClassifications(10).map { it.id })
        assertEquals(2, membershipCount(db))
        db.delete()
    }

    @Test
    fun `category backfill is idempotent across startups`() {
        val db = newDb("article-categories-idempotent")
        createLegacyArticlesDb(db)

        val first = ArticleStore(db.absolutePath)
        val countAfterFirstStartup = membershipCount(db)
        val firstFeed = first.feed("en", "sports", limit = 10, offset = 0).first.map { it.id }

        val second = ArticleStore(db.absolutePath)
        val countAfterSecondStartup = membershipCount(db)
        val secondFeed = second.feed("en", "sports", limit = 10, offset = 0).first.map { it.id }

        assertEquals(countAfterFirstStartup, countAfterSecondStartup)
        assertEquals(firstFeed, secondFeed)
        assertEquals(2, countAfterSecondStartup)
        db.delete()
    }

    @Test
    fun `a new category candidate resets verification until content agrees`() {
        val (store, db) = store()
        val shared = store.seedArticle("https://example.com/late-sports", category = "general", publishedAt = 1_000L)
        val newer = store.seedArticle("https://example.com/newer-general", category = "general", publishedAt = 2_000L)
        store.putAiText(shared)
        store.putAiText(newer)

        val firstGeneralOrder = store.feed("en", "general", limit = 10, offset = 0).first.map { it.id }
        val firstGeneral = repaginate(FeedLayout.EMPTY, firstGeneralOrder, pageSize = 1, firstNumber = 10)
        store.saveFeedLayout("en-general", firstGeneral)

        store.insertIfNew(
            title = "Headline",
            url = "https://example.com/late-sports",
            description = "Description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "sports",
            country = null,
            publishedAt = 1_000L,
        )

        assertTrue(store.feed("en", "sports", limit = 10, offset = 0).first.isEmpty())
        assertEquals("sports", store.recordClassificationAttempt(shared, "sports"))

        val secondGeneralOrder = store.feed("en", "general", limit = 10, offset = 0).first.map { it.id }
        val savedGeneral = store.feedLayout("en-general")
        val secondGeneral = repaginate(savedGeneral, secondGeneralOrder, pageSize = 1, firstNumber = 10)
        val sports = repaginate(
            store.feedLayout("en-sports"),
            store.feed("en", "sports", limit = 10, offset = 0).first.map { it.id },
            pageSize = 1,
            firstNumber = 10,
        )

        assertTrue(shared in savedGeneral.placedIds)
        assertTrue(secondGeneral.pages.none { it.articleIds.contains(shared) })
        assertEquals(listOf(shared), sports.head!!.articleIds)
        db.delete()
    }

    @Test
    fun `category health becomes strict only after reaching its configured minimum`() {
        val (store, db) = store()

        assertTrue(store.categoryFeedsReady(mapOf("ar-sports" to 39), 40).isEmpty())
        assertEquals(
            setOf("ar-sports"),
            store.categoryFeedsReady(mapOf("ar-sports" to 40), 40),
        )
        assertEquals(
            setOf("ar-sports"),
            store.categoryFeedsReady(mapOf("ar-sports" to 1), 40),
        )
        assertTrue(store.categoryFeedsReady(mapOf("ar-sports" to 49), 50).isEmpty())
        db.delete()
    }

    @Test
    fun `country-tagged sport reaches its category but not For You`() {
        val (store, db) = store()
        val id = store.seedArticle(
            url = "https://example.com/egypt-sport",
            language = "ar",
            category = "sports",
            country = "eg",
        )
        store.putAiText(id, language = "ar", category = "sports")

        val sports = store.feed(
            language = "ar",
            category = "sports",
            limit = 10,
            offset = 0,
            country = null,
            diversifyBySource = true,
            excludeCountryTagged = false,
        )
        val unfiltered = store.feed(
            language = "ar",
            category = null,
            limit = 10,
            offset = 0,
            country = null,
            diversifyBySource = true,
            excludeCountryTagged = true,
        )

        assertEquals(listOf(id), sports.first.map { it.id })
        assertEquals(1, sports.second)
        assertTrue(unfiltered.first.isEmpty())
        assertEquals(0, unfiltered.second)
        db.delete()
    }

    @Test
    fun `unfiltered feed does not multiply articles by membership`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/unfiltered", category = "general")
        store.insertIfNew(
            title = "Headline",
            url = "https://example.com/unfiltered",
            description = "Description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "sports",
            country = null,
            publishedAt = 1_000L,
        )
        store.putAiText(id, category = "sports")

        val unfiltered = store.feed("en", category = null, limit = 10, offset = 0)

        assertEquals(listOf(id), unfiltered.first.map { it.id })
        assertEquals(1, unfiltered.second)
        assertEquals("sports", unfiltered.first.single().category)
        db.delete()
    }

    @Test
    fun `prune removes category memberships with stale articles`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/stale", category = "general", publishedAt = 1_000L)
        store.insertIfNew(
            title = "Headline",
            url = "https://example.com/stale",
            description = "Description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "sports",
            country = null,
            publishedAt = 1_000L,
        )

        assertEquals(2, membershipCount(db, id))
        assertEquals(1, store.prune(cutoffMillis = 2_000L))

        assertEquals(0, membershipCount(db, id))
        db.delete()
    }

    @Test
    fun `pending text uses rarest category membership for round-robin fairness`() {
        val (store, db) = store()
        val shared = store.seedArticle("https://example.com/pending-shared", category = "general", publishedAt = 10_000L)
        store.insertIfNew(
            title = "Headline",
            url = "https://example.com/pending-shared",
            description = "Description",
            imageUrl = null,
            sourceName = "Source",
            language = "en",
            category = "sports",
            country = null,
            publishedAt = 10_000L,
        )
        repeat(5) { index ->
            store.seedArticle(
                url = "https://example.com/general-$index",
                category = "general",
                publishedAt = 9_000L - index,
            )
        }

        val pending = store.pendingTexts(limit = 1, countryLanguages = emptySet()).single()

        assertEquals(shared, pending.id)
        assertEquals("sports", pending.category)
        db.delete()
    }

    @Test
    fun `pending text retry cap still excludes unserved rows`() {
        val (store, db) = store()
        val capped = store.seedArticle("https://example.com/unserved-cap", category = "sports", publishedAt = 10_000L)
        repeat(3) {
            store.recordUnservedTextAttempt(capped, "en", "Rendered $capped", "Summary $capped")
        }
        store.recordClassificationAttempt(capped, "sports")
        val fresh = store.seedArticle("https://example.com/fresh-pending", category = "sports", publishedAt = 9_000L)

        val pendingIds = store.pendingTexts(limit = 10, countryLanguages = emptySet()).map { it.id }

        assertTrue(capped !in pendingIds)
        assertTrue(fresh in pendingIds)
        db.delete()
    }

    @Test
    fun `category membership indexes cover article id and category lookups`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/index-check")
        store.putAiText(id)

        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            val indexes = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA index_list('article_categories')").use { result ->
                    buildList {
                        while (result.next()) add(result.getString("name"))
                    }
                }
            }
            val indexedColumns = indexes.associateWith { index ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA index_info('$index')").use { result ->
                        buildList {
                            while (result.next()) add(result.getString("name"))
                        }
                    }
                }
            }
            assertTrue(indexedColumns.values.any { it.firstOrNull() == "article_id" }, indexedColumns.toString())
            assertTrue(indexedColumns.values.any { it.firstOrNull() == "category" }, indexedColumns.toString())
        }
        db.delete()
    }
}
