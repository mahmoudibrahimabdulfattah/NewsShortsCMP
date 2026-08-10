package org.example.newsshorts.server.config

import org.example.newsshorts.server.model.FeedSource

/**
 * RSS sources, grouped by language. Adding a language, category, or country is
 * a one-line change here — no client update needed.
 *
 * Every URL here was verified to return items; prefer section feeds of large
 * outlets, which are stable, over aggregators.
 */
object FeedCatalog {

    val sources: List<FeedSource> = listOf(
        // ---- Arabic: general ----
        FeedSource("BBC عربي", "https://feeds.bbci.co.uk/arabic/rss.xml", "ar", "general"),
        FeedSource("الجزيرة", "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdff8b8cab9", "ar", "general"),
        FeedSource("سكاي نيوز عربية", "https://www.skynewsarabia.com/rss/latest", "ar", "general", country = "ae"),
        FeedSource("RT Arabic", "https://arabic.rt.com/rss/", "ar", "general"),
        FeedSource("CNN بالعربية", "https://arabic.cnn.com/api/v1/rss/rss.xml", "ar", "general"),

        // ---- Arabic: categories ----
        FeedSource("BBC عربي علوم", "https://feeds.bbci.co.uk/arabic/scienceandtech/rss.xml", "ar", "science"),
        FeedSource("BBC عربي رياضة", "https://feeds.bbci.co.uk/arabic/sports/rss.xml", "ar", "sports"),
        FeedSource("BBC عربي اقتصاد", "https://feeds.bbci.co.uk/arabic/business/rss.xml", "ar", "business"),
        FeedSource("RT Arabic تكنولوجيا", "https://arabic.rt.com/rss/technology/", "ar", "technology"),
        FeedSource("RT Arabic رياضة", "https://arabic.rt.com/rss/sport/", "ar", "sports"),
        FeedSource("RT Arabic اقتصاد", "https://arabic.rt.com/rss/business/", "ar", "business"),
        FeedSource("CNN بالعربية اقتصاد", "https://arabic.cnn.com/api/v1/rss/business/rss.xml", "ar", "business"),
        FeedSource("CNN بالعربية رياضة", "https://arabic.cnn.com/api/v1/rss/sport/rss.xml", "ar", "sports"),
        FeedSource("CNN بالعربية صحة", "https://arabic.cnn.com/api/v1/rss/health/rss.xml", "ar", "health"),
        FeedSource("CNN بالعربية منوعات", "https://arabic.cnn.com/api/v1/rss/entertainment/rss.xml", "ar", "entertainment"),

        // ---- Arabic: countries ----
        FeedSource("اليوم السابع", "https://www.youm7.com/rss/SectionRss?SectionID=65", "ar", "general", country = "eg"),
        FeedSource("المصري اليوم", "https://www.almasryalyoum.com/rss/rssfeeds", "ar", "general", country = "eg"),
        FeedSource("الشرق الأوسط", "https://aawsat.com/feed", "ar", "general", country = "sa"),

        // ---- English: general ----
        FeedSource("BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml", "en", "general"),
        FeedSource("The Guardian", "https://www.theguardian.com/world/rss", "en", "general"),

        // ---- English: categories ----
        FeedSource("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", "en", "technology"),
        FeedSource("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "en", "business"),
        FeedSource("BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "en", "science"),
        FeedSource("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml", "en", "health"),
        FeedSource("BBC Entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", "en", "entertainment"),
        FeedSource("Sky Sports", "https://www.skysports.com/rss/12040", "en", "sports"),
        FeedSource("TechCrunch", "https://techcrunch.com/feed/", "en", "technology"),

        // ---- English: countries ----
        FeedSource("NYT U.S.", "https://rss.nytimes.com/services/xml/rss/nyt/US.xml", "en", "general", country = "us"),
        FeedSource("BBC UK", "https://feeds.bbci.co.uk/news/uk/rss.xml", "en", "general", country = "gb"),
    )

    val languages: Set<String> = sources.map { it.language }.toSet()
    val categories: Set<String> = sources.map { it.category }.toSet()
    val countries: Set<String> = sources.mapNotNull { it.country }.toSet()
}
