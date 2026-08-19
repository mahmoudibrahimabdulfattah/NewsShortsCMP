package com.mk.newsshorts.server.store

import com.mk.newsshorts.server.feed.FeedLayout
import com.mk.newsshorts.server.feed.FeedPage
import com.mk.newsshorts.server.model.FeedArticleDto
import com.mk.newsshorts.server.push.SentNotification
import com.mk.newsshorts.server.share.SharedArticle
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

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
 * Which page of which feed an article was published on.
 *
 * This table is what makes a page boundary stable. Pages could otherwise only
 * be "ranks 41-80 of whatever is published right now", which is a different set
 * of articles after every ingestion cycle — see [com.mk.newsshorts.server.feed.repaginate].
 * Persisted for the same reason as [PushLog]: every publish is a fresh process.
 */
object FeedPages : Table("feed_pages") {
    /** The feed's published name, e.g. `en-general` or `country-eg-ar`. */
    val feedKey = varchar("feed_key", 64).index()
    val articleId = long("article_id")
    val page = integer("page")
    /** Position within the page, so a sealed page's order is frozen too. */
    val position = integer("position")

    override val primaryKey = PrimaryKey(feedKey, articleId)
}

/**
 * Every article a feed has ever published, still on a page or not.
 *
 * Separate from [FeedPages], which is rewritten on every publish and so only
 * ever describes the current layout. This one only grows, because the question
 * it answers is "has this feed published this article before?" — and an article
 * that has left the published depth must not be served again as though it were
 * new. See [com.mk.newsshorts.server.feed.FeedLayout].
 *
 * [placedAt] exists to bound the table: rows outlive the articles they name by
 * a wide margin (see [ArticleStore.PLACED_GRACE_MILLIS]) and are then dropped.
 */
object FeedPlaced : Table("feed_placed") {
    val feedKey = varchar("feed_key", 64)
    val articleId = long("article_id")
    val placedAt = long("placed_at").index()

    override val primaryKey = PrimaryKey(feedKey, articleId)
}

/**
 * Per-feed paging state: the head page's number.
 * See [com.mk.newsshorts.server.feed.FeedLayout].
 */
object FeedPageState : Table("feed_page_state") {
    val feedKey = varchar("feed_key", 64)
    val headPage = integer("head_page")

    /**
     * Dead, and kept only so a database cached from before [FeedPlaced] existed
     * still accepts inserts — the column is NOT NULL there, and CI restores that
     * database rather than starting clean. The value is never read.
     */
    val watermarkId = long("watermark_id").default(0L)

    override val primaryKey = PrimaryKey(feedKey)
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

/**
 * The articles a shared link can still land on.
 *
 * A copy rather than a view, because [Articles] is pruned to a week — deep
 * enough for a feed, and nowhere near long enough for a link somebody sent a
 * friend. These rows carry everything a landing page renders, so a page can be
 * rebuilt long after the article itself has gone, and they are cheap: no
 * description, no country, no join.
 *
 * Keyed by the slug the page is published under rather than by article id, for
 * the reason [com.mk.newsshorts.server.share.ShareSlug] exists — an id from a
 * restored database can name a different story, and this table outlives far
 * more restores than the feed does.
 */
object SharedArticles : Table("shared_articles") {
    val slug = varchar("slug", 16)
    val language = varchar("language", 8)
    val title = text("title")
    val summary = text("summary")
    val url = text("url")
    val imageUrl = text("image_url").nullable()
    val sourceName = text("source_name")
    val category = varchar("category", 32)
    val publishedAt = long("published_at").index()

    override val primaryKey = PrimaryKey(slug, language)
}

/**
 * Every notification that has been sent, which [PushLog] cannot answer.
 *
 * That table holds one row per topic and rewrites it on every send, because all
 * it is asked is when the quiet window started. The in-app inbox asks something
 * else — what a reader missed — and that is a list.
 *
 * Published rather than kept on the device for the same reason. A local copy
 * would be empty on a fresh install, would miss anything that arrived while the
 * app was force-stopped, and would be missing exactly the notifications a reader
 * had switched a tier off for — which is the one thing an inbox is for.
 */
object PushHistory : Table("push_history") {
    val topic = varchar("topic", 64)
    val sentAt = long("sent_at").index()
    val language = varchar("language", 8).index()
    val tier = varchar("tier", 16)
    val title = text("title")
    val body = text("body")
    /** Null for the reminder tier, which carries no article. */
    val deepLink = text("deep_link").nullable()

    override val primaryKey = PrimaryKey(topic, sentAt)
}

class ArticleStore(dbPath: String) {

    init {
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        // createMissingTablesAndColumns so a cached database from before a
        // column was added (CI restores it between runs) migrates in place.
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Articles, ArticleTexts, PushLog, FeedPages, FeedPlaced, FeedPageState,
                SharedArticles, PushHistory,
            )
        }
    }

    /**
     * The stored page layout for one feed, or [FeedLayout.EMPTY] the first time
     * a feed is published.
     *
     * Pages come back in reading order — head first, then descending page
     * number — which is the order [com.mk.newsshorts.server.feed.repaginate]
     * expects and produces.
     */
    fun feedLayout(feedKey: String): FeedLayout = transaction {
        val state = FeedPageState.selectAll().andWhere { FeedPageState.feedKey eq feedKey }.firstOrNull()
            ?: return@transaction FeedLayout.EMPTY

        val headPage = state[FeedPageState.headPage]
        val byPage = FeedPages.selectAll()
            .andWhere { FeedPages.feedKey eq feedKey }
            .map { Triple(it[FeedPages.page], it[FeedPages.position], it[FeedPages.articleId]) }
            .groupBy({ it.first }) { it.second to it.third }

        val stored = byPage.map { (page, entries) ->
            FeedPage(number = page, articleIds = entries.sortedBy { it.first }.map { it.second })
        }
        // A head page can legitimately hold nothing — a quiet feed that has just
        // sealed everything it had — and then it has no rows to be read back
        // from, so it is restored from the recorded number instead. Losing it
        // would restart numbering and hand an existing page number to different
        // articles.
        val pages = (stored + FeedPage(headPage, emptyList()).takeIf { stored.none { p -> p.number == headPage } })
            .filterNotNull()
            // The head is the newest page and so the highest number, and reading
            // order is the reverse of numbering: head first, oldest page last.
            .sortedByDescending { it.number }

        // Union with what is currently on a page, so a database written before
        // FeedPlaced existed does not read as a feed that has published nothing
        // and re-admit its whole current layout as new.
        val placed = FeedPlaced.selectAll()
            .andWhere { FeedPlaced.feedKey eq feedKey }
            .mapTo(HashSet()) { it[FeedPlaced.articleId] }
        placed += pages.flatMap { it.articleIds }

        FeedLayout(pages = pages, placedIds = placed)
    }

    fun saveFeedLayout(feedKey: String, layout: FeedLayout) {
        transaction {
            FeedPages.deleteWhere { FeedPages.feedKey eq feedKey }
            layout.pages.forEach { page ->
                page.articleIds.forEachIndexed { position, id ->
                    FeedPages.insert {
                        it[FeedPages.feedKey] = feedKey
                        it[articleId] = id
                        it[FeedPages.page] = page.number
                        it[FeedPages.position] = position
                    }
                }
            }
            // insertIgnore, not delete-then-write: the first time an id is
            // placed is the timestamp that should decide when the row expires,
            // and rewriting it every publish would keep the whole set alive
            // forever.
            val now = System.currentTimeMillis()
            layout.placedIds.forEach { id ->
                FeedPlaced.insertIgnore {
                    it[FeedPlaced.feedKey] = feedKey
                    it[articleId] = id
                    it[placedAt] = now
                }
            }
            FeedPageState.deleteWhere { FeedPageState.feedKey eq feedKey }
            FeedPageState.insert {
                it[FeedPageState.feedKey] = feedKey
                it[headPage] = layout.head?.number ?: 1
            }
        }
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

    /**
     * Adds one sent notification to the history the inbox is published from.
     *
     * Separate from [recordPush] rather than folded into it: that call is the
     * pacing record and has to stay one row per topic, and a send that fails
     * must leave neither behind.
     */
    fun recordPushHistory(
        topic: String,
        language: String,
        tier: String,
        title: String,
        body: String,
        deepLink: String?,
        sentAt: Long,
    ) {
        transaction {
            PushHistory.insertIgnore {
                it[PushHistory.topic] = topic
                it[PushHistory.sentAt] = sentAt
                it[PushHistory.language] = language
                it[PushHistory.tier] = tier
                it[PushHistory.title] = title
                it[PushHistory.body] = body
                it[PushHistory.deepLink] = deepLink
            }
        }
    }

    /**
     * One language's sent notifications, newest first.
     *
     * Rows with no article are left out: the reminder tier exists so a slot is
     * never wasted, but an inbox entry that opens nothing is a row a reader taps
     * once and never trusts again.
     */
    fun pushHistory(language: String, limit: Int): List<SentNotification> = transaction {
        PushHistory.selectAll()
            .andWhere { PushHistory.language eq language }
            .andWhere { PushHistory.deepLink.isNotNull() }
            .orderBy(PushHistory.sentAt, SortOrder.DESC)
            .limit(limit)
            .map {
                SentNotification(
                    sentAt = it[PushHistory.sentAt],
                    tier = it[PushHistory.tier],
                    title = it[PushHistory.title],
                    body = it[PushHistory.body],
                    deepLink = it[PushHistory.deepLink].orEmpty(),
                )
            }
    }

    /** Drops history sent before [cutoffMillis]. */
    fun prunePushHistory(cutoffMillis: Long): Int = transaction {
        PushHistory.deleteWhere { PushHistory.sentAt less cutoffMillis }
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
     * Records what a landing page needs, for every article being published.
     *
     * An upsert and not an insert: an article can reach a feed before the
     * summarizer has got to it, carrying the trimmed description as a stand-in.
     * The real summary lands a cycle or two later, and an insert-only archive
     * would keep the stand-in on the page for as long as the link lives.
     */
    fun archiveShared(articles: List<SharedArticle>) {
        if (articles.isEmpty()) return
        transaction {
            articles.forEach { article ->
                SharedArticles.upsert {
                    it[slug] = article.slug
                    it[language] = article.language
                    it[title] = article.title
                    it[summary] = article.summary
                    it[url] = article.url
                    it[imageUrl] = article.imageUrl
                    it[sourceName] = article.sourceName
                    it[category] = article.category
                    it[publishedAt] = article.publishedAt
                }
            }
        }
    }

    /**
     * The archive, newest first, capped at [limit].
     *
     * The cap is a publishing budget, not a storage one: every row here becomes
     * a file in the artifact CI uploads on every run, and the site is rebuilt
     * from scratch each time. Newest first because a link's chance of still
     * being opened falls off with the story's age.
     */
    fun sharedArticles(limit: Int): List<SharedArticle> = transaction {
        SharedArticles.selectAll()
            .orderBy(SharedArticles.publishedAt, SortOrder.DESC)
            .limit(limit)
            .map {
                SharedArticle(
                    slug = it[SharedArticles.slug],
                    language = it[SharedArticles.language],
                    title = it[SharedArticles.title],
                    summary = it[SharedArticles.summary],
                    url = it[SharedArticles.url],
                    imageUrl = it[SharedArticles.imageUrl],
                    sourceName = it[SharedArticles.sourceName],
                    category = it[SharedArticles.category],
                    publishedAt = it[SharedArticles.publishedAt],
                )
            }
    }

    /**
     * Drops archived articles published before [cutoffMillis], which is how a
     * shared link eventually stops working.
     *
     * Deliberately a much older cutoff than [prune] takes: the two answer
     * different questions. That one asks what is still worth putting in front of
     * a reader; this one asks how long a link somebody already sent should keep
     * opening something.
     */
    fun pruneShared(cutoffMillis: Long): Int = transaction {
        SharedArticles.deleteWhere { SharedArticles.publishedAt less cutoffMillis }
    }

    /**
     * Drops articles published before [cutoffMillis]. Without this the database
     * grows without bound and the render budget drifts toward stale articles.
     */
    fun prune(cutoffMillis: Long): Int = transaction {
        // Long after the article itself is gone. These rows are what stops a
        // republished or restored article being served as new, so they have to
        // outlive every copy of it — including one that comes back from a
        // database CI restored from an older run.
        FeedPlaced.deleteWhere { FeedPlaced.placedAt less (cutoffMillis - PLACED_GRACE_MILLIS) }

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
     *
     * [excludeCountryTagged] keeps a country's own sources out. The general
     * feed is what a reader sees before choosing anything, and a country feed
     * is what they see after choosing; publishing an article in both made the
     * Countries tab look like a copy of For You, which for an Egyptian reader
     * it largely was. Country sources publish far more often than the
     * international ones, so interleaving alone only spread the duplicates out.
     */
    fun feed(
        language: String?,
        category: String?,
        limit: Int,
        offset: Long,
        country: String? = null,
        diversifyBySource: Boolean = false,
        excludeCountryTagged: Boolean = false,
    ): Pair<List<FeedArticleDto>, Long> =
        transaction {
            fun base() = Articles
                .join(ArticleTexts, JoinType.INNER, onColumn = Articles.id, otherColumn = ArticleTexts.articleId)
                .selectAll()
                .also { query ->
                    language?.let { query.andWhere { ArticleTexts.language eq it } }
                    category?.let { query.andWhere { Articles.category eq it } }
                    country?.let { query.andWhere { Articles.country eq it } }
                    if (excludeCountryTagged) query.andWhere { Articles.country.isNull() }
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

        /** How long a [FeedPlaced] row outlives the article it names: 90 days. */
        const val PLACED_GRACE_MILLIS = 90L * 24 * 60 * 60 * 1000
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
