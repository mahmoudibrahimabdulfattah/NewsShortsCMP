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

            // Every branch below says why nothing was sent. Silence used to be
            // the answer to "no push again?" — quiet hours, a stale feed, a
            // rejected send and a broken key all looked identical in the log.
            val lastSentAt = store.lastPushAt(topic)
            if (lastSentAt != null && now - lastSentAt < minimumGapMillis) {
                val minutesLeft = (minimumGapMillis - (now - lastSentAt)) / 60_000
                log.info("Holding $topic for another $minutesLeft min of the quiet window")
                return@forEach
            }

            // Only the top general story, and only while it is still news.
            val (articles, _) = store.feed(
                language = language, category = "general", limit = 1, offset = 0,
            )
            val article = articles.firstOrNull()
            if (article == null) {
                log.info("No summarized general story to push for $topic")
                return@forEach
            }
            val ageMinutes = (now - article.publishedAt) / 60_000
            if (now - article.publishedAt > maximumAgeMillis) {
                log.info("Nothing fresh enough for $topic — newest is $ageMinutes min old")
                return@forEach
            }

            if (notifier.send(topic, article)) {
                store.recordPush(topic, article.url, now)
            } else {
                // The notifier reports the cause; this records the consequence,
                // which is that the quiet window did not start and the next run
                // will try the same story again.
                log.warn("Send failed for $topic — not recorded, will retry next cycle")
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
