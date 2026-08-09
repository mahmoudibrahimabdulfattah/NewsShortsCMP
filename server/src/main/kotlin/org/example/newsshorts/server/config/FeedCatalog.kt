package org.example.newsshorts.server.config

import org.example.newsshorts.server.model.FeedSource

/**
 * RSS sources, grouped by language. Adding a language or source is a
 * one-line change here — no client update needed.
 */
object FeedCatalog {

    val sources: List<FeedSource> = listOf(
        // ---- Arabic ----
        FeedSource("BBC عربي", "https://feeds.bbci.co.uk/arabic/rss.xml", "ar", "general"),
        FeedSource("الجزيرة", "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdff8b8cab9", "ar", "general"),
        FeedSource("سكاي نيوز عربية", "https://www.skynewsarabia.com/rss/latest", "ar", "general"),
        FeedSource("RT Arabic", "https://arabic.rt.com/rss/", "ar", "general"),
        FeedSource("CNN بالعربية", "https://arabic.cnn.com/api/v1/rss/rss.xml", "ar", "general"),

        // ---- English ----
        FeedSource("BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml", "en", "general"),
        FeedSource("The Guardian", "https://www.theguardian.com/world/rss", "en", "general"),
        FeedSource("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", "en", "technology"),
        FeedSource("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "en", "business"),
        FeedSource("BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "en", "science"),
        FeedSource("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml", "en", "health"),
        FeedSource("BBC Entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", "en", "entertainment"),
        FeedSource("Sky Sports", "https://www.skysports.com/rss/12040", "en", "sports"),
        FeedSource("TechCrunch", "https://techcrunch.com/feed/", "en", "technology"),
    )

    val languages: Set<String> = sources.map { it.language }.toSet()
    val categories: Set<String> = sources.map { it.category }.toSet()
}
