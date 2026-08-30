package com.mk.newsshorts.server.store

import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleTextProvenanceTest {

    private fun store(): Pair<ArticleStore, File> {
        val db = File.createTempFile("article-texts", ".db").apply {
            delete()
            deleteOnExit()
        }
        return ArticleStore(db.absolutePath) to db
    }

    private fun ArticleStore.seedArticle(
        url: String,
        title: String = "Headline",
        description: String? = "Description",
        language: String = "en",
        category: String = "general",
        country: String? = null,
        publishedAt: Long = 1_000L,
    ): Long = insertIfNew(
        title = title,
        url = url,
        description = description,
        imageUrl = null,
        sourceName = "Source",
        language = language,
        category = category,
        country = country,
        publishedAt = publishedAt,
    )!!

    private fun ArticleStore.classifyGeneral(id: Long) {
        recordClassificationAttempt(id, "general")
    }

    @Test
    fun `AI text is final and is not pending`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/ai-final")

        store.putText(id, "en", "AI title", "AI summary", TextSource.AI)
        store.putText(id, "en", "Fallback title", "Fallback summary", TextSource.FALLBACK)
        store.classifyGeneral(id)

        val article = store.feed("en", "general", limit = 10, offset = 0).first.single()
        assertEquals("AI title", article.title)
        assertEquals("AI summary", article.summary)
        assertTrue(store.pendingTexts(10, emptySet()).none { it.id == id && it.targetLanguage == "en" })
        db.delete()
    }

    @Test
    fun `fallback retries stop after the attempt cap`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/retry-cap")
        store.classifyGeneral(id)

        store.putText(id, "en", "Fallback 1", "Summary 1", TextSource.FALLBACK)
        assertTrue(store.pendingTexts(10, emptySet()).any { it.id == id && it.targetLanguage == "en" })

        store.putText(id, "en", "Fallback 2", "Summary 2", TextSource.FALLBACK)
        assertTrue(store.pendingTexts(10, emptySet()).any { it.id == id && it.targetLanguage == "en" })

        store.putText(id, "en", "Fallback 3", "Summary 3", TextSource.FALLBACK)
        assertTrue(store.pendingTexts(10, emptySet()).none { it.id == id && it.targetLanguage == "en" })
        db.delete()
    }

    @Test
    fun `AI text upgrades a fallback row`() {
        val (store, db) = store()
        val id = store.seedArticle("https://example.com/fallback-upgrade")
        store.putText(id, "en", "Fallback title", "Fallback summary", TextSource.FALLBACK)

        store.putText(id, "en", "AI title", "AI summary", TextSource.AI)
        store.classifyGeneral(id)

        val article = store.feed("en", "general", limit = 10, offset = 0).first.single()
        assertEquals("AI title", article.title)
        assertEquals("AI summary", article.summary)
        assertTrue(store.pendingTexts(10, emptySet()).none { it.id == id && it.targetLanguage == "en" })
        db.delete()
    }

    @Test
    fun `fresh text requests outrank fallback retries`() {
        val (store, db) = store()
        val retryId = store.seedArticle("https://example.com/retry", publishedAt = 2_000L)
        val freshId = store.seedArticle("https://example.com/fresh", publishedAt = 1_000L)
        store.putText(retryId, "en", "Fallback", "Fallback summary", TextSource.FALLBACK)
        store.classifyGeneral(retryId)

        val pending = store.pendingTexts(limit = 1, countryLanguages = emptySet()).single()

        assertEquals(freshId, pending.id)
        db.delete()
    }

    @Test
    fun `old article text schema migrates without losing texts`() {
        val db = File.createTempFile("article-texts-migration", ".db").apply {
            delete()
            deleteOnExit()
        }
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
                    ) VALUES (
                        1, 'Source title', 'https://example.com/legacy',
                        'Source description', NULL, NULL, 'Source',
                        'en', 'general', NULL, 1000, 1000
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO article_texts (article_id, language, title, summary)
                    VALUES (1, 'en', 'Legacy title', 'Legacy summary')
                    """.trimIndent()
                )
            }
        }

        val store = ArticleStore(db.absolutePath)

        val article = store.feed("en", "general", limit = 10, offset = 0).first.single()
        assertEquals("Legacy title", article.title)
        assertEquals("Legacy summary", article.summary)
        assertTrue(store.pendingTexts(10, emptySet()).none { it.id == 1L && it.targetLanguage == "en" })

        val newId = store.seedArticle("https://example.com/after-migration", publishedAt = 2_000L)
        store.putText(newId, "en", "New title", "New summary", TextSource.AI)
        store.classifyGeneral(newId)
        assertEquals(2, store.feed("en", "general", limit = 10, offset = 0).first.size)
        db.delete()
    }
}
