package com.mk.newsshorts.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.feed.FeedPageNames
import com.mk.newsshorts.server.feed.repaginate
import com.mk.newsshorts.server.ingest.IngestionPipeline
import com.mk.newsshorts.server.ingest.RssFetcher
import com.mk.newsshorts.server.model.FeedArticleDto
import com.mk.newsshorts.server.model.FeedResponse
import com.mk.newsshorts.server.push.BreakingNewsPusher
import com.mk.newsshorts.server.push.PushNotifier
import com.mk.newsshorts.server.share.SharePage
import com.mk.newsshorts.server.share.ShareSlug
import com.mk.newsshorts.server.share.SharedArticle
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.buildSummarizer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Runs one ingestion cycle and writes the feed as static JSON files.
 *
 * This is what CI publishes to GitHub Pages: the feed is read-only and
 * refreshed on a schedule, so a CDN serves it better (and cheaper) than a
 * long-running server. The Ktor server in [main] stays for local development.
 *
 * Layout mirrors the live API's query parameters:
 *   v1/feed/{lang}.json              — all categories, first page
 *   v1/feed/{lang}-{category}.json   — one category, first page
 *   v1/feed/country-{code}-{lang}.json — one country, in one language
 *   v1/feed/{name}-p{n}.json         — a later page of {name}, reached by
 *                                      following `nextPage` from the one before
 *   v1/search/{lang}.json            — everything published in one language, in
 *                                      one file, for the app to search offline
 *   v1/meta.json                     — available languages, categories, countries
 *   a/{lang}/{slug}/index.html       — one shared article's landing page
 *   404.html                         — what a link older than the archive gets
 */
object StaticFeedGenerator {

    private val log = LoggerFactory.getLogger(StaticFeedGenerator::class.java)
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    /**
     * Articles per file. Small enough that a cold start is one quick request
     * rather than the whole feed, and the rest arrives while the reader is
     * still on the first few cards.
     */
    const val PAGE_SIZE = 40

    /**
     * How deep the published feed goes in total. Beyond this a feed stops
     * having more pages; in practice retention (four days) usually gets there
     * first.
     */
    const val MAX_FEED_ARTICLES = 400

    /**
     * How many articles per language the published search corpus holds.
     *
     * A CDN serving static files has no query API, so search is answered on the
     * device against a corpus published here — see the `v1/search` layout above.
     * That makes the number a download budget rather than a database limit: at
     * this size the file is a few hundred kilobytes uncompressed and well under
     * a hundred gzipped, which is what a reader pays once, on their first
     * search of a session.
     *
     * Deliberately deeper than [MAX_FEED_ARTICLES]: a reader searching is
     * looking for a story they remember, which is exactly the story that has
     * already fallen off the end of the feed.
     */
    const val SEARCH_INDEX_ARTICLES = 800

    /**
     * How long a shared link keeps opening the story it was sent for.
     *
     * Far longer than the feed's own retention, which is a week: a feed answers
     * what is worth reading now, and a link in someone's chat is a promise made
     * to whoever sent it. Ninety days is the same horizon [FeedPlaced] rows
     * already keep, for the same reason — the thing outlives the article.
     */
    private const val DEFAULT_SHARE_RETENTION_DAYS = 90

    /**
     * The real limit on the archive, and the one worth watching.
     *
     * Every archived row becomes a file in the artifact CI uploads, and the
     * whole site is rebuilt and redeployed every half hour — so this is a
     * publishing budget, not a storage one, and it is what really decides how
     * long a shared link lives. [DEFAULT_SHARE_RETENTION_DAYS] never binds
     * first at the volume the feed runs today.
     *
     * The arithmetic behind the default, so it can be re-done rather than
     * guessed at: the feed publishes about three thousand articles a day across
     * both languages, and a page measures around 3.9 KB — 4.6 KB in Arabic,
     * where UTF-8 costs two bytes a character and the hand-off link percent-
     * encodes them to nine. Forty thousand pages is then roughly a fortnight of
     * cover and 155 MB of site, against Pages' 1 GB. Below about twenty-five
     * thousand the archive stops outliving the feed's own week, which is the
     * only reason it exists. Raise it once a run's timing shows what the deploy
     * can carry.
     */
    private const val DEFAULT_MAX_SHARE_PAGES = 40_000

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    fun generate(outputDir: File, dbPath: String) = runBlocking {
        val store = ArticleStore(dbPath)
        IngestionPipeline(store, RssFetcher(), buildSummarizer()).runCycle()

        val feedDir = File(outputDir, "v1/feed").apply { mkdirs() }
        var filesWritten = 0

        FeedCatalog.languages.forEach { language ->
            filesWritten += write(feedDir, store, feedKey = language, language = language, category = null)

            FeedCatalog.categories.forEach { category ->
                filesWritten += write(
                    feedDir, store,
                    feedKey = "$language-$category", language = language, category = category,
                )
            }
        }

        FeedCatalog.countries.forEach { country ->
            FeedCatalog.countryLanguages.forEach { language ->
                filesWritten += write(
                    feedDir, store,
                    feedKey = "country-$country-$language",
                    language = language, category = null, country = country,
                )
            }
        }

        val searchDir = File(outputDir, "v1/search").apply { mkdirs() }
        val shareable = mutableListOf<SharedArticle>()
        FeedCatalog.languages.forEach { language ->
            val (articles, total) = store.feed(
                language = language, category = null,
                limit = SEARCH_INDEX_ARTICLES, offset = 0, country = null,
                diversifyBySource = false,
            )
            filesWritten += writeSearchIndex(searchDir, language, articles, total)
            // The same read serves both: the search corpus is already the
            // widest published set in a language, and an article nobody can
            // reach is an article nobody can share.
            shareable += articles.map { article ->
                SharedArticle(
                    slug = ShareSlug.of(article.url),
                    language = article.language,
                    title = article.title,
                    summary = article.summary,
                    url = article.url,
                    imageUrl = article.imageUrl,
                    sourceName = article.sourceName,
                    category = article.category,
                    publishedAt = article.publishedAt,
                )
            }
        }
        store.archiveShared(shareable)

        File(outputDir, "v1/meta.json").writeText(
            json.encodeToString(
                MetaResponse(
                    languages = FeedCatalog.languages.toList(),
                    categories = FeedCatalog.categories.toList(),
                    countries = FeedCatalog.countries.toList(),
                )
            )
        )
        filesWritten++

        writeAppConfig(outputDir)
        filesWritten++

        if (writeLegacySharePage(outputDir)) filesWritten++
        filesWritten += writeSharePages(outputDir, store)

        // Without this, GitHub Pages runs the output through Jekyll.
        File(outputDir, ".nojekyll").writeText("")

        log.info("Wrote $filesWritten JSON files to ${outputDir.absolutePath}")

        // After publishing, not before: a notification should never point at a
        // story the feed has not caught up with yet.
        // fromEnvironment reports why it declined, so this only notes the effect.
        val notifier = PushNotifier.fromEnvironment()
        if (notifier == null) log.info("Push is not configured — skipping")
        else BreakingNewsPusher(store, notifier).run()
    }

    /**
     * The kill switch for old builds.
     *
     * A published app cannot be recalled: an installed build keeps running its
     * own code against this feed forever. When a released version becomes
     * unusable — a breaking change to the feed shape, a bug that corrupts saved
     * data — raising MIN_SUPPORTED_VERSION_CODE is the only way to stop it, so
     * the number lives here rather than in the app.
     *
     * Defaults keep every build supported, which is what it should say until
     * there is a reason otherwise.
     */
    private fun writeAppConfig(outputDir: File) {
        val storeUrl = System.getenv("PLAY_STORE_URL").orEmpty()
        val config = AppConfigResponse(
            minSupportedVersionCode = envInt("MIN_SUPPORTED_VERSION_CODE", default = 1),
            latestVersionCode = envInt("LATEST_VERSION_CODE", default = 1),
            storeUrl = storeUrl,
            rootPolicy = System.getenv("ROOT_POLICY")?.takeUnless { it.isBlank() }?.trim() ?: "warn",
            emulatorPolicy = System.getenv("EMULATOR_POLICY")?.takeUnless { it.isBlank() }?.trim() ?: "block",
        )
        if (config.minSupportedVersionCode > 1) {
            log.info("Builds below versionCode ${config.minSupportedVersionCode} are now blocked")
        }
        File(outputDir, "v1/app.json").writeText(json.encodeToString(config))
    }

    /**
     * The number a feed's first page takes when there is no stored layout.
     *
     * The layout lives in the article database, which CI restores from a cache
     * — best-effort by definition. If it is ever lost, every feed looks new and
     * numbering would restart at 1, republishing `-p1.json` with a different set
     * of articles under a name readers are already holding a link to. The run
     * number never repeats, so a rebuilt layout takes names no earlier publish
     * has used, and a reader following a stale link gets a 404 that the app
     * already reads as the end of the feed.
     *
     * Falls back to 1 locally, where there is no run number and no published
     * history to collide with.
     */
    private fun firstPageNumber(): Int =
        System.getenv("GITHUB_RUN_NUMBER")?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 1

    /** A malformed value would silently lock every reader out, so it is reported. */
    private fun envInt(name: String, default: Int): Int {
        val raw = System.getenv(name)?.takeUnless { it.isBlank() } ?: return default
        return raw.trim().toIntOrNull() ?: run {
            log.warn("$name is set to '$raw', which is not a number — using $default")
            default
        }
    }

    /**
     * The landing page builds released before per-article pages existed still
     * link to: one page at `a/`, carrying the whole article in its query string
     * and drawing it on load.
     *
     * Those builds are installed and cannot be recalled, and every link they
     * have already produced points here — so this stays published even though
     * nothing new points at it. It is also the reason those links never
     * unfurled: a crawler runs no script and saw an empty document.
     *
     * Returns whether it was written. A missing template is reported and skipped
     * rather than thrown: publishing the feed is this job's purpose, and the
     * share page is an extra that must not be able to stop ingestion, generation
     * and notifications.
     */
    private fun writeLegacySharePage(outputDir: File): Boolean {
        val template = javaClass.getResourceAsStream("/share.html")
            ?.bufferedReader()?.readText()
        if (template == null) {
            log.warn("share.html is missing from server resources — shared links will 404")
            return false
        }
        // Empty until the app is published; the page then hides the store button.
        val storeUrl = System.getenv("PLAY_STORE_URL").orEmpty()
        File(outputDir, "a").apply { mkdirs() }
            .resolve("index.html")
            .writeText(template.replace("__PLAY_STORE_URL__", storeUrl))
        return true
    }

    /**
     * Writes a landing page for every archived article, and returns the file
     * count.
     *
     * One file per article per language, because the preview card a messaging
     * app draws is built from tags in the document it fetches, and none of those
     * crawlers run scripts. There is no way to serve a per-article `<head>` from
     * one page on a static host.
     *
     * Pruning happens here rather than during ingestion so that the archive's
     * lifetime is decided in the same place as the publishing budget that
     * actually governs it.
     */
    private fun writeSharePages(outputDir: File, store: ArticleStore): Int {
        val retentionDays = envInt("SHARE_PAGE_RETENTION_DAYS", DEFAULT_SHARE_RETENTION_DAYS)
        val dropped = store.pruneShared(System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY)
        if (dropped > 0) log.info("Dropped $dropped share pages older than $retentionDays days")

        val siteBaseUrl = siteBaseUrl()
        val storeUrl = System.getenv("PLAY_STORE_URL").orEmpty()
        val pages = store.sharedArticles(envInt("MAX_SHARE_PAGES", DEFAULT_MAX_SHARE_PAGES))

        var bytes = 0L
        pages.forEach { article ->
            val html = SharePage.render(article, siteBaseUrl, storeUrl)
            bytes += html.length
            File(outputDir, SharePage.pathFor(article))
                .apply { parentFile.mkdirs() }
                .writeText(html)
        }

        // One stylesheet and one script for all of them. Inlined, they would be
        // repeated as many times as the archive is deep.
        File(outputDir, SharePage.STYLESHEET_PATH)
            .apply { parentFile.mkdirs() }
            .writeText(SharePage.stylesheet())
        File(outputDir, SharePage.SCRIPT_PATH)
            .apply { parentFile.mkdirs() }
            .writeText(SharePage.script())
        File(outputDir, "404.html").writeText(SharePage.notFound(siteBaseUrl, storeUrl))

        // Logged every run because this is the number that decides how long a
        // deploy takes, and it is invisible until it hurts.
        log.info("Wrote ${pages.size} share pages, ${bytes / 1024} KiB of HTML")
        return pages.size + 3
    }

    /**
     * The site's own address, which the pages need in absolute form — a preview
     * card's `og:url` and `og:image` are fetched by a crawler that has no page
     * to resolve a relative path against.
     *
     * The default matches the app's `SHARE_BASE_URL` default, so a local run
     * produces the same links CI does.
     */
    private fun siteBaseUrl(): String =
        System.getenv("SITE_BASE_URL")?.takeUnless { it.isBlank() }?.trim()?.trimEnd('/')
            ?: "https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP"

    /**
     * Writes one language's search corpus and returns how many files it wrote.
     *
     * Everything published in that language in one file, newest first, with no
     * category or country filter — a reader searching does not care which tab a
     * story would have appeared under. Country articles are included because
     * [ArticleStore.feed] only filters on country when asked to.
     *
     * Handed the rows rather than reading them, because the same read is what
     * the share archive is built from and it is the widest query in the run.
     *
     * Not interleaved by source, unlike a feed: the mix exists so no publisher
     * owns the top of a feed nobody asked for, and a search result list is
     * ordered by how well it matches, which the app decides.
     *
     * The shape is [FeedResponse] with no `nextPage`, so the app parses it with
     * the same code that reads a feed page — one file, and the feed's own
     * mapper and article model all the way through.
     */
    private fun writeSearchIndex(
        searchDir: File,
        language: String,
        articles: List<FeedArticleDto>,
        total: Long,
    ): Int {
        File(searchDir, "$language.json").writeText(
            json.encodeToString(FeedResponse(articles = articles, total = total, nextPage = null))
        )
        return 1
    }

    /**
     * Writes one feed as a chain of page files and returns how many it wrote.
     *
     * The whole depth is read and interleaved in one go before it is split, so
     * the publisher mix is right *across* a page boundary and not only inside
     * one page — mixing each page separately would hand the first slots of
     * every page to whoever publishes most often.
     *
     * The page split itself comes from [repaginate], which keeps already-sealed
     * pages exactly as they were so a reader half way down the feed is not
     * reading a boundary that moved under them since they started.
     */
    private fun write(
        feedDir: File,
        store: ArticleStore,
        feedKey: String,
        language: String,
        category: String?,
        country: String? = null,
    ): Int {
        val (articles, total) = store.feed(
            language = language, category = category,
            limit = MAX_FEED_ARTICLES, offset = 0, country = country,
            diversifyBySource = true,
            // A country's sources belong to that country's feed and nowhere
            // else. Without this every Egyptian daily also filled For You, and
            // the two tabs served the same stories in nearly the same order.
            excludeCountryTagged = country == null,
        )

        val layout = repaginate(
            previous = store.feedLayout(feedKey),
            order = articles.map { it.id },
            pageSize = PAGE_SIZE,
            firstNumber = firstPageNumber(),
        )
        store.saveFeedLayout(feedKey, layout)

        val byId = articles.associateBy { it.id }
        layout.pages.forEachIndexed { index, page ->
            val next = layout.pages.getOrNull(index + 1)
                ?.let { FeedPageNames.fileFor(feedKey, layout, index + 1) }
            val body = FeedResponse(
                articles = page.articleIds.mapNotNull(byId::get),
                total = total,
                nextPage = next,
            )
            File(feedDir, FeedPageNames.fileFor(feedKey, layout, index))
                .writeText(json.encodeToString(body))
        }
        return layout.pages.size
    }
}

/**
 * [storeUrl] is empty until the app is on Play. The app treats an empty value as
 * "no update available", so a forced update can never leave a reader with a
 * blocking screen and nowhere to go.
 */
@kotlinx.serialization.Serializable
private data class AppConfigResponse(
    val minSupportedVersionCode: Int,
    val latestVersionCode: Int,
    val storeUrl: String,
    /**
     * What a rooted or repackaged install should do: allow, warn, or block.
     * Served rather than compiled in because the right answer depends on who
     * turns out to be installing the app, which is not knowable before launch.
     */
    val rootPolicy: String,
    /** Same values, for emulators and developer-mode devices. */
    val emulatorPolicy: String,
)

@kotlinx.serialization.Serializable
private data class MetaResponse(
    val languages: List<String>,
    val categories: List<String>,
    val countries: List<String>,
)
