package com.mk.newsshorts.server.push

import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.model.FeedArticleDto
import com.mk.newsshorts.server.store.ArticleStore
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

/**
 * Picks at most one story per language to notify about, per run.
 *
 * The publish workflow runs every half hour, so the constraint that matters is
 * restraint: a news app that notifies more than a few times a day gets its
 * notifications turned off, and then it can never reach the reader again.
 */
/**
 * Delivers one notification. Separated from [PushNotifier] so the selection
 * rules below can be tested without contacting Firebase.
 */
interface Notifier {
    suspend fun send(topic: String, article: FeedArticleDto): Boolean
}

class BreakingNewsPusher(
    private val store: ArticleStore,
    private val notifier: Notifier,
    private val minimumGapMillis: Long = MINIMUM_GAP.inWholeMilliseconds,
    private val maximumAgeMillis: Long = MAXIMUM_AGE.inWholeMilliseconds,
) {

    private val log = LoggerFactory.getLogger(BreakingNewsPusher::class.java)

    suspend fun run(now: Long = System.currentTimeMillis()) {
        FeedCatalog.languages.forEach { language ->
            val topic = "news_$language"

            val lastSentAt = store.lastPushAt(topic)
            if (lastSentAt != null && now - lastSentAt < minimumGapMillis) return@forEach

            // Only the top general story, and only while it is still news.
            val (articles, _) = store.feed(
                language = language, category = "general", limit = 1, offset = 0,
            )
            val article = articles.firstOrNull() ?: return@forEach
            if (now - article.publishedAt > maximumAgeMillis) {
                log.info("Nothing fresh enough to push for $topic")
                return@forEach
            }

            if (notifier.send(topic, article)) {
                store.recordPush(topic, article.url, now)
            }
        }
    }

    private companion object {
        /** At most four notifications a day per language. */
        val MINIMUM_GAP = 6.hours

        /** Older than this is not breaking news. */
        val MAXIMUM_AGE = 3.hours
    }
}
