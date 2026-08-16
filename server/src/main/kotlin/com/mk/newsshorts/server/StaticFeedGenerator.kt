package com.mk.newsshorts.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.feed.FeedPageNames
import com.mk.newsshorts.server.feed.repaginate
import com.mk.newsshorts.server.ingest.IngestionPipeline
import com.mk.newsshorts.server.ingest.RssFetcher
import com.mk.newsshorts.server.model.FeedResponse
import com.mk.newsshorts.server.push.BreakingNewsPusher
import com.mk.newsshorts.server.push.PushNotifier
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
        FeedCatalog.languages.forEach { language ->
            filesWritten += writeSearchIndex(searchDir, store, language)
        }

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

        if (writeSharePage(outputDir)) filesWritten++

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
     * The page a shared link lands on. It hands off to the app when installed
     * and otherwise shows the story with a link to the store and the source, so
     * a recipient without the app still gets something useful.
     *
     * Returns whether it was written. A missing template is reported and skipped
     * rather than thrown: publishing the feed is this job's purpose, and the
     * share page is an extra that must not be able to stop ingestion, generation
     * and notifications.
     */
    private fun writeSharePage(outputDir: File): Boolean {
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
     * Writes one language's search corpus and returns how many files it wrote.
     *
     * Everything published in that language in one file, newest first, with no
     * category or country filter — a reader searching does not care which tab a
     * story would have appeared under. Country articles are included because
     * [ArticleStore.feed] only filters on country when asked to.
     *
     * Not interleaved by source, unlike a feed: the mix exists so no publisher
     * owns the top of a feed nobody asked for, and a search result list is
     * ordered by how well it matches, which the app decides.
     *
     * The shape is [FeedResponse] with no `nextPage`, so the app parses it with
     * the same code that reads a feed page — one file, and the feed's own
     * mapper and article model all the way through.
     */
    private fun writeSearchIndex(searchDir: File, store: ArticleStore, language: String): Int {
        val (articles, total) = store.feed(
            language = language, category = null,
            limit = SEARCH_INDEX_ARTICLES, offset = 0, country = null,
            diversifyBySource = false,
        )
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
