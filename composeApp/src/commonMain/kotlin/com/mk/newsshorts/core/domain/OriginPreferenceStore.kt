package com.mk.newsshorts.core.domain

interface OriginPreferenceStore {
    fun preferredOrigin(): String?
    fun savePreferredOrigin(origin: String)
}
