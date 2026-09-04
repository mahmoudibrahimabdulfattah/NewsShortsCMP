package com.mk.newsshorts.feature.settings

/**
 * Which per-tier notification switch was toggled. Matches the "tier" field the
 * server puts in the FCM data payload (server's `PushTier.label`), so the
 * stored preference and the incoming message can be compared as plain strings.
 */
enum class NotificationTier(val wireValue: String) {
    BREAKING("breaking"), TOP_STORY("top_story"), REMINDER("reminder");
}
