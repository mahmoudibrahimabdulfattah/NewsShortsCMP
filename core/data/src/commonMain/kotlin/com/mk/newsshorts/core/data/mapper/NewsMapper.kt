package com.mk.newsshorts.core.data.mapper

import com.mk.newsshorts.core.data.remote.ArticleDto
import com.mk.newsshorts.core.data.remote.NewsApiResponse
import com.mk.newsshorts.core.model.time.currentTimeMillis
import com.mk.newsshorts.core.model.ArticleAuthor
import com.mk.newsshorts.core.model.ArticleContent
import com.mk.newsshorts.core.model.ArticleDescription
import com.mk.newsshorts.core.model.ArticleId
import com.mk.newsshorts.core.model.ArticleTitle
import com.mk.newsshorts.core.model.ArticleUrl
import com.mk.newsshorts.core.model.ImageUrl
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.core.model.NewsCategory
import com.mk.newsshorts.core.model.NewsSource
import com.mk.newsshorts.core.model.PublishedTimestamp
import com.mk.newsshorts.core.model.SourceId
import com.mk.newsshorts.core.model.SourceName

object NewsMapper {
    fun mapToDomain(
        response: NewsApiResponse,
        category: NewsCategory
    ): List<NewsArticle> {
        return response.articles
            .filter { article -> isValidArticle(article) }
            .mapIndexed { index, dto -> mapArticleToDomain(dto, index, category) }
    }

    private fun isValidArticle(article: ArticleDto): Boolean {
        return article.title.isNotBlank() &&
            article.title != "[Removed]" &&
            article.url.isNotBlank()
    }

    private fun mapArticleToDomain(
        dto: ArticleDto,
        index: Int,
        category: NewsCategory
    ): NewsArticle {
        return NewsArticle(
            id = ArticleId("${dto.source.id ?: "unknown"}_$index"),
            title = ArticleTitle(dto.title),
            description = ArticleDescription(dto.description ?: ""),
            content = ArticleContent(dto.content ?: dto.description ?: ""),
            author = ArticleAuthor(dto.author ?: "Unknown"),
            source = NewsSource(
                id = SourceId(dto.source.id ?: "unknown"),
                name = SourceName(dto.source.name)
            ),
            imageUrl = dto.urlToImage?.takeIf { it.isNotBlank() }?.let { ImageUrl(it) },
            articleUrl = ArticleUrl(dto.url),
            publishedAt = parsePublishedDate(dto.publishedAt),
            // The article's own category when it has one; the caller's feed
            // otherwise. The search corpus spans every category at once, so
            // taking the caller's word there would label all of it "general".
            category = dto.category?.let { NewsCategory.fromApiValue(it) } ?: category
        )
    }

    private fun parsePublishedDate(dateString: String): PublishedTimestamp {
        return try {
            // Parse ISO 8601 date format: "2024-12-24T10:30:00Z"
            val epochMillis: Long = parseIso8601ToEpochMillis(dateString)
            PublishedTimestamp(epochMillis)
        } catch (exception: Exception) {
            // Fallback to current timestamp if parsing fails
            PublishedTimestamp(currentTimeMillis())
        }
    }

    private fun parseIso8601ToEpochMillis(dateString: String): Long {
        // Simple ISO 8601 parser without external dependencies
        // Format: "2024-12-24T10:30:00Z" or "2024-12-24T10:30:00.000Z"
        val cleanDate: String = dateString.replace("Z", "").replace("z", "")
        val parts: List<String> = cleanDate.split("T")
        if (parts.size != 2) return currentTimeMillis()
        val dateParts: List<String> = parts[0].split("-")
        val timeParts: List<String> = parts[1].split(":")
        if (dateParts.size != 3 || timeParts.size < 2) return currentTimeMillis()
        val year: Int = dateParts[0].toIntOrNull() ?: return currentTimeMillis()
        val month: Int = dateParts[1].toIntOrNull() ?: return currentTimeMillis()
        val day: Int = dateParts[2].toIntOrNull() ?: return currentTimeMillis()
        val hour: Int = timeParts[0].toIntOrNull() ?: 0
        val minute: Int = timeParts[1].toIntOrNull() ?: 0
        val second: Int = timeParts.getOrNull(2)?.split(".")?.get(0)?.toIntOrNull() ?: 0
        // Calculate epoch milliseconds (simplified, assumes UTC)
        val daysFromEpoch: Long = calculateDaysFromEpoch(year, month, day)
        val secondsOfDay: Long = (hour * 3600L) + (minute * 60L) + second
        return (daysFromEpoch * 86400L + secondsOfDay) * 1000L
    }

    private fun calculateDaysFromEpoch(year: Int, month: Int, day: Int): Long {
        // Days from 1970-01-01 to given date
        var totalDays: Long = 0
        // Add days for years
        for (y in 1970 until year) {
            totalDays += if (isLeapYear(y)) 366 else 365
        }
        // Add days for months
        val daysInMonth: IntArray = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (m in 1 until month) {
            totalDays += daysInMonth[m - 1]
            if (m == 2 && isLeapYear(year)) totalDays += 1
        }
        // Add days
        totalDays += (day - 1)
        return totalDays
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}
