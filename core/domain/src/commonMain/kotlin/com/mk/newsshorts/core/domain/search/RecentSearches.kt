package com.mk.newsshorts.core.domain.search

interface RecentSearches {
    fun load(): List<String>
    fun add(query: String): List<String>
    fun remove(query: String): List<String>
    fun clear()
}
