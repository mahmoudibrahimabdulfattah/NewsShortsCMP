package com.mk.newsshorts.server

import com.mk.newsshorts.core.contract.feed.FeedResponse
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.feed.FeedLayout
import com.mk.newsshorts.server.feed.FeedPageNames
import com.mk.newsshorts.server.feed.repaginate
import com.mk.newsshorts.server.ingest.IngestionPipeline
import com.mk.newsshorts.server.ingest.RssFetcher
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.buildClassifier
import com.mk.newsshorts.server.summarize.buildSummarizer
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val dbPath = System.getenv("DB_PATH") ?: "news.db"

    // `--generate-static <dir>` runs one ingestion cycle and exits, writing the
    // feed as JSON files (used by CI to publish to GitHub Pages).
    val staticIndex = args.indexOf("--generate-static")
    if (staticIndex >= 0) {
        val outputDir = args.getOrNull(staticIndex + 1)
            ?: error("--generate-static requires an output directory")
        StaticFeedGenerator.generate(java.io.File(outputDir), dbPath)
        return
    }

    val port = (System.getenv("PORT") ?: "8080").toInt()
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/** The `-p{n}` a later page's file name ends with. */
private val PAGE_SUFFIX = Regex("-p(\\d+)$")

fun Application.module() {
    val store = ArticleStore(System.getenv("DB_PATH") ?: "news.db")
    val pipeline = IngestionPipeline(store, RssFetcher(), buildSummarizer(), buildClassifier())
    pipeline.start(this)

    install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
    install(CallLogging)
    install(CORS) { anyHost() }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "internal error")))
        }
    }

    routing {
        get("/health") { call.respondText("ok") }

        get("/v1/meta") {
            call.respond(
                mapOf(
                    "languages" to FeedCatalog.languages.toList(),
                    "categories" to FeedCatalog.categories.toList(),
                    "countries" to FeedCatalog.countries.toList(),
                    "generatedAt" to System.currentTimeMillis(),
                )
            )
        }

        get("/v1/feed") {
            val language = call.request.queryParameters["lang"]
            val category = call.request.queryParameters["category"]
            val country = call.request.queryParameters["country"]
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0)

            // Same mix the published files get, so local development sees the
            // feed the app will actually be served.
            val (articles, total) =
                store.feed(language, category, limit, offset, country, diversifyBySource = true)
            call.respond(FeedResponse(articles = articles, total = total))
        }

        // Same paths the static (GitHub Pages) publish serves, so one client
        // config works against either backend — including the `-p{n}` page
        // files the app reaches by following `nextPage`.
        get("/v1/feed/{name}.json") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.NotFound)
            val requestedPage = PAGE_SUFFIX.find(name)?.groupValues?.get(1)?.toIntOrNull()
            val feedKey = PAGE_SUFFIX.replace(name, "")

            val (articles, total) = when {
                feedKey.startsWith("country-") -> {
                    val (country, language) = feedKey.removePrefix("country-").split("-", limit = 2)
                    store.feed(
                        language, null, StaticFeedGenerator.MAX_FEED_ARTICLES, 0,
                        country = country, diversifyBySource = true,
                    )
                }
                "-" in feedKey -> {
                    val (language, category) = feedKey.split("-", limit = 2)
                    store.feed(
                        language, category, StaticFeedGenerator.MAX_FEED_ARTICLES, 0,
                        diversifyBySource = true,
                    )
                }
                else -> store.feed(
                    feedKey, null, StaticFeedGenerator.MAX_FEED_ARTICLES, 0, diversifyBySource = true,
                )
            }

            // Paged from scratch on every request rather than from the stored
            // layout: a development server has no publish history to be
            // consistent with, and this way the pages it serves are exactly
            // what a first publish of the same articles would produce.
            val layout = repaginate(FeedLayout.EMPTY, articles.map { it.id }, StaticFeedGenerator.PAGE_SIZE)
            val index = if (requestedPage == null) 0 else layout.pages.indexOfFirst { it.number == requestedPage }
            if (index < 0) return@get call.respond(HttpStatusCode.NotFound)

            val byId = articles.associateBy { it.id }
            call.respond(
                FeedResponse(
                    articles = layout.pages[index].articleIds.mapNotNull(byId::get),
                    total = total,
                    nextPage = layout.pages.getOrNull(index + 1)
                        ?.let { FeedPageNames.fileFor(feedKey, layout, index + 1) },
                )
            )
        }
    }
}
