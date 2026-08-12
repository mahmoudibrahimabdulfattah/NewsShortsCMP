package com.mk.newsshorts.server.push

/**
 * One notification, already resolved to the text a reader will see.
 *
 * [deepLink] is null for the reminder tier, which has no article behind it; the
 * app then opens on the feed instead of a details screen.
 */
data class PushMessage(
    val title: String,
    val body: String,
    val deepLink: String?,
    /** Which selection tier produced this, for the run log and analytics. */
    val tier: PushTier,
)

enum class PushTier(val label: String) {
    /** A story published within the breaking window. */
    BREAKING("breaking"),

    /** The best story of the day when nothing is breaking. */
    TOP_STORY("top_story"),

    /** No article to carry — a nudge back into the app. */
    REMINDER("reminder"),
}

/**
 * Copy for the reminder tier.
 *
 * This tier exists so a slot is never wasted, but it is deliberately last:
 * a notification with no news in it is the fastest way to get notifications
 * switched off, and that loss is permanent. Anything with a real headline
 * behind it goes first.
 *
 * The lines rotate so a reader who sees two in a week does not get the same
 * sentence twice.
 */
object ReminderCopy {

    private val arabic = listOf(
        "أخبار مختصرة" to "اطّلع على أهم ما فاتك اليوم في دقيقة",
        "جديد اليوم" to "ملخصات سريعة لأهم الأخبار — اقرأها الآن",
        "لا يفوتك" to "أبرز العناوين بانتظارك داخل التطبيق",
    )

    private val english = listOf(
        "News Shorts" to "Catch up on today's headlines in a minute",
        "New today" to "Quick summaries of the stories that matter",
        "Don't miss out" to "The day's top headlines are waiting for you",
    )

    /** [seed] is the day, so the line changes between days but not within one. */
    fun forLanguage(language: String, seed: Long): Pair<String, String> {
        val lines = if (language == "ar") arabic else english
        return lines[((seed % lines.size).toInt() + lines.size) % lines.size]
    }
}
