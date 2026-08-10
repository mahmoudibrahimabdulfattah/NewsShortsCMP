package org.example.newsshorts.server.store

import org.example.newsshorts.server.model.FeedArticleDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object Articles : Table("articles") {
    val id = long("id").autoIncrement()
    val title = text("title")
    val url = text("url").uniqueIndex()
    val description = text("description").nullable()
    val summary = text("summary").nullable()
    val imageUrl = text("image_url").nullable()
    val sourceName = text("source_name")
    val language = varchar("language", 8).index()
    val category = varchar("category", 32).index()
    val country = varchar("country", 8).nullable().index()
    val publishedAt = long("published_at").index()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * An article's title and summary rendered in one language.
 *
 * A country's news has to read in whichever language the user picked, but most
 * countries only have sources in one language — so an article is rendered into
 * every language it needs to appear in, translating the title along the way.
 */
object ArticleTexts : Table("article_texts") {
    val articleId = long("article_id").index()
    val language = varchar("language", 8).index()
    val title = text("title")
    val summary = text("summary")

    override val primaryKey = PrimaryKey(articleId, language)
}

class ArticleStore(dbPath: String) {

    init {
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        // createMissingTablesAndColumns so a cached database from before a
        // column was added (CI restores it between runs) migrates in place.
        transaction { SchemaUtils.createMissingTablesAndColumns(Articles, ArticleTexts) }
    }

    /** Inserts if the URL is new. Returns the new row id, or null if it already existed. */
    fun insertIfNew(
        title: String,
        url: String,
        description: String?,
        imageUrl: String?,
        sourceName: String,
        language: String,
        category: String,
        country: String?,
        publishedAt: Long,
    ): Long? = transaction {
        val result = Articles.insertIgnore {
            it[Articles.title] = title
            it[Articles.url] = url
            it[Articles.description] = description
            it[Articles.imageUrl] = imageUrl
            it[Articles.sourceName] = sourceName
            it[Articles.language] = language
            it[Articles.category] = category
            it[Articles.country] = country
            it[Articles.publishedAt] = publishedAt
            it[createdAt] = System.currentTimeMillis()
        }
        result.getOrNull(Articles.id)
    }

    /**
     * Drops articles published before [cutoffMillis]. Without this the database
     * grows without bound and the render budget drifts toward stale articles.
     */
    fun prune(cutoffMillis: Long): Int = transaction {
        val stale = Articles.selectAll()
            .andWhere { Articles.publishedAt less cutoffMillis }
            .map { it[Articles.id] }
        if (stale.isEmpty()) return@transaction 0
        ArticleTexts.deleteWhere { ArticleTexts.articleId inList stale }
        Articles.deleteWhere { Articles.id inList stale }
        stale.size
    }

    fun putText(articleId: Long, language: String, title: String, summary: String) {
        transaction {
            ArticleTexts.insertIgnore {
                it[ArticleTexts.articleId] = articleId
                it[ArticleTexts.language] = language
                it[ArticleTexts.title] = title
                it[ArticleTexts.summary] = summary
            }
        }
    }

    data class PendingText(
        val id: Long,
        val title: String,
        val description: String?,
        val sourceLanguage: String,
        val targetLanguage: String,
        val category: String,
        val country: String?,
    )

    /**
     * Article/language pairs still missing text, interleaved round-robin across
     * (target language, category, country) groups so a burst from one source
     * can't starve the other feeds of the per-cycle budget.
     *
     * [countryLanguages] are the languages every country feed is offered in.
     */
    fun pendingTexts(limit: Int, countryLanguages: Set<String>): List<PendingText> = transaction {
        val existing: Set<Pair<Long, String>> = ArticleTexts.selectAll()
            .map { it[ArticleTexts.articleId] to it[ArticleTexts.language] }
            .toSet()

        val candidates = Articles.selectAll()
            .orderBy(Articles.publishedAt, SortOrder.DESC)
            .limit(limit * 6)
            .flatMap { row ->
                val id = row[Articles.id]
                val sourceLanguage = row[Articles.language]
                val country = row[Articles.country]
                val targets = buildSet {
                    add(sourceLanguage)
                    if (country != null) addAll(countryLanguages)
                }
                targets.filter { (id to it) !in existing }.map { target ->
                    PendingText(
                        id = id,
                        title = row[Articles.title],
                        description = row[Articles.description],
                        sourceLanguage = sourceLanguage,
                        targetLanguage = target,
                        category = row[Articles.category],
                        country = country,
                    )
                }
            }

        val groups = candidates
            .groupBy { Triple(it.targetLanguage, it.category, it.country) }
            .values.map { it.iterator() }
        val interleaved = ArrayList<PendingText>(limit)
        while (interleaved.size < limit && groups.any { it.hasNext() }) {
            groups.forEach { group ->
                if (interleaved.size < limit && group.hasNext()) interleaved.add(group.next())
            }
        }
        interleaved
    }

    /**
     * Ready-to-serve feed. Filtering is on the *text* language, not the
     * source's, so a translated article appears in the language the reader
     * asked for.
     */
    fun feed(
        language: String?,
        category: String?,
        limit: Int,
        offset: Long,
        country: String? = null,
    ): Pair<List<FeedArticleDto>, Long> =
        transaction {
            fun base() = Articles
                .join(ArticleTexts, JoinType.INNER, onColumn = Articles.id, otherColumn = ArticleTexts.articleId)
                .selectAll()
                .also { query ->
                    language?.let { query.andWhere { ArticleTexts.language eq it } }
                    category?.let { query.andWhere { Articles.category eq it } }
                    country?.let { query.andWhere { Articles.country eq it } }
                }

            val total = base().count()
            val rows = base()
                .orderBy(Articles.publishedAt, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map {
                    FeedArticleDto(
                        id = it[Articles.id],
                        title = it[ArticleTexts.title],
                        summary = it[ArticleTexts.summary],
                        url = it[Articles.url],
                        imageUrl = it[Articles.imageUrl],
                        sourceName = it[Articles.sourceName],
                        language = it[ArticleTexts.language],
                        category = it[Articles.category],
                        publishedAt = it[Articles.publishedAt],
                    )
                }
            rows to total
        }
}
