package org.example.newsshorts.server.push

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.example.newsshorts.server.model.FeedArticleDto
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Sends one breaking-news push per language topic.
 *
 * Authentication uses the service account in FIREBASE_SERVICE_ACCOUNT (the
 * JSON key, or a path to it). The JWT is assembled by hand rather than pulling
 * in the Google auth library: it is one signature, and the server image stays
 * small.
 */
class PushNotifier(serviceAccountJson: String) : Notifier {

    private val log = LoggerFactory.getLogger(PushNotifier::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp)

    private val account = json.parseToJsonElement(serviceAccountJson).jsonObject
    private val projectId = account.getValue("project_id").jsonPrimitive.content
    private val clientEmail = account.getValue("client_email").jsonPrimitive.content
    private val privateKeyPem = account.getValue("private_key").jsonPrimitive.content

    override suspend fun send(topic: String, article: FeedArticleDto): Boolean {
        val token = accessToken() ?: return false
        val body = buildJsonObject {
            putJsonObject("message") {
                put("topic", topic)
                // Data-only: the app builds the notification so it controls the
                // channel and the tap target even when backgrounded.
                putJsonObject("data") {
                    put("title", article.title)
                    put("body", article.summary.take(160))
                    // Kept for clients installed before deepLink existed.
                    put("url", article.url)
                    put("deepLink", ArticleDeepLinks.build(article))
                }
                putJsonObject("android") { put("priority", "high") }
            }
        }

        return runCatching {
            val response = http.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (response.status.isSuccess()) {
                log.info("Pushed to $topic: ${article.title.take(60)}")
                true
            } else {
                log.warn("Push to $topic failed: ${response.status} ${response.bodyAsText().take(300)}")
                false
            }
        }.getOrElse {
            log.warn("Push to $topic errored: ${it.message}")
            false
        }
    }

    /** Exchanges a self-signed JWT for an OAuth access token. */
    private suspend fun accessToken(): String? = runCatching {
        val now = System.currentTimeMillis() / 1000
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claimsJson =
            """{"iss":"$clientEmail","scope":"https://www.googleapis.com/auth/firebase.messaging",""" +
                """"aud":"https://oauth2.googleapis.com/token","iat":$now,"exp":${now + 3600}}"""
        val claims = base64Url(claimsJson.toByteArray())
        val signature = base64Url(sign("$header.$claims"))
        val assertion = "$header.$claims.$signature"

        val response = http.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$assertion"
            )
        }
        if (!response.status.isSuccess()) {
            log.warn("Token request failed: ${response.status} ${response.bodyAsText().take(200)}")
            return null
        }
        json.parseToJsonElement(response.bodyAsText()).jsonObject["access_token"]?.jsonPrimitive?.content
    }.getOrElse {
        log.warn("Token request errored: ${it.message}")
        null
    }

    private fun sign(input: String): ByteArray {
        val der = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(der)))
        return Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(input.toByteArray())
            sign()
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        /** Null when no service account is configured, which disables push. */
        fun fromEnvironment(): PushNotifier? {
            val raw = System.getenv("FIREBASE_SERVICE_ACCOUNT")?.takeUnless { it.isBlank() } ?: return null
            return runCatching {
                val contents = if (raw.trimStart().startsWith("{")) raw else File(raw).readText()
                PushNotifier(contents)
            }.getOrElse {
                // A configured-but-unusable key is a mistake worth seeing, not a
                // reason to quietly stop notifying everyone.
                LoggerFactory.getLogger(PushNotifier::class.java)
                    .error("FIREBASE_SERVICE_ACCOUNT is set but unusable: ${it.message}")
                null
            }
        }
    }
}
