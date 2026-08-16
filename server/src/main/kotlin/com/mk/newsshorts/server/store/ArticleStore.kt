package com.mk.newsshorts.server.store

import com.mk.newsshorts.server.model.FeedArticleDto
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
import org.jetbrains.exposed.sql.insert
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

/**
 * When each push topic was last sent to. Persisted rather than held in memory
 * because every publish run is a fresh process — without it the rate limit
 * would reset on every cycle and readers would be notified every half hour.
 */
object PushLog : Table("push_log") {
    val topic = varchar("topic", 64)
    val articleUrl = text("article_url")
    val sentAt = long("sent_at")

    override val primaryKey = PrimaryKey(topic)
}

class ArticleStore(dbPath: String) {

    init {
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        // createMissingTablesAndColumns so a cached database from before a
        // column was added (CI restores it between runs) migrates in place.
        transaction { SchemaUtils.createMissingTablesAndColumns(Articles, ArticleTexts, PushLog) }
    }

    fun lastPushAt(topic: String): Long? = transaction {
        PushLog.selectAll().andWhere { PushLog.topic eq topic }
            .firstOrNull()?.get(PushLog.sentAt)
    }

    fun recordPush(topic: String, articleUrl: String, sentAt: Long) {
        transaction {
            PushLog.deleteWhere { PushLog.topic eq topic }
            PushLog.insert {
                it[PushLog.topic] = topic
                it[PushLog.articleUrl] = articleUrl
                it[PushLog.sentAt] = sentAt
            }
        }
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

        // Every article missing text is grouped first and truncated only by the
        // round-robin below. Taking the newest N up front instead would let a
        // high-volume general source fill the whole window, leaving the smaller
        // category feeds permanently unrendered.
        val queues = Articles.selectAll()
            .orderBy(Articles.publishedAt, SortOrder.DESC)
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
            .groupBy { Triple(it.targetLanguage, it.category, it.country) }
            .values.map { it.iterator() }

        val interleaved = ArrayList<PendingText>(limit)
        while (interleaved.size < limit && queues.any { it.hasNext() }) {
            queues.forEach { queue ->
                if (interleaved.size < limit && queue.hasNext()) interleaved.add(queue.next())
            }
        }
        interleaved
    }

    /**
     * Ready-to-serve feed. Filtering is on the *text* language, not the
     * source's, so a translated article appears in the language the reader
     * asked for.
     *
     * [diversifyBySource] interleaves publishers instead of serving strict
     * `published_at DESC` — see [interleaveBySource]. Off by default because
     * one caller genuinely wants the single newest article and nothing else:
     * the breaking-news push.
     */
    fun feed(
        language: String?,
        category: String?,
        limit: Int,
        offset: Long,
        country: String? = null,
        diversifyBySource: Boolean = false,
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

            // Interleaving has to happen before the window is cut, or it would
            // only shuffle rows a dominant publisher had already filled. Reading
            // a bounded multiple of the window keeps that affordable: with the
            // cap at 5x, a source would need to hold four fifths of the newest
            // rows before the tail ran short of others to alternate with.
            val readAhead = if (diversifyBySource) (offset + limit) * SOURCE_MIX_WINDOW else offset + limit
            val rows = base()
                .orderBy(Articles.publishedAt, SortOrder.DESC)
                .limit(readAhead.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
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

            val ordered = if (diversifyBySource) rows.interleaveBySource() else rows
            ordered.drop(offset.toInt()).take(limit) to total
        }

    private companion object {
        /** How many windows deep to read before interleaving. */
        const val SOURCE_MIX_WINDOW = 5
    }
}

/**
 * Rotates through publishers, newest first within each.
 *
 * Strict `published_at DESC` hands the top of a feed to whoever posts most
 * often. Two Egyptian dailies publishing every few minutes took half of the
 * first ten slots of the general Arabic feed, which made "For You" read as a
 * copy of the Egypt tab — the same stories, in the same order.
 *
 * Round-robin over sources, so the front of the feed is a mix and no publisher
 * can crowd the others out. Nothing is dropped: a source with more articles
 * than the rest simply keeps taking its turn once they run out. Ordering within
 * one publisher stays newest-first, and sources are visited in the order their
 * newest article appeared, so the freshest story is still first overall.
 *
 * The same reasoning as [ArticleStore.pendingTexts], applied to reading rather
 * than to rendering.
 */
internal fun List<FeedArticleDto>.interleaveBySource(): List<FeedArticleDto> {
    if (size < 2) return this
    val queues = groupBy { it.sourceName }.values.map { it.iterator() }
    if (queues.size < 2) return this
    val mixed = ArrayList<FeedArticleDto>(size)
    while (mixed.size < size) {
        queues.forEach { queue -> if (queue.hasNext()) mixed.add(queue.next()) }
    }
    return mixed
}
