package com.mk.newsshorts.core.domain.repository

interface InboxReadMarker {
    fun markRead(articleUrl: String)
}
