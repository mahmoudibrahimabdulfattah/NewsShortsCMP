package com.mk.newsshorts.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mk.newsshorts.presentation.ui.theme.UnreadMark
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.mvi.InboxNotification
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar
import com.mk.newsshorts.presentation.ui.components.formatPublishedTime

/**
 * What the reader was sent, whether or not their phone showed it.
 *
 * The list comes from the backend rather than from notifications this device
 * happened to receive — see `NotificationInboxClient` for why that difference
 * decides whether the screen is any use.
 *
 * [unreadIds] is passed in rather than derived here: opening this screen does
 * not clear a mark, so the set has to be the one the ViewModel holds, changed
 * only by opening a notification or by the action in the bar.
 */
@Composable
fun NotificationInboxScreen(
    notifications: List<InboxNotification>,
    unreadIds: Set<Long>,
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(
                title = strings.notificationInbox,
                onBack = { onEvent(NewsUiEvent.CloseOverlay) },
                action = {
                    // Offered only when it would do something. A control that is
                    // always there and usually inert teaches the reader to stop
                    // looking at it.
                    if (unreadIds.isNotEmpty()) {
                        TextButton(onClick = { onEvent(NewsUiEvent.MarkAllNotificationsRead) }) {
                            Text(
                                text = strings.markAllNotificationsRead,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            if (notifications.isEmpty()) {
                EmptyInbox(
                    title = strings.notificationInboxEmptyTitle,
                    body = strings.notificationInboxEmptyBody,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // The app draws behind the system navigation bar, so the
                    // last card needs the inset added or it sits under it on
                    // three-button navigation.
                    contentPadding = WindowInsets.navigationBars
                        .add(WindowInsets(left = 16.dp, top = 12.dp, right = 16.dp, bottom = 12.dp))
                        .asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(notifications, key = { it.sentAt }) { notification ->
                        NotificationRow(
                            notification = notification,
                            isUnread = notification.sentAt in unreadIds,
                            onClick = {
                                onEvent(
                                    NewsUiEvent.OpenInboxNotification(
                                        sentAt = notification.sentAt,
                                        deepLink = notification.deepLink,
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: InboxNotification,
    isUnread: Boolean,
    onClick: () -> Unit,
) {
    val strings = appStrings()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = if (isUnread) 0.55f else 0.3f)
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // A dot rather than bold text: the row's own weight is already
            // carrying the headline, and the mark has to read the same in both
            // reading directions.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    // The same crimson as the badge on the bell, and the same
                    // in both themes: one mark should not read as two states.
                    .background(if (isUnread) UnreadMark else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notification.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    // The same formatter the cards use, so a date reads the
                    // same wherever it appears.
                    text = formatPublishedTime(
                        PublishedTimestamp(notification.sentAt),
                        strings.monthNames,
                        strings.recently,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun EmptyInbox(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Centred explicitly: the column centres the block, not the lines
            // inside it, so a wrapped sentence fell back to the reading
            // direction and sat under a centred heading looking misaligned.
            //
            // And leading tightened from the theme's. Arabic body text carries
            // 1.4x for long-form reading, which is right for an article and
            // wrong for two centred lines in a card: one sentence broke into
            // halves further apart than the sentence was from its own heading,
            // so it read as two unrelated fragments. Derived from the font size
            // rather than fixed, so it still follows the reader's text scale.
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.35f,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
