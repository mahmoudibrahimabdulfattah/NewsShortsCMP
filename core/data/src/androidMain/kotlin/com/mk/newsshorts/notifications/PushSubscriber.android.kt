package com.mk.newsshorts.notifications

import android.content.Context
import com.mk.newsshorts.core.domain.notifications.PushSubscriber

class FirebasePushSubscriber(private val context: Context) : PushSubscriber {
    override fun subscribeToLanguage(language: String) {
        NewsTopics.subscribe(context, language)
    }

    override fun unsubscribeAll() {
        NewsTopics.unsubscribeAll(context)
    }
}
