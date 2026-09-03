package com.mk.newsshorts.core.domain.feed

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class InvalidationReason {
    LanguageChanged,
    CountryChanged,
    SyncApplied,
    OnboardingFinished,
}

interface FeedInvalidator {
    val signals: SharedFlow<InvalidationReason>
    fun invalidate(reason: InvalidationReason)
}

class DefaultFeedInvalidator : FeedInvalidator {
    private val mutableSignals = MutableSharedFlow<InvalidationReason>(
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    override val signals: SharedFlow<InvalidationReason> = mutableSignals.asSharedFlow()

    override fun invalidate(reason: InvalidationReason) {
        mutableSignals.tryEmit(reason)
    }

    private companion object {
        const val BUFFER_CAPACITY: Int = 16
    }
}
