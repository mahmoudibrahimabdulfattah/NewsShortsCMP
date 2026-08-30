package com.mk.newsshorts.server.summarize

import com.mk.newsshorts.server.store.TextSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * One article awaiting rendering. [targetLanguage] is the language the reader
 * will see, which is not always the language the article was published in.
 */
data class SummaryInput(
    val id: Long,
    val title: String,
    val description: String?,
    val targetLanguage: String,
)

/** An article's title and summary in the requested language. */
data class SummaryOutput(
    val title: String,
    val summary: String,
    val source: TextSource,
    val category: String? = null,
)

interface Summarizer {
    /** Returns article id -> rendered text. Missing ids mean rendering failed. */
    suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput>
}

/**
 * Summarizes with the Gemini API free tier. Batches up to [BATCH_SIZE] articles
 * per request to stay well inside the free daily quota (~1,000-1,500 req/day).
 *
 * Env: GEMINI_API_KEY (required for AI summaries), GEMINI_MODEL (default gemini-2.5-flash-lite).
 * Without a key, [FallbackSummarizer] behavior applies via [withFallback].
 */
class GeminiSummarizer(
    private val apiKey: String,
    private val model: String = System.getenv("GEMINI_MODEL") ?: "gemini-flash-lite-latest",
) : Summarizer {

    private val log = LoggerFactory.getLogger(GeminiSummarizer::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp)

    override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> {
        if (batch.isEmpty()) return emptyMap()
        val languageName = languageName(batch.first().targetLanguage)

        val prompt = buildString {
            appendLine(
                "You prepare news articles for a shorts-style news app. For EACH article below, " +
                    "write its headline and a neutral, factual summary of 50-70 words, both in " +
                    "$languageName. Translate them if the article is in another language. " +
                    "Keep the headline under 15 words. No opinions, no clickbait. " +
                    CATEGORY_INSTRUCTION + " " +
                    "Respond ONLY with a JSON array of objects: " +
                    "[{\"id\": <number>, \"title\": \"<text>\", \"summary\": \"<text>\", " +
                    "\"category\": \"<category>\"}]."
            )
            batch.forEach { article ->
                appendLine()
                appendLine("Article id=${article.id}")
                appendLine("Title: ${article.title}")
                article.description?.takeIf { it.isNotBlank() }?.let { appendLine("Content: ${it.take(1500)}") }
            }
        }

        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                })
            })
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            }
        }

        return try {
            val response = http.post(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            ) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) {
                log.warn("Gemini request failed: ${response.status} ${response.bodyAsText().take(300)}")
                return emptyMap()
            }
            parseSummaries(response.bodyAsText())
        } catch (e: Exception) {
            log.warn("Gemini request error: ${e.message}")
            emptyMap()
        }
    }

    private fun parseSummaries(responseBody: String): Map<Long, SummaryOutput> {
        val text = json.parseToJsonElement(responseBody).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: return emptyMap()

        return try {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                val summary = obj["summary"]?.jsonPrimitive?.content?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.content?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val category = parseCategory(obj["category"])
                id to SummaryOutput(
                    title = title,
                    summary = summary,
                    source = TextSource.AI,
                    category = category,
                )
            }.toMap()
        } catch (e: Exception) {
            log.warn("Gemini JSON parse failed: ${e.message}")
            emptyMap()
        }
    }

    companion object {
        /**
         * Articles per request. The free tier caps requests per day, not tokens
         * per request, so a larger batch buys throughput at no cost — the limit
         * is how much output stays reliable in one response.
         */
        const val BATCH_SIZE = 20

        fun languageName(code: String): String = when (code) {
            "ar" -> "Arabic"
            else -> "English"
        }
    }
}

/**
 * No AI: trims the RSS description to ~70 words and keeps the original title.
 * Keeps the pipeline alive in dev, but cannot translate — so it only applies
 * when the article is already in the target language.
 */
class FallbackSummarizer : Summarizer {
    override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> =
        batch.mapNotNull { article ->
            val text = article.description?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
            val words = text.split(" ")
            val summary = if (words.size <= 70) text else words.take(70).joinToString(" ") + "…"
            article.id to SummaryOutput(title = article.title, summary = summary, source = TextSource.FALLBACK)
        }.toMap()
}

/** Primary summarizer with per-article fallback for anything it missed. */
class ChainedSummarizer(private val primary: Summarizer, private val fallback: Summarizer) : Summarizer {
    override suspend fun summarize(batch: List<SummaryInput>): Map<Long, SummaryOutput> {
        val fromPrimary = primary.summarize(batch)
        val missing = batch.filter { it.id !in fromPrimary }
        if (missing.isEmpty()) return fromPrimary
        return fromPrimary + fallback.summarize(missing)
    }
}

fun buildSummarizer(): Summarizer {
    val log = LoggerFactory.getLogger("Summarizer")
    val geminiKey = System.getenv("GEMINI_API_KEY")
    return if (geminiKey.isNullOrBlank()) {
        log.warn("GEMINI_API_KEY not set — using trimmed-description fallback, no AI summaries")
        FallbackSummarizer()
    } else {
        log.info("Using Gemini summarizer")
        ChainedSummarizer(GeminiSummarizer(geminiKey), FallbackSummarizer())
    }
}
