package com.mk.newsshorts.core.model.onboarding

/**
 * The three things worth asking before the first headline: what language to
 * read in, what to read about, and whether to be told when something breaks.
 *
 * Ordered by how much the answer changes what the reader sees next - language
 * rewrites every screen including this one, categories decide what the feed
 * opens on, and notifications only matter after they have left.
 */
enum class OnboardingStep {
    LANGUAGE, CATEGORIES, NOTIFICATIONS;

    val next: OnboardingStep? get() = entries.getOrNull(ordinal + 1)
}
