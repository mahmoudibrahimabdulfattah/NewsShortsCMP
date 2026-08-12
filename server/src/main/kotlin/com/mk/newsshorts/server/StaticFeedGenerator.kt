package com.mk.newsshorts.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import com.mk.newsshorts.server.config.FeedCatalog
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
 *   v1/feed/{lang}.json              — all categories
 *   v1/feed/{lang}-{category}.json   — one category
 *   v1/feed/country-{code}-{lang}.json — one country, in one language
 *   v1/meta.json                     — available languages, categories, countries
 */
object StaticFeedGenerator {

    private val log = LoggerFactory.getLogger(StaticFeedGenerator::class.java)
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    // Deep enough that a reader reaches the end of a session, not the end of
    // the feed. One file keeps it a single request on the client.
    private const val ARTICLES_PER_FILE = 200

    fun generate(outputDir: File, dbPath: String) = runBlocking {
        val store = ArticleStore(dbPath)
        IngestionPipeline(store, RssFetcher(), buildSummarizer()).runCycle()

        val feedDir = File(outputDir, "v1/feed").apply { mkdirs() }
        var filesWritten = 0

        FeedCatalog.languages.forEach { language ->
            write(File(feedDir, "$language.json"), store, language, category = null)
            filesWritten++

            FeedCatalog.categories.forEach { category ->
                write(File(feedDir, "$language-$category.json"), store, language, category)
                filesWritten++
            }
        }

        FeedCatalog.countries.forEach { country ->
            FeedCatalog.countryLanguages.forEach { language ->
                val (articles, total) = store.feed(
                    language = language, category = null,
                    limit = ARTICLES_PER_FILE, offset = 0, country = country,
                )
                File(feedDir, "country-$country-$language.json")
                    .writeText(json.encodeToString(FeedResponse(articles = articles, total = total)))
                filesWritten++
            }
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
        )
        if (config.minSupportedVersionCode > 1) {
            log.info("Builds below versionCode ${config.minSupportedVersionCode} are now blocked")
        }
        File(outputDir, "v1/app.json").writeText(json.encodeToString(config))
    }

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

    private fun write(target: File, store: ArticleStore, language: String, category: String?) {
        val (articles, total) = store.feed(language, category, ARTICLES_PER_FILE, offset = 0)
        target.writeText(json.encodeToString(FeedResponse(articles = articles, total = total)))
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
)

@kotlinx.serialization.Serializable
private data class MetaResponse(
    val languages: List<String>,
    val categories: List<String>,
    val countries: List<String>,
)
