package com.mk.newsshorts.core.contract.feed

/**
 * Category ids carried by `FeedArticleDto.category` in the feed JSON.
 *
 * `com.mk.newsshorts.core.model.NewsCategory` maps onto these ids for the UI.
 * The order of [all] is the server's feed iteration order, so it is not free to
 * change even though the app's tab order is owned by the enum.
 */
object NewsCategories {
    const val GENERAL = "general"

    val all: Set<String> = linkedSetOf(
        GENERAL,
        "business",
        "technology",
        "science",
        "health",
        "sports",
        "entertainment",
    )
}
