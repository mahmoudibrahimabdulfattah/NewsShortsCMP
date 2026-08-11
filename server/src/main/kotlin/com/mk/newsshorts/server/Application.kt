package com.mk.newsshorts.server

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
import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.ingest.IngestionPipeline
import com.mk.newsshorts.server.ingest.RssFetcher
import com.mk.newsshorts.server.model.FeedResponse
import com.mk.newsshorts.server.store.ArticleStore
import com.mk.newsshorts.server.summarize.buildSummarizer

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

fun Application.module() {
    val store = ArticleStore(System.getenv("DB_PATH") ?: "news.db")
    val pipeline = IngestionPipeline(store, RssFetcher(), buildSummarizer())
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
                )
            )
        }

        get("/v1/feed") {
            val language = call.request.queryParameters["lang"]
            val category = call.request.queryParameters["category"]
            val country = call.request.queryParameters["country"]
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0)

            val (articles, total) = store.feed(language, category, limit, offset, country)
            call.respond(FeedResponse(articles = articles, total = total))
        }

        // Same paths the static (GitHub Pages) publish serves, so one client
        // config works against either backend.
        get("/v1/feed/{name}.json") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.NotFound)
            val (articles, total) = when {
                name.startsWith("country-") -> {
                    val (country, language) = name.removePrefix("country-").split("-", limit = 2)
                    store.feed(language, null, 100, 0, country = country)
                }
                "-" in name -> {
                    val (language, category) = name.split("-", limit = 2)
                    store.feed(language, category, 100, 0)
                }
                else -> store.feed(name, null, 100, 0)
            }
            call.respond(FeedResponse(articles = articles, total = total))
        }
    }
}
