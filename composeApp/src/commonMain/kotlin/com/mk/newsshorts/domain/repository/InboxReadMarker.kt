package com.mk.newsshorts.domain.repository

interface InboxReadMarker {
    fun markRead(articleUrl: String)
}
