package com.mk.newsshorts.server.push

import com.mk.newsshorts.server.config.FeedCatalog
import com.mk.newsshorts.server.model.FeedArticleDto
import com.mk.newsshorts.server.store.ArticleStore
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Delivers one notification. Separated from [PushNotifier] so the selection
 * rules below can be tested without contacting Firebase.
 */
interface Notifier {
    suspend fun send(topic: String, message: PushMessage): Boolean
}

/**
 * Fills each notification slot for each language.
 *
 * Four slots a day is the budget, and a slot left empty is a session that never
 * happens — so the tiers below always find something to send. What changes is
 * how good it is: a story from the breaking window first, the best story of the
 * day next, and only when neither exists a reminder with no article behind it.
 *
 * The order matters more than the guarantee. A real headline is what earns the
 * interruption; a content-free nudge spends goodwill that cannot be earned back
 * once a reader turns notifications off.
 */
class BreakingNewsPusher(
    private val store: ArticleStore,
    private val notifier: Notifier,
    private val minimumGapMillis: Long = MINIMUM_GAP.inWholeMilliseconds,
    private val breakingWindowMillis: Long = BREAKING_WINDOW.inWholeMilliseconds,
    private val topStoryWindowMillis: Long = TOP_STORY_WINDOW.inWholeMilliseconds,
) {

    private val log = LoggerFactory.getLogger(BreakingNewsPusher::class.java)

    suspend fun run(now: Long = System.currentTimeMillis()) {
        FeedCatalog.languages.forEach { language ->
            val topic = "news_$language"

            // Pacing still comes first: the point is to use every slot, not to
            // send more of them.
            val lastSentAt = store.lastPushAt(topic)
            if (lastSentAt != null && now - lastSentAt < minimumGapMillis) {
                val minutesLeft = (minimumGapMillis - (now - lastSentAt)) / 60_000
                log.info("Holding $topic for another $minutesLeft min of the quiet window")
                return@forEach
            }

            val message = selectMessage(language, now)
            log.info("Sending ${message.tier.label} to $topic: ${message.title.take(60)}")

            if (notifier.send(topic, message)) {
                store.recordPush(topic, message.deepLink ?: message.title, now)
            } else {
                // The notifier reports the cause; this records the consequence,
                // which is that the quiet window did not start and the next run
                // will try again.
                log.warn("Send failed for $topic — not recorded, will retry next cycle")
            }
        }
    }

    private fun selectMessage(language: String, now: Long): PushMessage {
        val article = newestGeneralStory(language)
        val age = article?.let { now - it.publishedAt }

        return when {
            article != null && age != null && age <= breakingWindowMillis ->
                article.toMessage(PushTier.BREAKING)

            article != null && age != null && age <= topStoryWindowMillis ->
                article.toMessage(PushTier.TOP_STORY)

            else -> {
                val reason = if (article == null) "no summarized story" else "newest is ${age!! / 60_000} min old"
                log.info("Falling back to a reminder for news_$language — $reason")
                reminder(language, now)
            }
        }
    }

    private fun newestGeneralStory(language: String): FeedArticleDto? =
        store.feed(language = language, category = "general", limit = 1, offset = 0).first.firstOrNull()

    private fun FeedArticleDto.toMessage(tier: PushTier) = PushMessage(
        title = title,
        body = summary.take(BODY_LIMIT),
        deepLink = ArticleDeepLinks.build(this),
        tier = tier,
    )

    private fun reminder(language: String, now: Long): PushMessage {
        val (title, body) = ReminderCopy.forLanguage(language, now / MILLIS_PER_DAY)
        return PushMessage(title = title, body = body, deepLink = null, tier = PushTier.REMINDER)
    }

    private companion object {
        /** At most four notifications a day per language. */
        val MINIMUM_GAP = 6.hours

        /** Fresh enough to call breaking. */
        val BREAKING_WINDOW = 3.hours

        /** Still worth a headline, just not an urgent one. */
        val TOP_STORY_WINDOW = 1.days

        const val BODY_LIMIT = 160
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
