package com.mk.newsshorts.server.summarize

import com.mk.newsshorts.server.model.NewsCategories
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/** One article awaiting a category, identified the same way a summary is. */
data class ClassifyInput(val id: Long, val title: String, val description: String?)

interface Classifier {
    /** Returns article id -> category. A missing id means classification failed. */
    suspend fun classify(batch: List<ClassifyInput>): Map<Long, String>
}

/**
 * The category instruction both the summarizer and the standalone classifier
 * send, so an article cannot be filed one way while it is being written and
 * another way afterwards.
 *
 * It describes the article, never where it came from: a publisher's sports
 * section carrying a flood story is exactly the case this exists to catch.
 */
internal const val CATEGORY_INSTRUCTION =
    "Classify each article by what it is about, ignoring which feed it arrived in. " +
        "Choose from general, business, technology, science, health, sports, entertainment. " +
        "Give exactly one category. " +
        "Politics, war, crime, disasters, and world news are general. Use general whenever no " +
        "specialised category clearly fits — a wrong specialised tab is worse than general."

/**
 * Reads the category out of one object of a model response, or null when the
 * model returned something unusable. A one-element array is tolerated because
 * response shapes drift, but a longer one is refused rather than choosing an
 * arbitrary category. An unknown name invalidates the whole answer: a model
 * inventing a name has not understood the list.
 */
internal fun parseCategory(element: JsonElement?): String? {
    val category = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonArray -> (element.singleOrNull() as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
        else -> null
    }
    return category?.trim()?.lowercase()?.takeIf(NewsCategories.all::contains)
}

/**
 * Classifies articles that already have their text.
 *
 * Kept apart from [GeminiSummarizer] because the alternative is re-summarizing
 * an article that already reads well just to learn its category — the answer is
 * a single word, so the request carries no summary tokens and holds
 * [BATCH_SIZE] articles rather than twenty.
 */
class GeminiClassifier(
    private val apiKey: String,
    private val model: String = System.getenv("GEMINI_MODEL") ?: "gemini-flash-lite-latest",
) : Classifier {

    private val log = LoggerFactory.getLogger(GeminiClassifier::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp)

    override suspend fun classify(batch: List<ClassifyInput>): Map<Long, String> {
        if (batch.isEmpty()) return emptyMap()

        val prompt = buildString {
            appendLine(
                "$CATEGORY_INSTRUCTION Respond ONLY with a JSON array of objects: " +
                    "[{\"id\": <number>, \"category\": \"<category>\"}]."
            )
            batch.forEach { article ->
                appendLine()
                appendLine("Article id=${article.id}")
                appendLine("Title: ${article.title}")
                article.description?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("Content: ${it.take(300)}") }
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
                put("temperature", 0.0)
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
                log.warn("Gemini classify failed: ${response.status} ${response.bodyAsText().take(300)}")
                return emptyMap()
            }
            parseClassifications(response.bodyAsText())
        } catch (e: Exception) {
            log.warn("Gemini classify error: ${e.message}")
            emptyMap()
        }
    }

    private fun parseClassifications(responseBody: String): Map<Long, String> {
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
                val category = parseCategory(obj["category"]) ?: return@mapNotNull null
                id to category
            }.toMap()
        } catch (e: Exception) {
            log.warn("Gemini classify parse failed: ${e.message}")
            emptyMap()
        }
    }

    companion object {
        /**
         * Articles per request. Far larger than the summarizer's batch because
         * the answer is one word an article: the request is bounded by how much
         * evidence fits, not by how much prose stays coherent.
         */
        const val BATCH_SIZE = 100
    }
}

/**
 * No AI: leaves every article for the feed it arrived in to describe. Keeps the
 * pipeline running in dev, where an unclassified article stays in General.
 */
object NoClassifier : Classifier {
    override suspend fun classify(batch: List<ClassifyInput>): Map<Long, String> = emptyMap()
}

fun buildClassifier(): Classifier {
    val log = LoggerFactory.getLogger("Classifier")
    val geminiKey = System.getenv("GEMINI_API_KEY")
    return if (geminiKey.isNullOrBlank()) {
        log.warn("GEMINI_API_KEY not set — articles stay in General until a key is configured")
        NoClassifier
    } else {
        GeminiClassifier(geminiKey)
    }
}
