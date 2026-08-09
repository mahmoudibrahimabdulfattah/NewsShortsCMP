package org.example.newsshorts.server.summarize

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

/** One article awaiting a summary. */
data class SummaryInput(val id: Long, val title: String, val description: String?, val language: String)

interface Summarizer {
    /** Returns article id -> summary. Missing ids mean the summary failed. */
    suspend fun summarize(batch: List<SummaryInput>): Map<Long, String>
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

    override suspend fun summarize(batch: List<SummaryInput>): Map<Long, String> {
        if (batch.isEmpty()) return emptyMap()
        val languageName = if (batch.first().language == "ar") "Arabic" else "English"

        val prompt = buildString {
            appendLine(
                "You summarize news articles for a shorts-style news app. For EACH article below, " +
                    "write a neutral, factual summary of 50-70 words in $languageName. " +
                    "No opinions, no clickbait. " +
                    "Respond ONLY with a JSON array of objects: [{\"id\": <number>, \"summary\": \"<text>\"}]."
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

    private fun parseSummaries(responseBody: String): Map<Long, String> {
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
                id to summary
            }.toMap()
        } catch (e: Exception) {
            log.warn("Gemini JSON parse failed: ${e.message}")
            emptyMap()
        }
    }

    companion object {
        const val BATCH_SIZE = 10
    }
}

/** No AI: trims the RSS description to ~70 words. Keeps the pipeline alive in dev. */
class FallbackSummarizer : Summarizer {
    override suspend fun summarize(batch: List<SummaryInput>): Map<Long, String> =
        batch.mapNotNull { article ->
            val text = article.description?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
            val words = text.split(" ")
            val summary = if (words.size <= 70) text else words.take(70).joinToString(" ") + "…"
            article.id to summary
        }.toMap()
}

/** Primary summarizer with per-article fallback for anything it missed. */
class ChainedSummarizer(private val primary: Summarizer, private val fallback: Summarizer) : Summarizer {
    override suspend fun summarize(batch: List<SummaryInput>): Map<Long, String> {
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
