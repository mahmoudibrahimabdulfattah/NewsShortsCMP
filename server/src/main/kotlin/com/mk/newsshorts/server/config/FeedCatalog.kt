package com.mk.newsshorts.server.config

import com.mk.newsshorts.server.model.FeedSource
import com.mk.newsshorts.server.model.NewsCategories

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
        FeedSource("اندبندنت عربية", "https://www.independentarabia.com/rss.xml", "ar", "general"),
        FeedSource("RT Arabic", "https://arabic.rt.com/rss/", "ar", "general"),
        FeedSource("CNN بالعربية", "https://arabic.cnn.com/api/v1/rss/rss.xml", "ar", "general"),

        // ---- Arabic: categories ----
        FeedSource("RT Arabic تكنولوجيا", "https://arabic.rt.com/rss/technology/", "ar", "technology"),
        FeedSource(
            "يورونيوز عربية تكنولوجيا",
            "https://arabic.euronews.com/rss?level=vertical&name=next",
            "ar",
            "technology",
        ),
        FeedSource("RT Arabic رياضة", "https://arabic.rt.com/rss/sport/", "ar", "sports"),
        FeedSource("RT Arabic اقتصاد", "https://arabic.rt.com/rss/business/", "ar", "business"),
        FeedSource("CNN بالعربية اقتصاد", "https://arabic.cnn.com/api/v1/rss/business/rss.xml", "ar", "business"),
        FeedSource("CNN بالعربية رياضة", "https://arabic.cnn.com/api/v1/rss/sport/rss.xml", "ar", "sports"),
        FeedSource(
            "CNN بالعربية صحة وعلوم",
            "https://arabic.cnn.com/api/v1/rss/science_and_health/rss.xml",
            "ar",
            "health",
            additionalCategories = setOf("science"),
        ),
        FeedSource("CNN بالعربية منوعات", "https://arabic.cnn.com/api/v1/rss/entertainment/rss.xml", "ar", "entertainment"),
        FeedSource("CNN بالعربية ستايل", "https://arabic.cnn.com/api/v1/rss/style/rss.xml", "ar", "entertainment"),
        FeedSource("RT Arabic صحة", "https://arabic.rt.com/rss/health/", "ar", "health"),

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
        FeedSource("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "en", "sports"),
        FeedSource("Sky Sports", "https://www.skysports.com/rss/12040", "en", "sports"),
        FeedSource("TechCrunch", "https://techcrunch.com/feed/", "en", "technology"),
        FeedSource("Ars Technica", "https://arstechnica.com/feed/", "en", "technology"),
        FeedSource("Engadget", "https://www.engadget.com/rss.xml", "en", "technology"),
        FeedSource("The Guardian Science", "https://www.theguardian.com/science/rss", "en", "science"),
        FeedSource("The Guardian Sport", "https://www.theguardian.com/uk/sport/rss", "en", "sports"),
        FeedSource("The Guardian Culture", "https://www.theguardian.com/uk/culture/rss", "en", "entertainment"),
        FeedSource("The Guardian Technology", "https://www.theguardian.com/uk/technology/rss", "en", "technology"),
        FeedSource("The Guardian Business", "https://www.theguardian.com/uk/business/rss", "en", "business"),
        // Specialised English articles are rendered into every feed language,
        // so these sources also fill the Arabic science and health tabs.
        FeedSource("ScienceDaily", "https://www.sciencedaily.com/rss/all.xml", "en", "science"),
        FeedSource("Phys.org", "https://phys.org/rss-feed/", "en", "science"),
        FeedSource("STAT", "https://www.statnews.com/feed/", "en", "health"),
        FeedSource("The Guardian Health", "https://www.theguardian.com/society/health/rss", "en", "health"),

        // ---- English: countries ----
        // Every country the app offers needs at least one source; readers who
        // picked the other language get these translated.
        FeedSource("NYT U.S.", "https://rss.nytimes.com/services/xml/rss/nyt/US.xml", "en", "general", country = "us"),
        FeedSource("BBC UK", "https://feeds.bbci.co.uk/news/uk/rss.xml", "en", "general", country = "gb"),
        FeedSource("Egypt Independent", "https://www.egyptindependent.com/feed/", "en", "general", country = "eg"),
        FeedSource("Saudi Gazette", "https://saudigazette.com.sa/rssFeed/74", "en", "general", country = "sa"),
        FeedSource("The National", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml", "en", "general", country = "ae"),
        FeedSource("DW", "https://rss.dw.com/rdf/rss-en-all", "en", "general", country = "de"),
        FeedSource("The Local Germany", "https://www.thelocal.de/feeds/rss.php", "en", "general", country = "de"),
        FeedSource("France 24", "https://www.france24.com/en/rss", "en", "general", country = "fr"),
        FeedSource("RFI", "https://www.rfi.fr/en/rss", "en", "general", country = "fr"),
        FeedSource("BBC India", "https://feeds.bbci.co.uk/news/world/asia/india/rss.xml", "en", "general", country = "in"),
        FeedSource("The Hindu", "https://www.thehindu.com/news/national/feeder/default.rss", "en", "general", country = "in"),
        FeedSource("BBC China", "https://feeds.bbci.co.uk/news/world/asia/china/rss.xml", "en", "general", country = "cn"),
        FeedSource("China Daily", "https://www.chinadaily.com.cn/rss/china_rss.xml", "en", "general", country = "cn"),
        FeedSource("South China Morning Post", "https://www.scmp.com/rss/91/feed", "en", "general", country = "cn"),
        FeedSource("The Japan Times", "https://www.japantimes.co.jp/feed/", "en", "general", country = "jp"),
        FeedSource("ABC News Australia", "https://www.abc.net.au/news/feed/51120/rss.xml", "en", "general", country = "au"),
        FeedSource("Global News", "https://globalnews.ca/feed/", "en", "general", country = "ca"),
        FeedSource("The Rio Times", "https://riotimesonline.com/feed/", "en", "general", country = "br"),
    )

    val languages: Set<String> = sources.map { it.language }.toSet()
    // The client exposes every category even when its only current source is in
    // another language, so generation and health checks must do the same.
    val categories: Set<String> = NewsCategories.all
    val countries: Set<String> = sources.mapNotNull { it.country }.toSet()

    /**
     * Every country feed is offered in all of these, translating where a
     * country has no source in a given language — a reader who picked English
     * should get Egypt in English, not in Arabic.
     */
    val countryLanguages: Set<String> = languages
}
