package com.mk.newsshorts.domain.model

enum class NewsCategory(
    val displayName: String,
    val apiValue: String,
    val emoji: String
) {
    GENERAL(displayName = "General", apiValue = "general", emoji = "📰"),
    TECHNOLOGY(displayName = "Technology", apiValue = "technology", emoji = "💻"),
    BUSINESS(displayName = "Business", apiValue = "business", emoji = "💼"),
    ENTERTAINMENT(displayName = "Entertainment", apiValue = "entertainment", emoji = "🎬"),
    SPORTS(displayName = "Sports", apiValue = "sports", emoji = "⚽"),
    SCIENCE(displayName = "Science", apiValue = "science", emoji = "🔬"),
    HEALTH(displayName = "Health", apiValue = "health", emoji = "🏥");

    companion object {
        fun fromApiValue(value: String): NewsCategory {
            return entries.find { it.apiValue.equals(value, ignoreCase = true) } ?: GENERAL
        }
    }
}

