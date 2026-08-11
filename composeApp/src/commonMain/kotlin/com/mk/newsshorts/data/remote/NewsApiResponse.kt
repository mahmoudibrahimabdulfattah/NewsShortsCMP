package com.mk.newsshorts.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewsApiResponse(
    @SerialName("status")
    val status: String,
    @SerialName("totalResults")
    val totalResults: Int,
    @SerialName("articles")
    val articles: List<ArticleDto>
)

@Serializable
data class ArticleDto(
    @SerialName("source")
    val source: SourceDto,
    @SerialName("author")
    val author: String?,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String?,
    @SerialName("url")
    val url: String,
    @SerialName("urlToImage")
    val urlToImage: String?,
    @SerialName("publishedAt")
    val publishedAt: String,
    @SerialName("content")
    val content: String?
)

@Serializable
data class SourceDto(
    @SerialName("id")
    val id: String?,
    @SerialName("name")
    val name: String
)

