package org.example.newsshorts.server.store

import org.example.newsshorts.server.model.FeedArticleDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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

class ArticleStore(dbPath: String) {

    init {
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        // createMissingTablesAndColumns so a cached database from before a
        // column was added (CI restores it between runs) migrates in place.
        transaction { SchemaUtils.createMissingTablesAndColumns(Articles) }
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

    fun setSummary(articleId: Long, summary: String) {
        transaction {
            Articles.update({ Articles.id eq articleId }) { it[Articles.summary] = summary }
        }
    }

    data class PendingArticle(
        val id: Long,
        val title: String,
        val description: String?,
        val language: String,
        val category: String,
        val country: String?,
    )

    /**
     * Articles inserted but not yet summarized, interleaved round-robin across
     * (language, category, country) groups so a burst from one source can't
     * starve the other feeds of the per-cycle summary budget.
     */
    fun pendingSummaries(limit: Int): List<PendingArticle> = transaction {
        val newestFirst = Articles.selectAll()
            .andWhere { Articles.summary.isNull() }
            .orderBy(Articles.publishedAt, SortOrder.DESC)
            .limit(limit * 4)
            .map {
                PendingArticle(
                    id = it[Articles.id],
                    title = it[Articles.title],
                    description = it[Articles.description],
                    language = it[Articles.language],
                    category = it[Articles.category],
                    country = it[Articles.country],
                )
            }

        val groups = newestFirst
            .groupBy { Triple(it.language, it.category, it.country) }
            .values.map { it.iterator() }
        val interleaved = ArrayList<PendingArticle>(limit)
        while (interleaved.size < limit && groups.any { it.hasNext() }) {
            groups.forEach { group ->
                if (interleaved.size < limit && group.hasNext()) interleaved.add(group.next())
            }
        }
        interleaved
    }

    /** Ready-to-serve feed: only articles that have a summary. */
    fun feed(
        language: String?,
        category: String?,
        limit: Int,
        offset: Long,
        country: String? = null,
    ): Pair<List<FeedArticleDto>, Long> =
        transaction {
            fun base() = Articles.selectAll().andWhere { Articles.summary.isNotNull() }.also { query ->
                language?.let { query.andWhere { Articles.language eq it } }
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
                        title = it[Articles.title],
                        summary = it[Articles.summary] ?: "",
                        url = it[Articles.url],
                        imageUrl = it[Articles.imageUrl],
                        sourceName = it[Articles.sourceName],
                        language = it[Articles.language],
                        category = it[Articles.category],
                        publishedAt = it[Articles.publishedAt],
                    )
                }
            rows to total
        }
}
