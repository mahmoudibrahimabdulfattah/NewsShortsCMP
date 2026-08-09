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
    val publishedAt = long("published_at").index()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

class ArticleStore(dbPath: String) {

    init {
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(Articles) }
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

    data class PendingArticle(val id: Long, val title: String, val description: String?, val language: String)

    /** Articles inserted but not yet summarized. */
    fun pendingSummaries(limit: Int): List<PendingArticle> = transaction {
        Articles.selectAll()
            .andWhere { Articles.summary.isNull() }
            .orderBy(Articles.publishedAt, SortOrder.DESC)
            .limit(limit)
            .map {
                PendingArticle(
                    id = it[Articles.id],
                    title = it[Articles.title],
                    description = it[Articles.description],
                    language = it[Articles.language],
                )
            }
    }

    /** Ready-to-serve feed: only articles that have a summary. */
    fun feed(language: String?, category: String?, limit: Int, offset: Long): Pair<List<FeedArticleDto>, Long> =
        transaction {
            fun base() = Articles.selectAll().andWhere { Articles.summary.isNotNull() }.also { query ->
                language?.let { query.andWhere { Articles.language eq it } }
                category?.let { query.andWhere { Articles.category eq it } }
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
